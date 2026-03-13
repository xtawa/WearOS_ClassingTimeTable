package com.classing.mobile.timetable.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ImportScreen(onPreview: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("导入页面（支持 ICS）")
        Button(onClick = onPreview) { Text("解析并预览") }
    }
}

@Composable
fun ImportPreviewScreen(onConfirm: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("导入预览/确认")
        Button(onClick = onConfirm) { Text("确认写入") }
    }
}

@Composable
fun CourseEditorScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("课程编辑页") }
}

@Composable
fun SemesterManagementScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("学期管理页") }
}

@Composable
fun ReminderSettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("提醒设置页") }
}

@Composable
fun SyncStatusScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("同步状态页") }
}
