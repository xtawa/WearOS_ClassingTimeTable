package com.example.classtimetable.wear.sync

import com.example.classtimetable.shared.CourseCodec
import com.example.classtimetable.shared.DataLayerPaths
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = CourseRepository(this)
        dataEvents.forEach { event ->
            val item = event.dataItem
            if (item.uri.path == DataLayerPaths.COURSES_PATH) {
                val payload = com.google.android.gms.wearable.DataMapItem.fromDataItem(item)
                    .dataMap.getString(DataLayerPaths.COURSES_KEY)
                    ?: "[]"
                repository.save(CourseCodec.decode(payload))
            }
        }
    }
}
