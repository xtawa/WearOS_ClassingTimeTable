package com.classing.wear.timetable.widget

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.classing.wear.timetable.complication.NextClassComplicationService
import com.classing.wear.timetable.tile.NextClassTileService

object WearSurfaceUpdateRequester {
    fun requestAll(context: Context) {
        runCatching {
            TileService.getUpdater(context)
                .requestUpdate(NextClassTileService::class.java)
        }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(
                        context,
                        NextClassComplicationService::class.java,
                    ),
                )
                .requestUpdateAll()
        }
    }
}
