package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileBackStateTest {
    @Test
    fun reduceBackState_returnsNull_whenNotInSettingsLayer() {
        val state = MobileBackState(
            layer = MobileLayer.Schedule,
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertNull(reduced)
    }

    @Test
    fun reduceBackState_closesImportJsonPromptFirst() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.Import,
            previousMainLayer = MobileLayer.Dashboard,
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
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.CloudSync,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Settings, reduced?.layer)
        assertEquals(SettingsPage.Main, reduced?.settingsPage)
    }

    @Test
    fun reduceBackState_returnsFromAccountChildPageToAccount() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.AccountPasswordReset,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Settings, reduced?.layer)
        assertEquals(SettingsPage.Account, reduced?.settingsPage)
    }

    @Test
    fun reduceBackState_returnsFromSettingsMainToPreviousMainLayer() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Dashboard, reduced?.layer)
        assertEquals(SettingsPage.Main, reduced?.settingsPage)
    }

    @Test
    fun reduceBackState_returnsToTimetableFromScheduleCalendarSubview() {
        val state = MobileBackState(
            layer = MobileLayer.Schedule,
            scheduleSubview = ScheduleSubview.Calendar,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Schedule, reduced?.layer)
        assertEquals(ScheduleSubview.Timetable, reduced?.scheduleSubview)
    }

    @Test
    fun reduceBackState_returnsToTimetableFromCourseDetail() {
        val state = MobileBackState(
            layer = MobileLayer.Schedule,
            scheduleSubview = ScheduleSubview.CourseDetail,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(ScheduleSubview.Timetable, reduced?.scheduleSubview)
    }

    @Test
    fun reduceBackState_returnsFromCourseDetailToItsHomeSource() {
        val state = MobileBackState(
            layer = MobileLayer.Schedule,
            scheduleSubview = ScheduleSubview.CourseDetail,
            settingsPage = SettingsPage.Main,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
            detailReturnLayer = MobileLayer.Dashboard,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Dashboard, reduced?.layer)
    }

    @Test
    fun reduceBackState_returnsFromAssistantToItsPreviousMainLayer() {
        val state = MobileBackState(
            layer = MobileLayer.Settings,
            scheduleSubview = ScheduleSubview.Timetable,
            settingsPage = SettingsPage.AskAi,
            previousMainLayer = MobileLayer.Dashboard,
            showImportJsonPromptPage = false,
        )

        val reduced = reduceBackState(state)

        assertEquals(MobileLayer.Dashboard, reduced?.layer)
        assertEquals(SettingsPage.Main, reduced?.settingsPage)
    }
}
