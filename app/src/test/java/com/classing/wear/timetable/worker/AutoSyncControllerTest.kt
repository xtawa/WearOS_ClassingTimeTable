package com.classing.wear.timetable.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSyncControllerTest {
    @Test
    fun setEnabled_true_callsEnable() {
        var enableCount = 0
        var disableCount = 0
        val controller = AutoSyncController(
            onEnable = { enableCount += 1 },
            onDisable = { disableCount += 1 },
        )

        controller.setEnabled(true)

        assertEquals(1, enableCount)
        assertEquals(0, disableCount)
    }

    @Test
    fun setEnabled_false_callsDisable() {
        var enableCount = 0
        var disableCount = 0
        val controller = AutoSyncController(
            onEnable = { enableCount += 1 },
            onDisable = { disableCount += 1 },
        )

        controller.setEnabled(false)

        assertEquals(0, enableCount)
        assertEquals(1, disableCount)
    }
}
