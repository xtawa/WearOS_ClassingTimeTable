package com.classing.wear.timetable.data.sync

import com.classing.wear.timetable.data.mock.MockRemotePayloadFactory
import kotlinx.coroutines.delay

class MockSyncDataSource : SyncDataSource {
    override suspend fun fetchFull(): RemoteSchedulePayload {
        delay(400)
        return MockRemotePayloadFactory.fullPayload()
    }

    override suspend fun fetchDelta(sinceVersion: Long): RemoteSchedulePayload {
        delay(250)
        return MockRemotePayloadFactory.deltaPayload(sinceVersion)
    }

    override suspend fun pushLocalChanges(changeSet: LocalChangeSet): RemoteSyncAck {
        delay(150)
        return RemoteSyncAck(
            success = true,
            acceptedVersion = changeSet.localVersion,
            conflictCount = 0,
            message = "Mock: no-op push",
        )
    }
}
