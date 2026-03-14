package com.classing.wear.timetable.tile

import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.protolayout.ResourceBuilders
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.widget.NextClassSnapshot
import com.classing.wear.timetable.widget.NextClassSnapshotProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking

class NextClassTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val snapshot = runBlocking { snapshotProvider().loadSnapshot() }
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(TILE_FRESHNESS_INTERVAL_MILLIS)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildRoot(snapshot))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun snapshotProvider(): NextClassSnapshotProvider {
        val app = applicationContext as ClassingTimetableApplication
        return NextClassSnapshotProvider(app.appContainer)
    }

    private fun buildRoot(snapshot: NextClassSnapshot): LayoutElementBuilders.LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(textElement(snapshot.courseTitle, 16f))
            .addContent(spacer(4f))
            .addContent(textElement(snapshot.timeText, 14f))

        if (snapshot.locationText.isNotBlank()) {
            content
                .addContent(spacer(2f))
                .addContent(textElement(snapshot.locationText, 12f))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(content.build())
            .build()
    }

    private fun textElement(text: String, sizeSp: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .build(),
            )
            .setMaxLines(1)
            .build()
    }

    private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(heightDp))
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "next_class_tile_v1"
        private const val TILE_FRESHNESS_INTERVAL_MILLIS = 60_000L
    }
}
