package com.classing.wear.timetable.reminder

import android.content.Context
import com.classing.client.exam.ExamReminderReceiver
import com.classing.wear.timetable.ClassingTimetableApplication
import kotlinx.coroutines.flow.first

class WearExamReminderReceiver : ExamReminderReceiver() {
    private fun container(context: Context) = (context.applicationContext as ClassingTimetableApplication).appContainer
    override suspend fun exams(context: Context) = container(context).database.examDao().getAll().map { it.toExam() }
    override suspend fun enabled(context: Context) = container(context).settingsRepository.observePreferences().first().remindersEnabled
}
