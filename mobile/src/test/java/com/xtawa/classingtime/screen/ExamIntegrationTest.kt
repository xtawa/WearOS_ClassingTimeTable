package com.xtawa.classingtime.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.classing.client.exam.ExamJson
import com.classing.client.exam.ExamStore
import com.classing.shared.exam.Exam
import com.classing.shared.sync.*
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.sync.MobileCloudSyncV2Store
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExamIntegrationTest {
    private lateinit var context: Context
    private val exam = Exam("a", "Math exam", 1_792_112_400_000, 1_792_119_600_000, "Asia/Shanghai", "A201")
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        try {
            androidx.work.WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build())
        }
        context.getSharedPreferences("classing_exams_v1", 0).edit().clear().commit()
        context.getSharedPreferences("mobile_cloud_sync_v2_state", 0).edit().clear().commit()
    }
    @Test fun examOnlyBackupRoundTripsAndLegacyBackupPreservesExamDomain() {
        val raw = buildScheduleBackupJson(emptyList(), emptyList(), ZoneId.of("Asia/Shanghai"), WeekNumberMode.NATURAL, LocalDate.of(2026,10,12), listOf(exam))
        assertEquals(listOf(exam), parseScheduleBackupJson(raw, context)!!.exams)
        val legacy = JSONObject(raw).also { it.remove("exams") }.toString()
        assertNull(parseScheduleBackupJson(legacy, context)!!.exams)
        assertEquals(emptyList<Exam>(), parseScheduleBackupJson(JSONObject(raw).put("exams", JSONArray()).toString(), context)!!.exams)
    }
    @Test fun invalidBackupRejectsWholeExamBatch() {
        val raw = buildScheduleBackupJson(emptyList(), emptyList(), ZoneId.of("UTC"), WeekNumberMode.NATURAL, LocalDate.now(), listOf(exam))
        val invalid = JSONObject(raw).put("exams", JSONArray().put(ExamJson.encode(exam)).put(ExamJson.encode(exam.copy(id="b")).put("endAt", 0)))
        assertNull(parseScheduleBackupJson(invalid.toString(), context))
    }
    @Test fun examJsonRoundTripAndDuplicateProtection() {
        assertEquals(exam, ExamJson.decode(ExamJson.encode(exam)))
        assertThrows(IllegalArgumentException::class.java) { ExamJson.list(JSONArray().put(ExamJson.encode(exam)).put(ExamJson.encode(exam))) }
        assertThrows(IllegalArgumentException::class.java) { ExamJson.decode(ExamJson.encode(exam).put("startAt",1.5)) }
    }
    @Test fun rejectedAiBatchDoesNotWriteAnyPartialChanges() {
        ExamStore.save(context, listOf(exam))
        val proposal = JSONObject().put("version",1).put("actions", JSONArray()
            .put(JSONObject().put("operation","create").put("exam", ExamJson.encode(exam)))
            .put(JSONObject().put("operation","delete").put("examId","missing")))
        assertThrows(IllegalArgumentException::class.java) { ExamJson.applyProposal(ExamStore.load(context), proposal) }
        assertEquals(listOf(exam), ExamStore.load(context))
    }
    @Test fun aiCreateUpdateDeleteAndFingerprintChanges() {
        val updated = exam.copy(title="Physics exam")
        val proposal = JSONObject().put("version",1).put("actions", JSONArray().put(JSONObject()
            .put("operation","update").put("examId",exam.id).put("exam",ExamJson.encode(updated))))
        assertEquals(listOf(updated), ExamJson.applyProposal(listOf(exam), proposal))
        val deletion = JSONObject().put("version",1).put("actions", JSONArray().put(JSONObject().put("operation","delete").put("examId",exam.id)))
        assertTrue(ExamJson.applyProposal(listOf(exam), deletion).isEmpty())
        fun fingerprint(exams: List<Exam>) = timetableFingerprint(emptyList(), "UTC", WeekNumberMode.NATURAL, LocalDate.of(2026,10,12), DayOfWeek.MONDAY, exams=exams)
        assertNotEquals(fingerprint(listOf(exam)), fingerprint(listOf(updated)))
    }
    @Test fun cloudCaptureCarriesExamTombstoneAndMissingDomainPreservesLocal() {
        ExamStore.save(context, listOf(exam))
        val first = MobileCloudSyncV2Store.captureLocal(context, syncScopes=setOf(SyncScope.TIMETABLE))
        MobileCloudSyncV2Store.saveDocument(context, first)
        ExamStore.save(context, emptyList())
        val deleted = MobileCloudSyncV2Store.captureLocal(context, syncScopes=setOf(SyncScope.TIMETABLE))
        assertTrue(deleted.records[CloudSyncV2.DOMAIN_TIMETABLE_EXAMS]!![exam.id]!!.isDeleted)
        ExamStore.save(context,listOf(exam))
        MobileCloudSyncV2Store.applyMerged(context,CloudSyncDocumentV2(),setOf(SyncScope.TIMETABLE))
        assertEquals(listOf(exam),ExamStore.load(context))
    }
    @Test fun explicitEmptyCloudDomainClearsExamsAndInvalidDomainLeavesBaselineUntouched() {
        ExamStore.save(context,listOf(exam))
        val invalid = CloudSyncDocumentV2(records=mapOf(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS to mapOf(
            "a" to VersionedRecord("a", ExamJson.encode(exam).put("endAt",0).toString(),LogicalVersion(1,"remote",1)))))
        assertThrows(IllegalArgumentException::class.java) { MobileCloudSyncV2Store.applyMerged(context,invalid,setOf(SyncScope.TIMETABLE)) }
        assertFalse(MobileCloudSyncV2Store.hasLocalBaseline(context))
        assertEquals(listOf(exam),ExamStore.load(context))
        MobileCloudSyncV2Store.applyMerged(context,CloudSyncDocumentV2(records=mapOf(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS to emptyMap())),setOf(SyncScope.TIMETABLE))
        assertTrue(ExamStore.load(context).isEmpty())
    }
}
