package com.xtawa.classingtime.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.OfficialSyncFrequency
import java.util.concurrent.TimeUnit

object CloudSyncEngine {
    private const val PREF_NAME = "mobile_cloud_sync_queue"
    private const val KEY_DIRTY_GENERATION = "dirty_generation"
    private const val UNIQUE_IMMEDIATE = "cloud_sync_v2_immediate"
    private const val UNIQUE_PERIODIC = "cloud_sync_v2_periodic"
    private const val INPUT_TRIGGER = "trigger"

    fun enqueue(context: Context, trigger: String, markDirty: Boolean = true) {
        val appContext = context.applicationContext
        if (markDirty) incrementGeneration(appContext)
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf(INPUT_TRIGGER to trigger))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_IMMEDIATE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        saveQueueStatus(appContext, "Cloud sync queued")
    }

    fun schedulePeriodic(context: Context) {
        val settings = MobilePrefsStore.loadSettings(context.applicationContext)
        val frequencyMinutes = resolveIntervalMinutes(settings)
        if (frequencyMinutes == null) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(frequencyMinutes, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf(INPUT_TRIGGER to CloudSyncContracts.TRIGGER_FOREGROUND_TICK))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun resolveIntervalMinutes(settings: com.xtawa.classingtime.data.MobileSettings): Long? {
        if (!settings.cloudSyncEnabled) return null
        return when (CloudProvider.fromWire(settings.cloudProvider)) {
            CloudProvider.OFFICIAL -> {
                when (settings.officialSyncFrequency) {
                    OfficialSyncFrequency.MANUAL_ONLY -> null
                    OfficialSyncFrequency.EVERY_15_MIN -> 15L
                    OfficialSyncFrequency.EVERY_30_MIN -> 30L
                    OfficialSyncFrequency.EVERY_1_HOUR -> 60L
                    OfficialSyncFrequency.EVERY_3_HOURS -> 180L
                }
            }
            else -> 30L
        }
    }

    internal fun generation(context: Context): Long = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_DIRTY_GENERATION, 0L)

    private fun incrementGeneration(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DIRTY_GENERATION, prefs.getLong(KEY_DIRTY_GENERATION, 0L) + 1L).commit()
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun saveQueueStatus(context: Context, status: String) {
        val settings = MobilePrefsStore.loadSettings(context)
        MobilePrefsStore.saveSettings(context, settings.copy(cloudLastResult = status))
    }
}

class CloudSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val startedGeneration = CloudSyncEngine.generation(applicationContext)
        val result = MobileCloudSyncCoordinator.requestCloudSync(
            context = applicationContext,
            trigger = inputData.getString("trigger").orEmpty().ifBlank { CloudSyncContracts.TRIGGER_FOREGROUND_TICK },
            force = true,
            alsoPushConfigToWear = false,
        )
        if (result.isFailure) return Result.retry()
        if (CloudSyncEngine.generation(applicationContext) > startedGeneration) {
            CloudSyncEngine.enqueue(applicationContext, CloudSyncContracts.TRIGGER_SETTINGS_CHANGED, markDirty = false)
        }
        return Result.success()
    }
}
