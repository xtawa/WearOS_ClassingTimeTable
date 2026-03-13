package com.classing.wear.timetable.tile

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
            title = "下一节课",
            subtitle = "接入 Wear Tiles API 后显示实时数据",
            timestamp = Instant.now(),
        )
    }
}

class NoOpTileUpdateRequester : TileUpdateRequester {
    override fun requestUpdate() {
        // TODO: 接入 androidx.wear.tiles 并调用 TileService.getUpdater(context).requestUpdate(...)
    }
}
