package com.example.classtimetable.mobile

import android.content.Context
import com.example.classtimetable.shared.Course
import com.example.classtimetable.shared.CourseCodec
import com.example.classtimetable.shared.DataLayerPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearSyncClient(context: Context) {
    private val dataClient = Wearable.getDataClient(context)

    fun pushCourses(courses: List<Course>) {
        val request = PutDataMapRequest.create(DataLayerPaths.COURSES_PATH).apply {
            dataMap.putString(DataLayerPaths.COURSES_KEY, CourseCodec.encode(courses))
            dataMap.putLong("updated_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
    }
}
