package com.classing.wear.timetable.account

private val IDENTIFIER_PATTERN = Regex("^[A-Za-z0-9._@-]{3,80}$")

internal fun isValidLoginIdentifier(value: String): Boolean =
    IDENTIFIER_PATTERN.matches(value.trim())

internal fun isValidLoginPassword(value: String): Boolean = value.length >= 8
