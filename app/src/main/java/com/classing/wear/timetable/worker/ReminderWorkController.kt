package com.classing.wear.timetable.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import java.util.concurrent.TimeUnit

class ReminderWorkController(
    private val context: Context,
    private val onEnable: (KeepAliveLevel) -> Unit,
    private val onDisable: () -> Unit,
) {
    constructor(context: Context) : this(
        context = context,
        onEnable = { level ->
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            WearReminderAlarmScheduler.refresh(context = context, enabled = true, level = level)
        },
        onDisable = {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WearReminderAlarmScheduler.cancel(context)
        },
    )

    fun setPolicy(enabled: Boolean, level: KeepAliveLevel) {
        if (enabled) onEnable(level) else onDisable()
    }

    fun refresh(level: KeepAliveLevel) {
        WearReminderAlarmScheduler.refresh(context = context, enabled = true, level = level)
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "wear_reminder_check_worker"
    }
}
