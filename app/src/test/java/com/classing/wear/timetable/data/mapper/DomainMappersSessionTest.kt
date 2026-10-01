package com.classing.wear.timetable.data.mapper

import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class DomainMappersSessionTest {
    private fun entity(startWeek: Int, endWeek: Int, parity: String = "ALL", dayOfWeek: Int = 1) = CourseSessionEntity(
        localId = 1,
        remoteId = "s",
        semesterId = 1,
        courseId = 1,
        dayOfWeek = dayOfWeek,
        timeSlotId = 1,
        startWeek = startWeek,
        endWeek = endWeek,
        weekParity = parity,
        version = 1,
    )

    @Test
    fun asDomainOrNull_mapsValidRow() {
        val session = entity(startWeek = 1, endWeek = 16).asDomainOrNull()
        assertNotNull(session)
        assertEquals(DayOfWeek.MONDAY, session!!.dayOfWeek)
        assertEquals(1..16, session.weekRule.startWeek..session.weekRule.endWeek)
    }

    @Test
    fun asDomainOrNull_skipsRowsWithInvalidWeekRuleInsteadOfThrowing() {
        assertNull(entity(startWeek = 0, endWeek = 16).asDomainOrNull())
        assertNull(entity(startWeek = 10, endWeek = 5).asDomainOrNull())
        assertNull(entity(startWeek = 1, endWeek = 99).asDomainOrNull())
        assertNull(entity(startWeek = 1, endWeek = 16, parity = "WEIRD").asDomainOrNull())
        assertNull(entity(startWeek = 1, endWeek = 16, dayOfWeek = 8).asDomainOrNull())
    }
}
