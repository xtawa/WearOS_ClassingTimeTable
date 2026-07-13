package com.xtawa.classingtime.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountInputValidationTest {
    @Test fun usernameMatchesPublishedContract() {
        assertTrue(isValidUsername("abc"))
        assertTrue(isValidUsername("user.name-_01"))
        assertTrue(isValidUsername("a".repeat(40)))
        assertFalse(isValidUsername("ab"))
        assertFalse(isValidUsername("a".repeat(41)))
        assertFalse(isValidUsername("user name"))
        assertFalse(isValidUsername("用户"))
    }

    @Test fun emailRequiresLocalDomainAndSuffix() {
        assertTrue(isValidEmail(" user@example.com "))
        assertFalse(isValidEmail("user"))
        assertFalse(isValidEmail("user@localhost"))
        assertFalse(isValidEmail("@example.com"))
    }

    @Test fun passwordVerificationAndRedeemInputsAreGuarded() {
        assertTrue(isValidPassword("12345678"))
        assertFalse(isValidPassword("1234567"))
        assertTrue(isValidVerificationCode("012345"))
        assertFalse(isValidVerificationCode("12345x"))
        assertTrue(isValidRedeemCode("ABCD-1234"))
        assertFalse(isValidRedeemCode(""))
        assertFalse(isValidRedeemCode("a b c d"))
    }
}
