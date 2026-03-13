package com.classing.wear.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 使用 WorkManager 消费提醒，减少广播中重量逻辑。
        val request = OneTimeWorkRequestBuilder<com.classing.wear.timetable.notification.ReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
