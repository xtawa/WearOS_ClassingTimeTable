package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileBackStateTest {
    @Test
    fun reduceBackState_returnsNull_whenNotInSettingsLayer() {
        val state = MobileBackState(
            layer = MobileLayer.Schedule,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Calendar,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertNull(reduced)
    }

    @Test
    fun reduceBackState_closesImportJsonPromptFirst() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            settingsPage = SettingsPage.Import,
            previousMainLayer = MobileLayer.Calendar,
            showImportJsonPromptPage = true,
        )

        val reduced = reduceBackState(state)

        assertEquals(false, reduced?.showImportJsonPromptPage)
        assertEquals(SettingsPage.Import, reduced?.settingsPage)
    }

    @Test
    fun reduceBackState_returnsFromSecondaryToSettingsMain() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            settingsPage = SettingsPage.CloudSync,
            previousMainLayer = MobileLayer.Calendar,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Settings, reduced?.layer)
        assertEquals(SettingsPage.Main, reduced?.settingsPage)
    }

    @Test
    fun reduceBackState_returnsFromSettingsMainToPreviousMainLayer() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Calendar,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Calendar, reduced?.layer)
        assertEquals(SettingsPage.Main, reduced?.settingsPage)
    }
}

