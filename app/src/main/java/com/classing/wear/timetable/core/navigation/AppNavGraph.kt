package com.classing.wear.timetable.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.ui.screen.detail.CourseDetailScreen
import com.classing.wear.timetable.ui.screen.detail.CourseDetailViewModel
import com.classing.wear.timetable.ui.screen.home.HomeScreen
import com.classing.wear.timetable.ui.screen.home.HomeViewModel
import com.classing.wear.timetable.ui.screen.settings.SettingsScreen
import com.classing.wear.timetable.ui.screen.settings.SettingsViewModel
import com.classing.wear.timetable.ui.screen.week.WeekScreen
import com.classing.wear.timetable.ui.screen.week.WeekViewModel
import com.classing.wear.timetable.ui.screen.search.SearchScreen
import com.classing.wear.timetable.ui.screen.search.SearchViewModel

@Composable
fun AppNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.Home.route,
    ) {
        composable(Destinations.Home.route) {
            val vm: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HomeViewModel(
                            scheduleRepository = appContainer.scheduleRepository,
                            settingsRepository = appContainer.settingsRepository,
                            mobileSyncRequester = appContainer.mobileSyncRequester,
                            timeProvider = appContainer.timeProvider,
                        )
                    }
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onOpenWeek = { navController.navigate(Destinations.Week.route) },
                onOpenSearch = { navController.navigate(Destinations.Search.route) },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) },
                onLessonClick = { courseId -> navController.navigate(Destinations.CourseDetail.createRoute(courseId)) },
                onRetrySync = vm::retrySync,
            )
        }

        composable(Destinations.Search.route) {
            val vm: SearchViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SearchViewModel(scheduleRepository = appContainer.scheduleRepository)
                    }
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onQueryChange = vm::onQueryChange,
                onCourseClick = { courseId -> navController.navigate(Destinations.CourseDetail.createRoute(courseId)) },
            )
        }

        composable(Destinations.Week.route) {
            val vm: WeekViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        WeekViewModel(
                            scheduleRepository = appContainer.scheduleRepository,
                            settingsRepository = appContainer.settingsRepository,
                            timeProvider = appContainer.timeProvider,
                        )
                    }
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            WeekScreen(
                state = state,
                onPreviousWeek = vm::previousWeek,
                onNextWeek = vm::nextWeek,
                onCurrentWeek = vm::jumpToCurrentWeek,
                onLessonClick = { courseId -> navController.navigate(Destinations.CourseDetail.createRoute(courseId)) },
            )
        }

        composable(
            route = Destinations.CourseDetail.route,
            arguments = listOf(navArgument("courseId") { type = NavType.LongType }),
        ) {
            val vm: CourseDetailViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        CourseDetailViewModel(
                            savedStateHandle = createSavedStateHandle(),
                            scheduleRepository = appContainer.scheduleRepository,
                            timeProvider = appContainer.timeProvider,
                        )
                    }
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            CourseDetailScreen(
                state = state,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.Settings.route) {
            val vm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            settingsRepository = appContainer.settingsRepository,
                            mobileSyncRequester = appContainer.mobileSyncRequester,
                            autoSyncController = appContainer.autoSyncController,
                            reminderWorkController = appContainer.reminderWorkController,
                        )
                    }
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onToggleDynamicColor = vm::toggleDynamicColor,
                onToggleReminder = vm::toggleReminder,
                onToggleAutoSync = vm::toggleAutoSync,
                onToggleWeekend = vm::toggleWeekend,
                onToggleShowCompletedToday = vm::toggleShowCompletedToday,
                onToggleTileShowTeacher = vm::toggleTileShowTeacher,
                onToggleTileShowLocation = vm::toggleTileShowLocation,
                onToggleTileShowCountdown = vm::toggleTileShowCountdown,
                onToggleTileShowCourseName = vm::toggleTileShowCourseName,
                onToggleTileShowCurrentWeek = vm::toggleTileShowCurrentWeek,
                onToggleTileShowTimeRange = vm::toggleTileShowTimeRange,
                onForceFullSync = vm::forceFullSync,
            )
        }
    }
}
