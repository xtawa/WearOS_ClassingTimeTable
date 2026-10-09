package com.xtawa.classingtime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTimetableStorageTest {
    private lateinit var context: Context
    private val lesson = PersistedLesson("math", "数学", "李老师", "A201", "Saved offline", 1, 540, 600, 1, 20, "ALL")
    private val cancellation = PersistedScheduleException(
        id = "cancel", lessonId = lesson.id, type = "CANCEL", date = "2026-10-12", note = "Holiday",
        title = null, teacher = null, location = null,
        dayOfWeek = null, startMinute = null, endMinute = null,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun savedTimetableAndExceptionsReloadWithoutAccountOrNetwork() {
        val snapshot = PersistedScheduleSnapshot("before-edit", 1L, "import", "SEMESTER", "2026-09-07", "MONDAY", listOf(lesson), listOf(cancellation))
        MobilePrefsStore.saveTimetableState(context, listOf(lesson), listOf(cancellation), listOf(snapshot))
        // Flush pending writes, then read through a fresh context without session or network dependencies.
        assertTrue(context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE).edit().commit())
        val reopenedContext = context.createPackageContext(context.packageName, 0)
        val restored = MobilePrefsStore.loadTimetableState(reopenedContext)
        assertEquals(listOf(lesson), restored.baseLessons)
        assertEquals(listOf(cancellation), restored.exceptions)
        assertEquals(listOf(snapshot), restored.snapshots)
        val storedKeys = reopenedContext.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE).all.keys
        assertFalse(storedKeys.any { it.contains("token") || it.contains("account") })
    }

    @Test
    fun anOlderOfflineInstallMigratesItsSavedCoursesLocally() {
        MobilePrefsStore.saveTimetableState(context, listOf(lesson), emptyList(), emptyList())
        val preferences = context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().remove("base_lessons_json").commit())
        val reopenedContext = context.createPackageContext(context.packageName, 0)
        assertEquals(listOf(lesson), MobilePrefsStore.loadTimetableState(reopenedContext).baseLessons)
        assertTrue(preferences.contains("base_lessons_json"))
        assertEquals(listOf(lesson), MobilePrefsStore.loadTimetableState(context).baseLessons)
    }
}
