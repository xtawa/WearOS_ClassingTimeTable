package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MobileScheduleStateTest {

    @Test
    fun datedMeetingsAreNotMergedAndConflictsUseTheirActualWeekday() {
        val rule = com.classing.shared.schedule.ScheduleRule(
            com.classing.shared.schedule.RepeatKind.DATES, semesterStart, semesterStart.plusDays(30),
            dates = setOf(semesterStart.plusDays(1)))
        val first = baseLesson.copy(scheduleRuleJson = com.classing.client.schedule.ScheduleRuleJson.encode(rule))
        val nextWeek = first.copy(id = "next-date", scheduleRuleJson = com.classing.client.schedule.ScheduleRuleJson.encode(
            rule.copy(dates = setOf(semesterStart.plusDays(8)))))
        val imported = appendImportedLessons(listOf(first), emptyList(), listOf(nextWeek, first.copy(id = "duplicate")))
        assertEquals(2, imported.baseLessons.size)
        assertEquals(1, imported.skippedDuplicateCount)
        val tuesday = baseLesson.copy(id = "tuesday", dayOfWeek = DayOfWeek.TUESDAY)
        assertEquals(1, detectLessonConflicts(listOf(first, tuesday), WeekNumberMode.SEMESTER, semesterStart).size)
        assertTrue(detectLessonConflicts(listOf(first, nextWeek), WeekNumberMode.SEMESTER, semesterStart).isEmpty())
        val a = rule.copy(kind = com.classing.shared.schedule.RepeatKind.ROTATION, dates = emptySet(), cycleLength = 2, cycleDays = setOf(1))
        val b = a.copy(cycleDays = setOf(2))
        assertTrue(detectLessonConflicts(listOf(
            first.copy(scheduleRuleJson = com.classing.client.schedule.ScheduleRuleJson.encode(a)),
            nextWeek.copy(scheduleRuleJson = com.classing.client.schedule.ScheduleRuleJson.encode(b)))).isEmpty())
    }

    @Test
    fun rotationSplitPreservesPointerAndConvertingToWeeklyDoesNotRewritePast() {
        val rule = com.classing.shared.schedule.ScheduleRule(
            kind = com.classing.shared.schedule.RepeatKind.ROTATION,
            anchorDate = LocalDate.of(2026, 3, 2), endDate = LocalDate.of(2026, 4, 24),
            cycleLength = 6, cycleDays = setOf(1, 4))
        val original = baseLesson.copy(scheduleRuleJson = com.classing.client.schedule.ScheduleRuleJson.encode(rule))
        val splitDate = LocalDate.of(2026, 3, 23)
        val clipped = clipMeetingDates(original, splitDate, null)!!
        val preserved = com.classing.client.schedule.ScheduleRuleJson.decode(clipped.scheduleRuleJson)!!
        for (day in 0L..25L) {
            val date = splitDate.plusDays(day)
            assertEquals(rule.matches(date, original.dayOfWeek), preserved.matches(date, original.dayOfWeek))
        }
        val result = applyLessonEdit(listOf(original), emptyList(),
            LessonEditContext(original, anchorDate = splitDate, allowedScopes = setOf(LessonEditScope.FromThisWeek)),
            original.copy(title = "Weekly Math", scheduleRuleJson = null), LessonEditScope.FromThisWeek,
            WeekNumberMode.SEMESTER, semesterStart)
        assertEquals(4, result.baseLessons.single { it.title == "Weekly Math" }.startWeek)
        val before = buildEffectiveOccurrencesForDateRange(result.baseLessons, result.exceptions,
            semesterStart, splitDate.minusDays(1), WeekNumberMode.SEMESTER, semesterStart)
        assertTrue(before.none { it.lesson.title == "Weekly Math" })
    }

    private val semesterStart = LocalDate.of(2026, 3, 2)
    private val baseLesson = LessonUi(
        id = "math-base",
        title = "Math",
        teacher = "Alice",
        location = "A101",
        note = null,
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 30),
        startWeek = 1,
        endWeek = 8,
        weekParity = LessonWeekParity.ALL,
    )

    @Test
    fun removeLesson_singleOccurrence_createsCancelException() {
        val result = removeLesson(
            baseLessons = listOf(baseLesson),
            exceptions = emptyList(),
            editContext = LessonEditContext(
                lesson = baseLesson,
                anchorDate = LocalDate.of(2026, 3, 16),
                allowedScopes = setOf(LessonEditScope.SingleOccurrence),
            ),
            scope = LessonEditScope.SingleOccurrence,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertEquals(1, result.baseLessons.size)
        assertEquals(1, result.exceptions.size)
        assertEquals(ScheduleExceptionKind.CANCEL, result.exceptions.first().type)
    }

    @Test
    fun applyLessonEdit_fromThisWeek_splitsBaseLesson() {
        val updated = baseLesson.copy(
            title = "Advanced Math",
            location = "B202",
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 30),
        )

        val result = applyLessonEdit(
            baseLessons = listOf(baseLesson),
            exceptions = emptyList(),
            editContext = LessonEditContext(
                lesson = baseLesson,
                anchorDate = LocalDate.of(2026, 3, 23),
                allowedScopes = setOf(LessonEditScope.FromThisWeek),
            ),
            updatedLesson = updated,
            scope = LessonEditScope.FromThisWeek,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertEquals(2, result.baseLessons.size)
        assertEquals(3, result.baseLessons.first { it.id == "math-base" }.endWeek)
        val split = result.baseLessons.first { it.id != "math-base" }
        assertEquals(4, split.startWeek)
        assertEquals("Advanced Math", split.title)
        assertEquals(LocalTime.of(10, 0), split.startTime)
    }

    @Test
    fun buildEffectiveOccurrences_appliesCancelRescheduleAndMakeup() {
        val exceptions = listOf(
            ScheduleExceptionUi(
                id = "cancel-1",
                lessonId = "math-base",
                type = ScheduleExceptionKind.CANCEL,
                date = LocalDate.of(2026, 3, 9),
            ),
            ScheduleExceptionUi(
                id = "reschedule-1",
                lessonId = "math-base",
                type = ScheduleExceptionKind.RESCHEDULE,
                date = LocalDate.of(2026, 3, 16),
                title = "Math",
                teacher = "Alice",
                location = "Lab",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(14, 30),
            ),
            ScheduleExceptionUi(
                id = "makeup-1",
                lessonId = null,
                type = ScheduleExceptionKind.MAKE_UP,
                date = LocalDate.of(2026, 3, 18),
                title = "Math Makeup",
                teacher = "Alice",
                location = "C303",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                startTime = LocalTime.of(15, 0),
                endTime = LocalTime.of(16, 0),
            ),
        )

        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = listOf(baseLesson),
            exceptions = exceptions,
            startDate = LocalDate.of(2026, 3, 9),
            endDate = LocalDate.of(2026, 3, 22),
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertTrue(occurrences.none { it.date == LocalDate.of(2026, 3, 9) && it.sourceLessonId == "math-base" })
        val rescheduled = occurrences.first { it.date == LocalDate.of(2026, 3, 16) }
        assertEquals(LocalTime.of(13, 0), rescheduled.lesson.startTime)
        assertEquals(EffectiveLessonOrigin.RESCHEDULED, rescheduled.origin)
        val makeup = occurrences.first { it.date == LocalDate.of(2026, 3, 18) }
        assertEquals("Math Makeup", makeup.lesson.title)
        assertEquals(EffectiveLessonOrigin.MAKE_UP, makeup.origin)
    }

    @Test
    fun resolveNextLessonForBoard_usesDateSpecificLessonsAcrossWeekBoundary() {
        val currentWeekMonday = baseLesson
        val nextWeekMonday = baseLesson.copy(
            id = "math-next-week",
            title = "Advanced Math",
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 30),
        )

        val nextLesson = resolveNextLessonForBoard(
            lessonsForDate = { date ->
                when (date) {
                    LocalDate.of(2026, 3, 2) -> listOf(currentWeekMonday)
                    LocalDate.of(2026, 3, 9) -> listOf(nextWeekMonday)
                    else -> emptyList()
                }
            },
            now = LocalDateTime.of(2026, 3, 6, 18, 0),
        )

        assertEquals("Advanced Math", nextLesson?.lesson?.title)
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 0), nextLesson?.startAt)
    }

    @Test
    fun createScheduleSnapshot_preservesAllWeekSettings() {
        val snapshot = createScheduleSnapshot(
            reason = "test",
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
            weekStartDay = DayOfWeek.SUNDAY,
            baseLessons = listOf(baseLesson),
            exceptions = emptyList(),
            createdAt = 1L,
        )

        assertEquals(WeekNumberMode.SEMESTER, snapshot.weekNumberMode)
        assertEquals(semesterStart, snapshot.semesterWeekStartDate)
        assertEquals(DayOfWeek.SUNDAY, snapshot.weekStartDay)
    }

    @Test
    fun weekIndexForMode_respectsConfiguredWeekStart() {
        val semesterStartOnWednesday = LocalDate.of(2026, 3, 4)

        assertEquals(2, weekIndexForMode(LocalDate.of(2026, 3, 8), WeekNumberMode.SEMESTER, semesterStartOnWednesday, DayOfWeek.SUNDAY))
        assertEquals(1, weekIndexForMode(LocalDate.of(2026, 3, 8), WeekNumberMode.SEMESTER, semesterStartOnWednesday, DayOfWeek.MONDAY))
        assertEquals(2, weekIndexForMode(LocalDate.of(2026, 3, 9), WeekNumberMode.SEMESTER, semesterStartOnWednesday, DayOfWeek.MONDAY))
    }

    @Test
    fun migrateLegacyDefaultWeekRange_keepsNaturalWeekCoursesVisibleAfterWeekThirty() {
        val legacyCourse = baseLesson.copy(
            startWeek = DEFAULT_START_WEEK,
            endWeek = LEGACY_DEFAULT_END_WEEK,
        )

        val migrated = migrateLegacyDefaultWeekRange(
            baseLessons = listOf(legacyCourse),
            weekNumberMode = WeekNumberMode.NATURAL,
        )

        assertEquals(DEFAULT_END_WEEK, migrated.single().endWeek)
        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = migrated,
            exceptions = emptyList(),
            startDate = LocalDate.of(2026, 8, 10),
            endDate = LocalDate.of(2026, 8, 10),
            weekNumberMode = WeekNumberMode.NATURAL,
            semesterWeekStartDate = semesterStart,
        )
        assertEquals(listOf("Math"), occurrences.map { it.lesson.title })
    }

    @Test
    fun migrateLegacyDefaultWeekRange_doesNotChangeSemesterOrCustomRanges() {
        val legacyCourse = baseLesson.copy(
            startWeek = DEFAULT_START_WEEK,
            endWeek = LEGACY_DEFAULT_END_WEEK,
        )
        val customCourse = baseLesson.copy(startWeek = 2, endWeek = 12)

        assertEquals(
            legacyCourse,
            migrateLegacyDefaultWeekRange(listOf(legacyCourse), WeekNumberMode.SEMESTER).single(),
        )
        assertEquals(
            customCourse,
            migrateLegacyDefaultWeekRange(listOf(customCourse), WeekNumberMode.NATURAL).single(),
        )
    }

    @Test
    fun restoreOriginalOccurrence_removesOnlyMatchingDateAndLesson() {
        val targetDate = LocalDate.of(2026, 3, 9)
        val exceptions = listOf(
            ScheduleExceptionUi("one", "math-base", ScheduleExceptionKind.CANCEL, targetDate),
            ScheduleExceptionUi("two", "math-base", ScheduleExceptionKind.CANCEL, targetDate.plusWeeks(1)),
            ScheduleExceptionUi("three", "other", ScheduleExceptionKind.CANCEL, targetDate),
        )

        val restored = restoreOriginalOccurrence(exceptions, "math-base", targetDate)

        assertEquals(listOf("two", "three"), restored.map { it.id })
    }
}
