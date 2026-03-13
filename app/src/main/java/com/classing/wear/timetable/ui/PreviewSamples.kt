package com.classing.wear.timetable.ui

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekParity
import com.classing.wear.timetable.domain.model.WeekRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object PreviewSamples {
    fun sampleLesson(): LessonOccurrence {
        val date = LocalDate.of(2026, 3, 13)
        val slot = TimeSlot(
            localId = 1,
            remoteId = "slot_3_4",
            semesterId = 1,
            indexInDay = 2,
            label = "3-4 节",
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 35),
            version = 1,
        )
        val course = Course(
            localId = 1,
            remoteId = "course_math",
            semesterId = 1,
            name = "高等数学 II",
            teacher = "李老师",
            classroom = "A201",
            note = "随堂测",
            colorLabel = "red",
            isFavorite = false,
            version = 1,
        )
        val session = CourseSession(
            localId = 1,
            remoteId = "session_math",
            semesterId = 1,
            courseId = 1,
            dayOfWeek = DayOfWeek.FRIDAY,
            timeSlotId = 1,
            weekRule = WeekRule(1, 19, WeekParity.ALL),
            version = 1,
        )

        return LessonOccurrence(
            course = course,
            session = session,
            timeSlot = slot,
            date = date,
            weekIndex = 3,
            status = LessonStatus.NOT_STARTED,
            startAt = LocalDateTime.of(date, slot.startTime),
            endAt = LocalDateTime.of(date, slot.endTime),
        )
    }
}
