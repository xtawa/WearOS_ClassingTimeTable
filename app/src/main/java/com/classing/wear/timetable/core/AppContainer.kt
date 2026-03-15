package com.classing.wear.timetable.core

import android.content.Context
import androidx.room.Room
import com.classing.wear.timetable.core.time.SystemTimeProvider
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.data.local.AppDatabase
import com.classing.wear.timetable.data.local.AppDatabaseMigrations
import com.classing.wear.timetable.data.preferences.DefaultSettingsRepository
import com.classing.wear.timetable.data.repository.DefaultScheduleRepository
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.worker.AutoSyncController
import com.classing.wear.timetable.worker.ReminderWorkController

interface AppContainer {
    val database: AppDatabase
    val timeProvider: TimeProvider
    val scheduleRepository: ScheduleRepository
    val settingsRepository: SettingsRepository
    val mobileSyncRequester: MobileSyncRequester
    val autoSyncController: AutoSyncController
    val reminderWorkController: ReminderWorkController
}

class DefaultAppContainer(
    context: Context,
) : AppContainer {
    private val appContext = context.applicationContext

    override val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "classing_timetable.db",
    )
        .addMigrations(AppDatabaseMigrations.MIGRATION_1_2)
        .build()

    override val timeProvider: TimeProvider = SystemTimeProvider()

    override val scheduleRepository: ScheduleRepository = DefaultScheduleRepository(
        semesterDao = database.semesterDao(),
        courseDao = database.courseDao(),
        sessionDao = database.courseSessionDao(),
        slotDao = database.timeSlotDao(),
        exceptionDao = database.scheduleExceptionDao(),
        timeProvider = timeProvider,
    )

    override val settingsRepository: SettingsRepository = DefaultSettingsRepository(appContext)

    override val mobileSyncRequester: MobileSyncRequester = MobileSyncRequester(appContext)
    override val autoSyncController: AutoSyncController = AutoSyncController(appContext)
    override val reminderWorkController: ReminderWorkController = ReminderWorkController(appContext)
}
