package com.xtawa.classingtime.reminder

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRebuildReceiverTest {
    @Test
    fun shouldRebuildForAction_acceptsClockAndBootEvents() {
        assertTrue(shouldRebuildForAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(shouldRebuildForAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(shouldRebuildForAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(shouldRebuildForAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertFalse(shouldRebuildForAction(Intent.ACTION_SCREEN_ON))
    }
}
