package com.example.classtimetable.wear.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import com.example.classtimetable.wear.sync.CourseRepository

class ScheduleAppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val next = CourseRepository(context).load().firstOrNull()
        provideContent {
            Content(next?.name ?: "暂无课程", next?.startTime ?: "--:--")
        }
    }

    @Composable
    private fun Content(title: String, time: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1D1B20)))
                .padding(12.dp)
        ) {
            Text("课程小组件", style = androidx.glance.text.TextStyle(color = ColorProvider(Color.White)))
            Text(title, style = androidx.glance.text.TextStyle(color = ColorProvider(Color(0xFFE8DEF8))))
            Text(time, style = androidx.glance.text.TextStyle(color = ColorProvider(Color(0xFFCAC4D0))))
        }
    }
}

class ScheduleAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleAppWidget()
}
