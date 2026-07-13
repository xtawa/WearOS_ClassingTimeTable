package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingNavigationResolutionTest {
    @Test
    fun previousStep_matchesToolbarAndSystemBackBehavior() {
        assertEquals(1, previousOnboardingStep(2))
        assertEquals(0, previousOnboardingStep(1))
        assertEquals(0, previousOnboardingStep(0))
    }
    @Test
    fun shouldCompleteOnImportSelection_returnsFalseForLaterOption() {
        assertFalse(shouldCompleteOnImportSelection(OnboardingImportTarget.NONE))
    }

    @Test
    fun shouldCompleteOnImportSelection_returnsTrueForImportOption() {
        assertTrue(shouldCompleteOnImportSelection(OnboardingImportTarget.ICS))
    }

    @Test
    fun resolveOnboardingNavigation_routesIcsToImportWithIcsFocus() {
        val decision = resolveOnboardingNavigation(completion(importTarget = OnboardingImportTarget.ICS))

        assertNull(decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun resolveOnboardingNavigation_routesJsonToImportWithJsonFocus() {
        val decision = resolveOnboardingNavigation(completion(importTarget = OnboardingImportTarget.JSON))

        assertNull(decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun resolveOnboardingNavigation_routesManualToImportWithManualFocus() {
        val decision = resolveOnboardingNavigation(completion(importTarget = OnboardingImportTarget.MANUAL_ENTRY))

        assertNull(decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun resolveOnboardingNavigation_routesCloudSyncToCloudSettings_whenExplicitlyRequested() {
        val decision = resolveOnboardingNavigation(
            completion(
                importTarget = OnboardingImportTarget.CLOUD_SYNC,
                openCloudSyncSettingsAfterFinish = true,
            ),
        )

        assertEquals(SettingsPage.CloudSync, decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun resolveOnboardingNavigation_routesBackupToBackupRestore() {
        val decision = resolveOnboardingNavigation(completion(importTarget = OnboardingImportTarget.BACKUP_RESTORE))

        assertNull(decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun resolveOnboardingNavigation_returnsNullTargetForNone() {
        val decision = resolveOnboardingNavigation(completion(importTarget = OnboardingImportTarget.NONE))

        assertNull(decision.targetSettingsPage)
        assertNull(decision.importFocusMethod)
    }

    @Test
    fun consumeImportFocus_clearsOnlyMatchedFocus() {
        assertNull(consumeImportFocus(ImportFocusMethod.JSON, ImportFocusMethod.JSON))
        assertEquals(
            ImportFocusMethod.ICS,
            consumeImportFocus(ImportFocusMethod.ICS, ImportFocusMethod.JSON),
        )
    }

    private fun completion(
        importTarget: OnboardingImportTarget,
        openCloudSyncSettingsAfterFinish: Boolean = false,
        openSettingsHomeAfterFinish: Boolean = false,
    ): OnboardingCompletion {
        return OnboardingCompletion(
            importTarget = importTarget,
            wearSyncMode = WearSyncMode.AUTO,
            openCloudSyncSettingsAfterFinish = openCloudSyncSettingsAfterFinish,
            cloudProvider = CloudProviderUi.WEBDAV,
            cloudSyncEnabled = false,
            cloudServerUrl = "",
            cloudRemotePath = "/classing/classing_sync.json",
            cloudUsername = "",
            cloudPassword = "",
            cloudDriveFileName = "classing_sync.json",
            reminderEnabled = false,
            showWeekend = true,
            semesterWeekStartDate = LocalDate.of(2026, 1, 1),
            openSettingsHomeAfterFinish = openSettingsHomeAfterFinish,
        )
    }
}
