package com.classing.wear.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.classing.wear.timetable.ui.ClassingTimetableApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ClassingTimetableApplication
        setContent {
            ClassingTimetableApp(app.appContainer)
        }
    }
}
