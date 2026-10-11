package com.classing.client.schedule

import com.classing.shared.schedule.RepeatKind
import com.classing.shared.schedule.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object ScheduleRuleJson {
    fun decode(raw: String?): ScheduleRule? {
        if (raw.isNullOrBlank()) return null
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "Unsupported schedule rule version" }
        fun ints(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }.orEmpty()
        fun dates(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { LocalDate.parse(a.getString(it)) }.toSet() }.orEmpty()
        return ScheduleRule(
            kind = RepeatKind.valueOf(json.getString("kind")),
            anchorDate = LocalDate.parse(json.getString("anchorDate")), endDate = LocalDate.parse(json.getString("endDate")),
            interval = json.optInt("interval", 1), daysOfWeek = ints("daysOfWeek").map(DayOfWeek::of).toSet(),
            weekStartDay = DayOfWeek.of(json.optInt("weekStartDay", 1)),
            cycleLength = json.optInt("cycleLength", 2), cycleDays = ints("cycleDays"),
            schoolDays = if (json.has("schoolDays")) ints("schoolDays").map(DayOfWeek::of).toSet() else (1..5).map(DayOfWeek::of).toSet(),
            holidays = dates("holidays"), advanceOnHolidays = json.optBoolean("advanceOnHolidays"), dates = dates("dates"),
            courseGroupId = json.optString("courseGroupId").takeIf { it.isNotBlank() },
            meetingType = json.optString("meetingType").takeIf { it.isNotBlank() }, termName = json.optString("termName").takeIf { it.isNotBlank() },
        )
    }

    fun encode(rule: ScheduleRule): String = JSONObject().put("version", 1).put("kind", rule.kind.name)
        .put("anchorDate", rule.anchorDate).put("endDate", rule.endDate).put("interval", rule.interval)
        .put("daysOfWeek", JSONArray(rule.daysOfWeek.map { it.value }.sorted())).put("weekStartDay", rule.weekStartDay.value)
        .put("cycleLength", rule.cycleLength).put("cycleDays", JSONArray(rule.cycleDays.sorted()))
        .put("schoolDays", JSONArray(rule.schoolDays.map { it.value }.sorted())).put("holidays", JSONArray(rule.holidays.sorted().map { it.toString() }))
        .put("advanceOnHolidays", rule.advanceOnHolidays).put("dates", JSONArray(rule.dates.sorted().map { it.toString() }))
        .put("courseGroupId", rule.courseGroupId).put("meetingType", rule.meetingType).put("termName", rule.termName).toString()
}
