package com.classing.wear.timetable.data.sync

import com.classing.wear.timetable.domain.model.SyncMode

interface PhoneSyncManager {
    suspend fun requestFromPhone(mode: SyncMode, sinceVersion: Long): RemoteSchedulePayload
    suspend fun submitWatchChanges(changeSet: LocalChangeSet): RemoteSyncAck
}
