package com.classing.wear.timetable.tile

import androidx.wear.tiles.ColorBuilders
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * The next class tile. A tile is glanced at, not read, so the order is countdown first, then the
 * course, then the supporting detail. Every field the snapshot carries is still rendered, just
 * ranked by how useful it is at a glance.
 */
class NextClassTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return tileScope.future {
            val snapshot = runCatching { snapshotProvider().loadSnapshot() }
                .getOrElse { unavailableSnapshot() }
            buildTile(snapshot)
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun buildTile(snapshot: NextClassSnapshot): TileBuilders.Tile {
        return TileBuilders.Tile.Builder()
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
    }

    private fun snapshotProvider(): NextClassSnapshotProvider {
        val app = applicationContext as ClassingTimetableApplication
        return NextClassSnapshotProvider(app.appContainer)
    }

    private fun unavailableSnapshot(): NextClassSnapshot {
        return NextClassSnapshot(
            hasLesson = false,
            courseTitle = "暂时无法加载",
            weekText = "",
            dateText = "Classing",
            timeText = "稍后重试",
            teacherText = "",
            locationText = "",
            countdownText = "",
            shortComplicationText = "--",
            longComplicationText = "暂时无法加载课程",
            contentDescription = "暂时无法加载课程",
        )
    }

    private fun buildRoot(snapshot: NextClassSnapshot): LayoutElementBuilders.LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // The countdown is the reason to look at the tile, so it leads and carries the accent.
        if (snapshot.countdownText.isNotBlank()) {
            content
                .addContent(textElement(snapshot.countdownText, 13f, COLOR_ACCENT, bold = true))
                .addContent(spacer(3f))
        }

        if (snapshot.courseTitle.isNotBlank()) {
            content.addContent(textElement(snapshot.courseTitle, 17f, COLOR_TEXT, bold = true))
        }

        if (snapshot.timeText.isNotBlank()) {
            content
                .addContent(spacer(3f))
                .addContent(textElement(snapshot.timeText, 13f, COLOR_TEXT))
        }

        if (snapshot.locationText.isNotBlank()) {
            content
                .addContent(spacer(2f))
                .addContent(textElement(snapshot.locationText, 12f, COLOR_DIM))
        }

        if (snapshot.teacherText.isNotBlank()) {
            content
                .addContent(spacer(2f))
                .addContent(textElement(snapshot.teacherText, 12f, COLOR_DIM))
        }

        // Week and date are context, not the headline, so they sit last in the muted role.
        val footer = listOf(snapshot.weekText, snapshot.dateText)
            .filter { it.isNotBlank() }
            .joinToString("  ")
        if (footer.isNotBlank()) {
            content
                .addContent(spacer(5f))
                .addContent(textElement(footer, 11f, COLOR_FAINT))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(content.build())
            .build()
    }

    private fun textElement(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): LayoutElementBuilders.LayoutElement {
        val fontStyle = LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(sizeSp))
            .setColor(ColorBuilders.argb(color))
        if (bold) {
            fontStyle.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
        }
        return LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(fontStyle.build())
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

        // Matches the dark palette used by the app so the tile does not look like a different product.
        private const val COLOR_TEXT = 0xFFF1F2F6.toInt()
        private const val COLOR_DIM = 0xFF9A9CA8.toInt()
        private const val COLOR_FAINT = 0xFF6B6D78.toInt()
        private const val COLOR_ACCENT = 0xFFFFB454.toInt()
    }
}
