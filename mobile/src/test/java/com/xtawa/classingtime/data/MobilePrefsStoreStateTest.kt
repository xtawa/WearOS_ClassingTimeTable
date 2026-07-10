package com.xtawa.classingtime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xtawa.classingtime.screen.DEFAULT_END_WEEK
import com.xtawa.classingtime.screen.DEFAULT_START_WEEK
import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.screen.LessonWeekParity
import com.xtawa.classingtime.screen.ScheduleExceptionKind
import com.xtawa.classingtime.screen.ScheduleExceptionUi
import com.xtawa.classingtime.screen.WeekNumberMode
import com.xtawa.classingtime.screen.capSnapshots
import com.xtawa.classingtime.screen.createScheduleSnapshot
import com.xtawa.classingtime.screen.toPersisted
import com.xtawa.classingtime.screen.toPersistedLesson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobilePrefsStoreStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun loadTimetableState_migratesLegacyLessonsJson() {
        val legacy = JSONArray().put(
            JSONObject()
                .put("id", "legacy-1")
                .put("title", "Legacy")
                .put("dayOfWeek", 1)
                .put("startMinute", 480)
                .put("endMinute", 570)
                .put("startWeek", 1)
                .put("endWeek", 8)
                .put("weekParity", "ALL"),
        )
        context.getSharedPreferences("mobile_timetable_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("lessons_json", legacy.toString())
            .commit()

        val state = MobilePrefsStore.loadTimetableState(context)

        assertEquals(1, state.baseLessons.size)
        assertEquals("Legacy", state.baseLessons.first().title)
        assertTrue(state.exceptions.isEmpty())
    }

    @Test
    fun saveTimetableState_roundTripsExceptions() {
        val lesson = sampleLesson()
        val exception = ScheduleExceptionUi(
            id = "cancel-1",
            lessonId = lesson.id,
            type = ScheduleExceptionKind.CANCEL,
            date = LocalDate.of(2026, 3, 9),
        )

        MobilePrefsStore.saveTimetableState(
            context = context,
            baseLessons = listOf(lesson.toPersistedLesson()),
            exceptions = listOf(exception.toPersisted()),
            snapshots = emptyList(),
        )

        val loaded = MobilePrefsStore.loadTimetableState(context)

        assertEquals(1, loaded.baseLessons.size)
        assertEquals(1, loaded.exceptions.size)
        assertEquals("CANCEL", loaded.exceptions.first().type)
    }

    @Test
    fun capSnapshots_keepsNewestFirst() {
        val snapshots = (1..10).map { index ->
            createScheduleSnapshot(
                reason = "snapshot_$index",
                weekNumberMode = WeekNumberMode.SEMESTER,
                semesterWeekStartDate = LocalDate.of(2026, 3, 2),
                baseLessons = listOf(sampleLesson()),
                exceptions = emptyList(),
                createdAt = index.toLong(),
            )
        }

        val capped = capSnapshots(snapshots, maxSize = 3)

        assertEquals(3, capped.size)
        assertEquals(10L, capped.first().createdAt)
        assertEquals(8L, capped.last().createdAt)
    }

    private fun sampleLesson(): LessonUi {
        return LessonUi(
            id = "sample",
            title = "Sample",
            teacher = null,
            location = "A101",
            note = null,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 30),
            startWeek = DEFAULT_START_WEEK,
            endWeek = DEFAULT_END_WEEK,
            weekParity = LessonWeekParity.ALL,
        )
    }
}
