package com.classing.wear.timetable.core.navigation

sealed class Destinations(val route: String) {
    data object Home : Destinations("home")
    data object Week : Destinations("week")
    data object Search : Destinations("search")
    data object Settings : Destinations("settings")
    data object CloudSync : Destinations("cloud_sync")
    data object About : Destinations("about")
    data object CourseDetail : Destinations("course_detail/{courseId}") {
        fun createRoute(courseId: Long): String = "course_detail/$courseId"
    }
}
