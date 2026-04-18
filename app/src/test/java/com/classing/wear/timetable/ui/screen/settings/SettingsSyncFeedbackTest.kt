package com.classing.wear.timetable.ui.screen.settings

import com.classing.wear.timetable.ui.state.SyncFeedback
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSyncFeedbackTest {
    @Test
    fun resolveSyncFeedback_returnsSuccess_whenRequestSucceededAndPhoneConnected() {
        assertEquals(SyncFeedback.SUCCESS, resolveSyncFeedback(Result.success(1)))
    }

    @Test
    fun resolveSyncFeedback_returnsCheckPhone_whenNoConnectedPhone() {
        assertEquals(SyncFeedback.CHECK_PHONE_CONNECTION, resolveSyncFeedback(Result.success(0)))
    }

    @Test
    fun resolveSyncFeedback_returnsCheckPhone_whenRequestFailed() {
        assertEquals(
            SyncFeedback.CHECK_PHONE_CONNECTION,
            resolveSyncFeedback(Result.failure(IllegalStateException("offline"))),
        )
    }
}
