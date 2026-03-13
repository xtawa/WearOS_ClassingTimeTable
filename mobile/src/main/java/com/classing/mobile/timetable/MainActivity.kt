package com.classing.mobile.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.classing.mobile.timetable.screen.CourseEditorScreen
import com.classing.mobile.timetable.screen.ImportPreviewScreen
import com.classing.mobile.timetable.screen.ImportScreen
import com.classing.mobile.timetable.screen.ReminderSettingsScreen
import com.classing.mobile.timetable.screen.SemesterManagementScreen
import com.classing.mobile.timetable.screen.SyncStatusScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileApp() }
    }
}

@Composable
private fun MobileApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "import") {
        composable("import") { ImportScreen(onPreview = { nav.navigate("preview") }) }
        composable("preview") { ImportPreviewScreen(onConfirm = { nav.navigate("editor") }) }
        composable("editor") { CourseEditorScreen() }
        composable("semester") { SemesterManagementScreen() }
        composable("reminder") { ReminderSettingsScreen() }
        composable("sync") { SyncStatusScreen() }
        composable("settings") { Text("Settings") }
    }
}
