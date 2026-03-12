package com.example.classtimetable.wear.sync

import android.content.Context
import com.example.classtimetable.shared.Course
import com.example.classtimetable.shared.CourseCodec

class CourseRepository(context: Context) {
    private val prefs = context.getSharedPreferences("courses", Context.MODE_PRIVATE)

    fun save(courses: List<Course>) {
        prefs.edit().putString("payload", CourseCodec.encode(courses)).apply()
    }

    fun load(): List<Course> {
        val payload = prefs.getString("payload", "[]") ?: "[]"
        return CourseCodec.decode(payload)
    }
}
