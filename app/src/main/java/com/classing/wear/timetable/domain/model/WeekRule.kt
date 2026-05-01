package com.classing.wear.timetable.domain.model

data class WeekRule(
    val startWeek: Int,
    val endWeek: Int,
    val parity: WeekParity = WeekParity.ALL,
) {
    init {
        require(startWeek in 1..53) { "startWeek must be in 1..53, was $startWeek" }
        require(endWeek in startWeek..53) { "endWeek must be in startWeek..53, was $endWeek" }
    }

    fun contains(week: Int): Boolean {
        if (week !in startWeek..endWeek) return false
        return when (parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
    }
}
