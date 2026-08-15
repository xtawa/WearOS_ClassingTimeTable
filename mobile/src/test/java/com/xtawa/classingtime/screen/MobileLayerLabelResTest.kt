package com.xtawa.classingtime.screen

import com.xtawa.classingtime.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileLayerLabelResTest {
    @Test
    fun labelRes_mapsMainTabsToTimetableHomeAndSettings() {
        assertEquals(R.string.layer_dashboard, MobileLayer.Schedule.labelRes())
        assertEquals(R.string.layer_home, MobileLayer.Dashboard.labelRes())
        assertEquals(R.string.layer_settings, MobileLayer.Settings.labelRes())
    }
}
