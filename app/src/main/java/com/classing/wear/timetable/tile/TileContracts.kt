package com.classing.wear.timetable.tile

import com.classing.wear.timetable.core.i18n.WearI18n
import java.time.Instant

data class TileScheduleSnapshot(
    val title: String,
    val subtitle: String,
    val timestamp: Instant,
)

interface TileDataProvider {
    suspend fun buildSnapshot(): TileScheduleSnapshot
}

interface TileUpdateRequester {
    fun requestUpdate()
}

class PlaceholderTileDataProvider : TileDataProvider {
    override suspend fun buildSnapshot(): TileScheduleSnapshot {
        return TileScheduleSnapshot(
            title = WearI18n.tilePlaceholderTitle(),
            subtitle = WearI18n.tilePlaceholderSubtitle(),
            timestamp = Instant.now(),
        )
    }
}

class NoOpTileUpdateRequester : TileUpdateRequester {
    override fun requestUpdate() {
        // TODO: 接入 androidx.wear.tiles 并调用 TileService.getUpdater(context).requestUpdate(...)
    }
}
