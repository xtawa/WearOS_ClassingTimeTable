package com.classing.wear.timetable.data.sync

import com.classing.wear.timetable.domain.model.SyncMode

class DefaultPhoneSyncManager(
    private val syncDataSource: SyncDataSource,
) : PhoneSyncManager {
    override suspend fun requestFromPhone(mode: SyncMode, sinceVersion: Long): RemoteSchedulePayload {
        return when (mode) {
            SyncMode.FULL -> syncDataSource.fetchFull()
            SyncMode.DELTA -> syncDataSource.fetchDelta(sinceVersion)
        }
    }

    override suspend fun submitWatchChanges(changeSet: LocalChangeSet): RemoteSyncAck {
        return syncDataSource.pushLocalChanges(changeSet)
    }
}
