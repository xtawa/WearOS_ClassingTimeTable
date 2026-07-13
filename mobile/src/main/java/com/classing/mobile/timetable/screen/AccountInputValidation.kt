package com.xtawa.classingtime.screen

private val USERNAME_PATTERN = Regex("^[A-Za-z0-9._-]{3,40}$")
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val REDEEM_CODE_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{3,63}$")

internal fun isValidUsername(value: String): Boolean = USERNAME_PATTERN.matches(value.trim())

internal fun isValidEmail(value: String): Boolean = EMAIL_PATTERN.matches(value.trim())

internal fun isValidPassword(value: String): Boolean = value.length >= 8

internal fun isValidVerificationCode(value: String): Boolean = value.length == 6 && value.all(Char::isDigit)

internal fun isValidRedeemCode(value: String): Boolean = REDEEM_CODE_PATTERN.matches(value.trim())
