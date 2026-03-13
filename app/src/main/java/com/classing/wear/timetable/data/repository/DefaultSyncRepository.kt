package com.classing.wear.timetable.data.repository

import com.classing.wear.timetable.data.local.dao.SyncMetadataDao
import com.classing.wear.timetable.data.local.entity.SyncMetadataEntity
import com.classing.wear.timetable.data.mapper.asDomain
import com.classing.wear.timetable.data.sync.LocalChangeSet
import com.classing.wear.timetable.data.sync.PhoneSyncManager
import com.classing.wear.timetable.data.sync.SyncPayloadApplier
import com.classing.wear.timetable.domain.model.ConflictStrategy
import com.classing.wear.timetable.domain.model.SyncMetadata
import com.classing.wear.timetable.domain.model.SyncMode
import com.classing.wear.timetable.domain.model.SyncResult
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class DefaultSyncRepository(
    private val syncMetadataDao: SyncMetadataDao,
    private val phoneSyncManager: PhoneSyncManager,
    private val payloadApplier: SyncPayloadApplier,
    private val conflictStrategy: ConflictStrategy = ConflictStrategy.LATEST_VERSION_WINS,
) : SyncRepository {

    private val state = MutableStateFlow<SyncState>(SyncState.Idle)

    override fun observeSyncState(): Flow<SyncState> = state.asStateFlow()

    override fun observeSyncMetadata(): Flow<SyncMetadata> {
        return syncMetadataDao.observe().map { entity ->
            (entity ?: defaultMetadata()).asDomain()
        }
    }

    override suspend fun sync(mode: SyncMode): SyncResult {
        state.value = SyncState.Syncing

        val currentMeta = syncMetadataDao.get() ?: defaultMetadata()
        return try {
            val payload = phoneSyncManager.requestFromPhone(mode, currentMeta.dataVersion)
            val applyResult = payloadApplier.apply(payload, mode)

            // Conflict strategy hook: real app can branch on server conflict payload here.
            val ack = phoneSyncManager.submitWatchChanges(
                LocalChangeSet(
                    mode = mode,
                    localVersion = applyResult.dataVersion,
                ),
            )

            val now = Instant.now()
            val metadata = currentMeta.copy(
                lastFullSyncAt = if (mode == SyncMode.FULL) now else currentMeta.lastFullSyncAt,
                lastDeltaSyncAt = if (mode == SyncMode.DELTA) now else currentMeta.lastDeltaSyncAt,
                lastSuccessAt = now,
                lastErrorMessage = null,
                dataVersion = maxOf(applyResult.dataVersion, ack.acceptedVersion),
                pendingChanges = if (conflictStrategy == ConflictStrategy.PHONE_WINS) 0 else currentMeta.pendingChanges,
            )
            syncMetadataDao.upsert(metadata)

            state.value = SyncState.Success(now)
            SyncResult(
                success = true,
                syncedRecords = applyResult.recordsWritten,
                conflictCount = ack.conflictCount,
                message = "Sync completed with $conflictStrategy",
            )
        } catch (t: Throwable) {
            val now = Instant.now()
            syncMetadataDao.upsert(
                currentMeta.copy(
                    lastErrorMessage = t.message,
                    lastDeltaSyncAt = if (mode == SyncMode.DELTA) now else currentMeta.lastDeltaSyncAt,
                ),
            )
            state.value = SyncState.Failed(t.message ?: "unknown error")
            SyncResult(
                success = false,
                syncedRecords = 0,
                conflictCount = 0,
                message = t.message ?: "sync failed",
            )
        }
    }

    private fun defaultMetadata(): SyncMetadataEntity {
        return SyncMetadataEntity(
            id = 0,
            lastFullSyncAt = null,
            lastDeltaSyncAt = null,
            lastSuccessAt = null,
            lastErrorMessage = null,
            dataVersion = 0,
            pendingChanges = 0,
        )
    }
}
