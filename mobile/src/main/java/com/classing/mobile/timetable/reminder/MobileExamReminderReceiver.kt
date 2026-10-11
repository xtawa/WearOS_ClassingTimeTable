package com.xtawa.classingtime.reminder

import android.content.Context
import com.classing.client.exam.ExamReminderReceiver
import com.classing.client.exam.ExamStore
import com.xtawa.classingtime.data.MobilePrefsStore

class MobileExamReminderReceiver : ExamReminderReceiver() {
    override suspend fun exams(context: Context) = ExamStore.load(context)
    override suspend fun enabled(context: Context) = MobilePrefsStore.loadSettings(context).reminderEnabled
}
