package com.example.classtimetable.wear.tile

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.classtimetable.wear.sync.CourseRepository

class ScheduleTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val first = CourseRepository(this).load().firstOrNull()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, "下节课").build()
            )
            .addContent(
                Text.Builder(this, first?.name ?: "暂无课程")
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(
                                ModifiersBuilders.Clickable.Builder()
                                    .setId("open")
                                    .setOnClick(
                                        androidx.wear.protolayout.ActionBuilders.LaunchAction.Builder().build()
                                    ).build()
                            ).build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                androidx.wear.protolayout.LayoutElementBuilders.Layout.Builder()
                                    .setRoot(content)
                                    .build()
                            ).build()
                    ).build()
            ).build()
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion("1")
            .setIdToImageMapping(emptyMap())
            .build()
    }
}
