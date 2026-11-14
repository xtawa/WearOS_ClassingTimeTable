package com.xtawa.classingtime.screen

import com.xtawa.classingtime.ui.timetable.*
import com.xtawa.classingtime.ui.theme.ScheduleDisplay
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DenseAndMetadataTest {
    private val date = LocalDate.of(2026, 10, 12)
    private val course = LessonUi("one", "Math", null, "A201", "", DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
    @Test fun weekCrossesMonthAndRespectsWeekStart() {
        val dates = denseWeekDates(LocalDate.of(2026,11,1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026,10,26),dates.first()); assertEquals(LocalDate.of(2026,11,1),dates.last())
        assertEquals(7,dates.size)
        assertEquals(DayOfWeek.SUNDAY, denseWeekDates(date,DayOfWeek.SUNDAY).first().dayOfWeek)
    }
    @Test fun overlappingCoursesStayVisibleAndSorted() {
        val other=course.copy(id="two",startTime=LocalTime.of(8,45))
        assertEquals(listOf(other,course),denseDayLessons(listOf(course,other)))
    }
    @Test fun unknownDisplaySettingUsesExistingFocusView() { assertEquals(ScheduleDisplay.Focus,ScheduleDisplay.fromRaw("old")) }
    @Test fun noteOnlyOverridePreservesSingleOccurrenceAndDoesNotCountAsMove() {
        val result=applyLessonEdit(listOf(course), emptyList(), LessonEditContext(course,date,setOf(LessonEditScope.SingleOccurrence)),
            course.copy(note="Bring calculator"),LessonEditScope.SingleOccurrence,WeekNumberMode.SEMESTER,date)
        assertEquals(course,result.baseLessons.single()); assertFalse(result.exceptions.single().isScheduleChange(result.baseLessons))
        val occurrence=buildEffectiveOccurrencesForDateRange(result.baseLessons,result.exceptions,date,date,WeekNumberMode.SEMESTER,date).single()
        assertEquals("Bring calculator",occurrence.lesson.note);assertEquals(EffectiveLessonOrigin.BASE,occurrence.origin)
        val following=buildEffectiveOccurrencesForDateRange(result.baseLessons,result.exceptions,date.plusWeeks(1),date.plusWeeks(1),WeekNumberMode.SEMESTER,date).single()
        assertEquals("",following.lesson.note)
    }
    @Test fun noOpDoesNotCreateExceptionButTimeEditStillDoes() {
        val ctx=LessonEditContext(course,date,setOf(LessonEditScope.SingleOccurrence))
        assertTrue(applyLessonEdit(listOf(course),emptyList(),ctx,course,LessonEditScope.SingleOccurrence,WeekNumberMode.SEMESTER,date).exceptions.isEmpty())
        val moved=applyLessonEdit(listOf(course),emptyList(),ctx,course.copy(startTime=LocalTime.of(9,30)),LessonEditScope.SingleOccurrence,WeekNumberMode.SEMESTER,date)
        assertTrue(moved.exceptions.single().isScheduleChange(moved.baseLessons))
    }
    @Test fun onlyServerBasedImportNeedsClassingLogin() {
        assertTrue(OnboardingImportTarget.AI_DOCUMENT.requiresClassingLogin());assertTrue(OnboardingImportTarget.AI_TEXT.requiresClassingLogin())
        for(target in listOf(OnboardingImportTarget.ICS,OnboardingImportTarget.JSON,OnboardingImportTarget.CANVAS,OnboardingImportTarget.MANUAL_ENTRY)) assertFalse(target.requiresClassingLogin())
    }
}
