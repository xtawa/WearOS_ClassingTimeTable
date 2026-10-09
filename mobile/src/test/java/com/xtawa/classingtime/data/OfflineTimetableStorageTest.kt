package com.xtawa.classingtime.data

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class OfflineTimetableStorageTest {
    private val lesson = PersistedLesson("math", "数学", "李老师", "A201", "Saved offline", 1, 540, 600, 1, 20, "ALL")
    private val cancellation = PersistedScheduleException(
        id = "cancel", lessonId = lesson.id, type = "CANCEL", date = "2026-10-12", note = "Holiday",
        dayOfWeek = null, startMinute = null, endMinute = null,
    )

    @Test fun savedTimetableAndExceptionsSurviveColdStartWithoutAccountOrNetwork() {
        val disk = MemoryPreferences()
        val snapshot = PersistedScheduleSnapshot("before-edit", 1L, "import", "SEMESTER", "2026-09-07", "MONDAY", listOf(lesson), listOf(cancellation))
        MobilePrefsStore.saveTimetableState(disk, listOf(lesson), listOf(cancellation), listOf(snapshot))
        // A fresh store receives only persisted data, no session, token, membership or network client.
        val restarted = MemoryPreferences(disk.values.toMutableMap())
        val restored = MobilePrefsStore.loadTimetableState(restarted)
        assertEquals(listOf(lesson), restored.baseLessons)
        assertEquals(listOf(cancellation), restored.exceptions)
        assertEquals(listOf(snapshot), restored.snapshots)
        assertFalse(restarted.values.keys.any { it.contains("token") || it.contains("account") })
    }

    @Test fun anOlderOfflineInstallMigratesItsSavedCoursesLocally() {
        val disk = MemoryPreferences()
        MobilePrefsStore.saveTimetableState(disk, listOf(lesson), emptyList(), emptyList())
        disk.values.remove("base_lessons_json")
        val restarted = MemoryPreferences(disk.values.toMutableMap())
        assertEquals(listOf(lesson), MobilePrefsStore.loadTimetableState(restarted).baseLessons)
        assertTrue(restarted.contains("base_lessons_json"))
        assertEquals(listOf(lesson), MobilePrefsStore.loadTimetableState(MemoryPreferences(restarted.values.toMutableMap())).baseLessons)
    }

    private class MemoryPreferences(val values: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {
        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit() = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putString(key: String?, value: String?) = apply { pending[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { pending[key!!] = values }
            override fun putInt(key: String?, value: Int) = apply { pending[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[key!!] = value }
            override fun remove(key: String?) = apply { pending[key!!] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }
}
