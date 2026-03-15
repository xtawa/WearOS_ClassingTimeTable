package com.classing.wear.timetable.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderWorkController(
    private val onEnable: () -> Unit,
    private val onDisable: () -> Unit,
) {
    constructor(context: Context) : this(
        onEnable = {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        },
        onDisable = {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        },
    )

    fun setEnabled(enabled: Boolean) {
        if (enabled) onEnable() else onDisable()
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "wear_reminder_check_worker"
    }
}
