package com.xtawa.classingtime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MobilePrefsStoreOnboardingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun onboardingDefault_isNotCompleted() {
        assertFalse(MobilePrefsStore.isOnboardingCompleted(context))
    }

    @Test
    fun setOnboardingCompleted_persistsValue() {
        MobilePrefsStore.setOnboardingCompleted(context, true)

        assertTrue(MobilePrefsStore.isOnboardingCompleted(context))
    }

    @Test
    fun ensureOnboardingCompletedForLegacyUser_doesNothingForFreshInstall() {
        val updated = MobilePrefsStore.ensureOnboardingCompletedForLegacyUser(context)

        assertFalse(updated)
        assertFalse(MobilePrefsStore.isOnboardingCompleted(context))
    }

    @Test
    fun ensureOnboardingCompletedForLegacyUser_marksCompletedWhenLegacyDataExists() {
        MobilePrefsStore.saveSettings(
            context,
            defaultSettings().copy(rawIcs = "BEGIN:VCALENDAR\nEND:VCALENDAR"),
        )

        val updated = MobilePrefsStore.ensureOnboardingCompletedForLegacyUser(context)

        assertTrue(updated)
        assertTrue(MobilePrefsStore.isOnboardingCompleted(context))
    }

    private fun defaultSettings(): MobileSettings {
        return MobileSettings(
            showWeekend = true,
            reminderEnabled = false,
            reminderMinutes = 15,
            keepAliveLevel = "BALANCED",
            experimentalAccessibilityKeepAliveEnabled = false,
            rawIcs = "",
            parseMessage = "",
            wearSyncMode = "AUTO",
            weekNumberMode = "NATURAL",
            semesterWeekStartDate = "",
            weekStartDay = "MONDAY",
            cloudSyncEnabled = false,
            cloudServerUrl = "",
            cloudRemotePath = "/classing/classing_sync.json",
            cloudUsername = "",
            cloudConfigPushStatus = "",
            cloudLastResult = "",
            cloudLastSyncedAt = 0L,
        )
    }
}
