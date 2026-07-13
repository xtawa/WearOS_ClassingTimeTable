package com.xtawa.classingtime.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xtawa.classingtime.data.MobilePrefsStore

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldRebuildForAction(intent.action)) return
        val appContext = context.applicationContext
        val settings = MobilePrefsStore.loadSettings(appContext)
        ReminderScheduler.sync(
            context = appContext,
            enabled = settings.reminderEnabled,
            keepAliveLevel = KeepAliveLevel.fromRaw(settings.keepAliveLevel),
            reminderMinutes = settings.reminderMinutes,
        )
        DailyBriefingScheduler.sync(appContext, settings)
    }
}

internal fun shouldRebuildForAction(action: String?): Boolean = action in setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
)
