package com.classing.wear.timetable.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLoginInputValidationTest {

    @Test
    fun isValidLoginIdentifier_acceptsValidValues() {
        assertTrue(isValidLoginIdentifier("alice"))
        assertTrue(isValidLoginIdentifier("user@mail.com"))
        assertTrue(isValidLoginIdentifier("a.b_c-3"))
    }

    @Test
    fun isValidLoginIdentifier_rejectsEmptyTooShortSpacesAndIllegalChars() {
        assertFalse(isValidLoginIdentifier(""))
        assertFalse(isValidLoginIdentifier("ab"))
        assertFalse(isValidLoginIdentifier("ab cd"))
        assertFalse(isValidLoginIdentifier("ab!"))
    }

    @Test
    fun isValidLoginPassword_acceptsLengthAtLeastEight() {
        assertTrue(isValidLoginPassword("12345678"))
        assertTrue(isValidLoginPassword("password123"))
    }

    @Test
    fun isValidLoginPassword_rejectsLengthBelowEight() {
        assertFalse(isValidLoginPassword("1234567"))
        assertFalse(isValidLoginPassword(""))
    }
}
