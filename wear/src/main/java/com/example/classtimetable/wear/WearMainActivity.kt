package com.example.classtimetable.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.classtimetable.wear.sync.CourseRepository

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearScheduleScreen(CourseRepository(this).load())
            }
        }
    }
}

@Composable
private fun WearScheduleScreen(courses: List<com.example.classtimetable.shared.Course>) {
    val state = remember { mutableStateOf(courses) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Text("今日课程", style = MaterialTheme.typography.titleMedium) }
        items(state.value.take(4)) { c ->
            Card {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("${c.startTime}-${c.endTime}")
                    Text(c.classroom)
                }
            }
        }
    }
}
