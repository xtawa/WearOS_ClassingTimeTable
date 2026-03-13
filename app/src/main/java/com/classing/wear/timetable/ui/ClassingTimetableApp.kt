package com.classing.wear.timetable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.navigation.AppNavGraph
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme

@Composable
fun ClassingTimetableApp(appContainer: AppContainer) {
    val preferences by appContainer.settingsRepository
        .observePreferences()
        .collectAsStateWithLifecycle(initialValue = UserPreferences())

    ClassingTimetableTheme(useDynamicColor = preferences.dynamicColor) {
        AppNavGraph(appContainer)
    }
}
