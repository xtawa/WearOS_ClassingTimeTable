package com.classing.wear.timetable.complication

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
            shortText = "无课",
            longText = "接入 Complication API 后显示倒计时",
            timestamp = Instant.now(),
        )
    }
}

class NoOpComplicationUpdateRequester : ComplicationUpdateRequester {
    override fun requestUpdate() {
        // TODO: 接入 androidx.wear.watchface.complications.datasource API.
    }
}
