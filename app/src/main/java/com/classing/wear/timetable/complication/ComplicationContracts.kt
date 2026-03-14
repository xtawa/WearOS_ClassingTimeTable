package com.classing.wear.timetable.complication

import com.classing.wear.timetable.core.i18n.WearI18n
import java.time.Instant

data class ComplicationSnapshot(
    val shortText: String,
    val longText: String,
    val timestamp: Instant,
)

interface ComplicationDataProvider {
    suspend fun buildSnapshot(): ComplicationSnapshot
}

interface ComplicationUpdateRequester {
    fun requestUpdate()
}

class PlaceholderComplicationProvider : ComplicationDataProvider {
    override suspend fun buildSnapshot(): ComplicationSnapshot {
        return ComplicationSnapshot(
            shortText = WearI18n.complicationNoClassShortText(),
            longText = WearI18n.complicationPlaceholderLongText(),
            timestamp = Instant.now(),
        )
    }
}

class NoOpComplicationUpdateRequester : ComplicationUpdateRequester {
    override fun requestUpdate() {
        // TODO: 接入 androidx.wear.watchface.complications.datasource API.
    }
}
