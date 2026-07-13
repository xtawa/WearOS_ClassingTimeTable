package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncModeResolutionTest {

    @Test
    fun detectWearAutoSyncPlan_usesDataLayerForCnLe() {
        val companion = WearOsCompanionInfo(
            packageName = "com.google.android.wearable.app.cn",
            versionName = "2.5.0",
            isChinaOrLe = true,
        )

        val result = detectWearAutoSyncPlan(companion)

        assertEquals(WearAutoVariant.CN_LE, result.variant)
        assertEquals(WearSyncMode.WEARABLE_API, result.effectiveMode)
    }

    @Test
    fun detectWearAutoSyncPlan_returnsWearableApiForGlobal() {
        val companion = WearOsCompanionInfo(
            packageName = "com.google.android.wearable.app",
            versionName = "2.5.0",
            isChinaOrLe = false,
        )

        val result = detectWearAutoSyncPlan(companion)

        assertEquals(WearAutoVariant.GLOBAL, result.variant)
        assertEquals(WearSyncMode.WEARABLE_API, result.effectiveMode)
    }

    @Test
    fun detectWearAutoSyncPlan_returnsWearableApiForUnknown() {
        val result = detectWearAutoSyncPlan(null)

        assertEquals(WearAutoVariant.UNKNOWN, result.variant)
        assertEquals(WearSyncMode.WEARABLE_API, result.effectiveMode)
    }

    @Test
    fun isLeVersion_matchesExpectedTokens() {
        assertTrue(isLeVersion("3.1.0-le"))
        assertTrue(isLeVersion("3_1_0_LE_build"))
        assertFalse(isLeVersion("3.1.0"))
    }

    @Test fun legacyWearOsAppSettingMigratesToDataLayer() {
        assertEquals(WearSyncMode.WEARABLE_API, migrateWearSyncMode("WEAROS_APP"))
        assertEquals(WearSyncMode.WEARABLE_API, migrateWearSyncMode("WEARABLE_API"))
        assertEquals(WearSyncMode.AUTO, migrateWearSyncMode("AUTO"))
    }
}
