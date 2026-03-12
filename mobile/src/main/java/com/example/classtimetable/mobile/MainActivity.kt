package com.example.classtimetable.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.classtimetable.shared.Course

class MainActivity : ComponentActivity() {
    private val syncClient by lazy { WearSyncClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneScheduleScreen(syncClient)
            }
        }
    }
}

@Composable
private fun PhoneScheduleScreen(syncClient: WearSyncClient) {
    val courses = remember {
        mutableStateListOf(
            Course("高等数学", "A-201", 1, "08:00", "09:40"),
            Course("操作系统", "B-503", 1, "10:00", "11:40"),
            Course("软件工程", "C-402", 3, "14:00", "15:40")
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("课程管理（Material 3）", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { syncClient.pushCourses(courses) }) {
                    Text("同步到手表")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(courses) { course ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(course.name, style = MaterialTheme.typography.titleMedium)
                            Text("教室 ${course.classroom}")
                            Text("周${course.dayOfWeek} ${course.startTime}-${course.endTime}")
                        }
                    }
                }
            }
        }
    }
}
