package com.classing.wear.timetable.data.sync

interface SyncDataSource {
    suspend fun fetchFull(): RemoteSchedulePayload
    suspend fun fetchDelta(sinceVersion: Long): RemoteSchedulePayload
    suspend fun pushLocalChanges(changeSet: LocalChangeSet): RemoteSyncAck
}
