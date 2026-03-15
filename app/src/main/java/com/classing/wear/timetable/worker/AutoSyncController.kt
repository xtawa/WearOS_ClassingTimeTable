package com.classing.wear.timetable.worker

import android.content.Context

class AutoSyncController(
    private val onEnable: () -> Unit,
    private val onDisable: () -> Unit,
) {
    constructor(context: Context) : this(
        onEnable = { SyncWorker.enqueuePeriodic(context) },
        onDisable = { SyncWorker.cancelPeriodic(context) },
    )

    fun setEnabled(enabled: Boolean) {
        if (enabled) onEnable() else onDisable()
    }
}
