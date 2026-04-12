package com.classing.wear.timetable.core.time

import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.domain.model.LessonStatus
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattersTest {
    @Test
    fun formatCountdown_returns_in_progress_when_status_is_in_progress() {
        val text = TimeFormatters.formatCountdown(Duration.ZERO, LessonStatus.IN_PROGRESS)
        assertEquals(WearI18n.countdownInProgress(), text)
    }
}
