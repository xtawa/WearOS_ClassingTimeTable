package com.classing.wear.timetable.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.widget.NextClassSnapshot
import com.classing.wear.timetable.widget.NextClassSnapshotProvider

class NextClassComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = snapshotProvider().loadSnapshot()
        return buildData(request.complicationType, snapshot)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = NextClassSnapshot(
            hasLesson = true,
            courseTitle = "Advanced Android",
            timeText = "09:00-10:40",
            locationText = "A-301",
            shortComplicationText = "09:00 Android",
            longComplicationText = "Advanced Android 09:00-10:40 @ A-301",
            contentDescription = "Preview next class",
        )
        return buildData(type, preview)
    }

    private fun snapshotProvider(): NextClassSnapshotProvider {
        val app = applicationContext as ClassingTimetableApplication
        return NextClassSnapshotProvider(app.appContainer)
    }

    private fun buildData(type: ComplicationType, snapshot: NextClassSnapshot): ComplicationData? {
        val contentDescription = PlainComplicationText.Builder(snapshot.contentDescription).build()
        val titleText = if (snapshot.hasLesson) WearI18n.complicationNextClassTitle() else ""

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(snapshot.shortComplicationText).build(),
                    contentDescription = contentDescription,
                ).setTitle(PlainComplicationText.Builder(titleText).build())
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(snapshot.longComplicationText).build(),
                    contentDescription = contentDescription,
                ).setTitle(PlainComplicationText.Builder(titleText).build())
                    .build()
            }

            else -> null
        }
    }
}
