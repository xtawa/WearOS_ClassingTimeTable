package com.example.classtimetable.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Course(
    val name: String,
    val classroom: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

object CourseCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(courses: List<Course>): String = json.encodeToString(courses)

    fun decode(payload: String): List<Course> = runCatching {
        json.decodeFromString<List<Course>>(payload)
    }.getOrDefault(emptyList())
}

object DataLayerPaths {
    const val COURSES_PATH = "/courses/sync"
    const val COURSES_KEY = "courses_json"
}
