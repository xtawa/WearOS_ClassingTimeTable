package com.classing.wear.timetable.core

import android.content.Context
import androidx.room.Room
import com.classing.wear.timetable.core.time.SystemTimeProvider
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.data.local.AppDatabase
import com.classing.wear.timetable.data.preferences.DefaultSettingsRepository
import com.classing.wear.timetable.data.repository.DefaultScheduleRepository
import com.classing.wear.timetable.data.repository.DefaultSyncRepository
import com.classing.wear.timetable.data.sync.DefaultPhoneSyncManager
import com.classing.wear.timetable.data.sync.MockSyncDataSource
import com.classing.wear.timetable.data.sync.SyncPayloadApplier
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.SyncRepository
import com.classing.wear.timetable.sync.MobileSyncRequester

interface AppContainer {
    val database: AppDatabase
    val timeProvider: TimeProvider
    val scheduleRepository: ScheduleRepository
    val syncRepository: SyncRepository
    val settingsRepository: SettingsRepository
    val mobileSyncRequester: MobileSyncRequester
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
        .fallbackToDestructiveMigration()
        .build()

    override val timeProvider: TimeProvider = SystemTimeProvider()

    private val syncDataSource = MockSyncDataSource()
    private val syncPayloadApplier = SyncPayloadApplier(database)
    private val phoneSyncManager = DefaultPhoneSyncManager(syncDataSource)

    override val scheduleRepository: ScheduleRepository = DefaultScheduleRepository(
        semesterDao = database.semesterDao(),
        courseDao = database.courseDao(),
        sessionDao = database.courseSessionDao(),
        slotDao = database.timeSlotDao(),
        exceptionDao = database.scheduleExceptionDao(),
        timeProvider = timeProvider,
    )

    override val syncRepository: SyncRepository = DefaultSyncRepository(
        syncMetadataDao = database.syncMetadataDao(),
        phoneSyncManager = phoneSyncManager,
        payloadApplier = syncPayloadApplier,
    )

    override val settingsRepository: SettingsRepository = DefaultSettingsRepository(appContext)

    override val mobileSyncRequester: MobileSyncRequester = MobileSyncRequester(appContext)
}
