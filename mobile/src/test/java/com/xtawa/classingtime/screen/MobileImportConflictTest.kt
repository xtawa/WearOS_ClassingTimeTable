package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class MobileImportConflictTest {
    @Test
    fun detectImportConflicts_includesExistingAndBatchConflicts() {
        val existing = lesson("existing", 9, 0, 10, 0)
        val first = lesson("first", 9, 30, 10, 30)
        val second = lesson("second", 10, 0, 11, 0)

        val conflicts = detectImportConflicts(listOf(first, second), listOf(existing))

        assertEquals(2, conflicts.size)
        assertEquals(
            setOf(
                setOf("first", "second"),
                setOf("first", "existing"),
            ),
            conflicts.map { setOf(it.first.id, it.second.id) }.toSet(),
        )
    }

    private fun lesson(id: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) = LessonUi(
        id = id,
        title = id,
        teacher = null,
        location = null,
        note = null,
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = LocalTime.of(startHour, startMinute),
        endTime = LocalTime.of(endHour, endMinute),
        startWeek = 1,
        endWeek = 20,
        weekParity = LessonWeekParity.ALL,
    )
}
