package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAiVoiceRmsTest {
    @Test
    fun silenceStaysQuiet() {
        assertEquals(0f, normalizedVoiceRms(shortArrayOf(0, 0, 0), 3), .0001f)
        assertEquals(0f, normalizedVoiceRms(shortArrayOf(1000), 0), .0001f)
    }

    @Test
    fun amplitudeRespondsToActualSamples() {
        val quiet = normalizedVoiceRms(shortArrayOf(100, -100, 100, -100), 4)
        val loud = normalizedVoiceRms(shortArrayOf(4000, -4000, 4000, -4000), 4)
        assertTrue("Loud speech should display more energy than quiet speech", loud > quiet)
        assertTrue(quiet >= 0f)
    }

    @Test
    fun saturatesAndClampsSampleCount() {
        val fullVolume = normalizedVoiceRms(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), 20)
        assertEquals(1f, fullVolume, .0001f)
        assertEquals(0f, normalizedVoiceRms(shortArrayOf(2000), -3), .0001f)
    }
}
