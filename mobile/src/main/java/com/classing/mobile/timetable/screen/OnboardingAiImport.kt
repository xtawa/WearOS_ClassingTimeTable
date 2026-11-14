package com.xtawa.classingtime.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.AiApiClient
import com.xtawa.classingtime.sync.AccountSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import com.classing.client.byok.*
import com.classing.shared.ai.*
import org.json.JSONArray
import org.json.JSONObject

private suspend fun onboardingByok(context: Context): ByokConfig? {
    val owner=com.xtawa.classingtime.data.MobilePrefsStore.loadSettings(context).accountSummary.userId
    if(owner.isBlank()) return null
    val config=ByokStore.load(context,owner)
    if(!config.enabled) return null
    requireMobileByokPro(context,owner)
    return config
}

private suspend fun recognizeByok(context: Context, profile: ByokProfile, prompt: String, images: List<ByokImage> = emptyList()): BackupRestorePayload {
    val today=LocalDate.now()
    val snapshot=timetableSnapshot(emptyList(),today,1,ZoneId.systemDefault().id,WeekNumberMode.SEMESTER,today,DayOfWeek.MONDAY)
    val messages=JSONArray().put(JSONObject().put("role","user").put("content",prompt))
    val reply=ByokProvider().chat(profile,messages,snapshot,images)
    val proposal=ByokActions.parse(reply).first ?: error(context.getString(R.string.ai_import_empty))
    require((0 until proposal.getJSONArray("actions").length()).all {proposal.getJSONArray("actions").getJSONObject(it).getString("operation")=="create"})
    val lessons=applyAskAiCourseProposal(emptyList(),proposal)
    return BackupRestorePayload(lessons,emptyList(),null,null,listOf(context.getString(R.string.ai_text_review),ByokActions.display(reply)).filter(String::isNotBlank))
}

internal suspend fun importOnboardingDocument(context: Context, uri: Uri): BackupRestorePayload = withContext(Dispatchers.IO) {
    val token = AccountSessionManager.ensureAccessToken(context) ?: error(context.getString(R.string.ai_import_login))
    val client = AiApiClient(appContext = context)
    val byok=onboardingByok(context)
    val vision=byok?.let {it.profile(it.visionId)}
    val lessons = mutableListOf<LessonUi>()
    val warnings = mutableListOf<String>()
    suspend fun recognize(jpeg: ByteArray) {
        if(vision!=null) {
            val parsed=recognizeByok(context,vision,"Extract every meeting from this timetable image as create actions. Do not invent unreadable fields; ask for clarification.",listOf(ByokImage("timetable",jpeg)))
            lessons+=parsed.baseLessons;warnings+=parsed.warnings;return
        }
        val result = client.photoImport(token, jpeg).getOrThrow()
        val parsed = parseJsonToLessons(result.timetable.toString(), context)
        lessons += parsed.lessons
        warnings += parsed.warnings
    }
    if (context.contentResolver.getType(uri) == "application/pdf") {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF")
        descriptor.use { fd -> PdfRenderer(fd).use { pdf ->
            require(pdf.pageCount in 1..5) { context.getString(R.string.ai_import_pdf_pages) }
            for (index in 0 until pdf.pageCount) {
                pdf.openPage(index).use { page ->
                    require(page.width > 0 && page.height > 0)
                    val scale = minOf(2048f / page.width, 2048f / page.height)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                        require(output.size() <= 2_500_000) { "PDF page is too large" }
                        recognize(output.toByteArray())
                    } finally { bitmap.recycle() }
                }
            }
        } }
    } else recognize(prepareTimetablePhoto(context, uri))
    val distinct = lessons.distinctBy { listOf(it.title, it.dayOfWeek, it.startTime, it.endTime, it.location, it.startWeek, it.endWeek, it.weekParity, it.scheduleRuleJson) }
    require(distinct.isNotEmpty()) { context.getString(R.string.onboarding_import_error) }
    BackupRestorePayload(distinct, emptyList(), null, null, warnings)
}

internal suspend fun importOnboardingText(context: Context, text: String): BackupRestorePayload = withContext(Dispatchers.IO) {
    require(text.isNotBlank() && text.length <= 12000)
    val token = AccountSessionManager.ensureAccessToken(context) ?: error(context.getString(R.string.ai_import_login))
    onboardingByok(context)?.let {return@withContext recognizeByok(context,it.profile(it.chatId),"Create the timetable from this text as create actions. Ask for missing times rather than guessing. Input:\n$text")}
    val client = AiApiClient(appContext = context)
    val model = client.models(token).getOrThrow().first
    val today = LocalDate.now()
    val snapshot = timetableSnapshot(emptyList(), today, 1, ZoneId.systemDefault().id, WeekNumberMode.SEMESTER, today, DayOfWeek.MONDAY)
    val result = client.chat(token, null, "Create the entire timetable from this text. Return create actions for every meeting. Ask for clarification instead of inventing dates/times. Input:\n$text", snapshot, model).getOrThrow()
    val proposal = result.courseProposal ?: error(result.reply.ifBlank { context.getString(R.string.ai_import_empty) })
    val lessons = applyAskAiCourseProposal(emptyList(), proposal)
    require(lessons.isNotEmpty()) { context.getString(R.string.ai_import_empty) }
    BackupRestorePayload(lessons, emptyList(), null, null, listOf(context.getString(R.string.ai_text_review), result.reply).filter(String::isNotBlank))
}
