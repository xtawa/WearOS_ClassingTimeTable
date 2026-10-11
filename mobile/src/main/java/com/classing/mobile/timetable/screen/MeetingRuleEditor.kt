package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.classing.client.schedule.ScheduleRuleJson
import com.classing.shared.schedule.*
import com.xtawa.classingtime.R
import java.time.DayOfWeek
import java.time.LocalDate

internal class MeetingRuleEditorState(lesson: LessonUi) {
    private val initial = ScheduleRuleJson.decode(lesson.scheduleRuleJson)
    var enabled by mutableStateOf(initial != null)
    var kind by mutableStateOf(initial?.kind ?: RepeatKind.WEEKLY)
    var anchor by mutableStateOf((initial?.anchorDate ?: LocalDate.now()).toString())
    var end by mutableStateOf((initial?.endDate ?: LocalDate.now().plusMonths(4)).toString())
    var interval by mutableStateOf((initial?.interval ?: 1).toString())
    var length by mutableStateOf((initial?.cycleLength ?: 2).toString())
    var cycleDays by mutableStateOf(initial?.cycleDays?.joinToString(",") ?: "1")
    var weekDays by mutableStateOf(initial?.daysOfWeek?.ifEmpty { setOf(lesson.dayOfWeek) } ?: setOf(lesson.dayOfWeek))
    var schoolDays by mutableStateOf(initial?.schoolDays ?: (1..5).map(DayOfWeek::of).toSet())
    var holidayDates by mutableStateOf(initial?.holidays?.sorted()?.joinToString(",") ?: "")
    var meetingDates by mutableStateOf(initial?.dates?.sorted()?.joinToString(",") ?: "")
    var advance by mutableStateOf(initial?.advanceOnHolidays ?: false)
    var type by mutableStateOf(initial?.meetingType ?: "LECTURE")
    var term by mutableStateOf(initial?.termName ?: "")
    var group by mutableStateOf(initial?.courseGroupId ?: "")
    private fun dates(raw: String) = raw.split(',').map(String::trim).filter(String::isNotBlank).map(LocalDate::parse).toSet()
    fun build(): String? {
        if (!enabled) return null
        val rule = ScheduleRule(kind, LocalDate.parse(anchor), LocalDate.parse(end),
            interval = if (kind == RepeatKind.WEEKLY) interval.toInt() else 1,
            daysOfWeek = weekDays, weekStartDay = initial?.weekStartDay ?: DayOfWeek.MONDAY,
            cycleLength = if (kind == RepeatKind.ROTATION) length.toInt() else 2,
            cycleDays = if (kind == RepeatKind.ROTATION) cycleDays.split(',').map { it.trim().toInt() }.toSet() else emptySet(),
            schoolDays = schoolDays, holidays = dates(holidayDates), advanceOnHolidays = advance,
            dates = if (kind == RepeatKind.DATES) dates(meetingDates) else emptySet(),
            meetingType = type, termName = term.trim().ifBlank { null }, courseGroupId = group.trim().ifBlank { null })
        require(kind != RepeatKind.DATES || rule.dates.isNotEmpty())
        require(kind != RepeatKind.WEEKLY || weekDays.isNotEmpty())
        return ScheduleRuleJson.encode(rule)
    }
}

@Composable internal fun MeetingRuleEditor(state: MeetingRuleEditorState) {
    Row { Text(stringResource(R.string.rule_advanced), Modifier.weight(1f)); Switch(state.enabled, { state.enabled = it }) }
    if (!state.enabled) return
    Text(stringResource(R.string.rule_legacy_ignored), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatKind.entries.forEach { kind -> FilterChip(state.kind == kind, { state.kind = kind }, label = { Text(stringResource(when(kind) { RepeatKind.WEEKLY -> R.string.rule_weekly; RepeatKind.ROTATION -> R.string.rule_rotation; RepeatKind.DATES -> R.string.rule_dates })) }) }
    }
    OutlinedTextField(state.anchor, { state.anchor = it }, label = { Text(stringResource(R.string.rule_anchor)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(state.end, { state.end = it }, label = { Text(stringResource(R.string.rule_end)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    if (state.kind == RepeatKind.WEEKLY) {
        OutlinedTextField(state.interval, { state.interval = it }, label = { Text(stringResource(R.string.rule_interval)) }, singleLine = true)
        RuleWeekdays(state.weekDays) { state.weekDays = it }
    }
    if (state.kind == RepeatKind.ROTATION) {
        OutlinedTextField(state.length, { state.length = it }, label = { Text(stringResource(R.string.rule_cycle_length)) }, singleLine = true)
        OutlinedTextField(state.cycleDays, { state.cycleDays = it }, label = { Text(stringResource(R.string.rule_cycle_days)) }, singleLine = true)
        Text(stringResource(R.string.rule_school_days)); RuleWeekdays(state.schoolDays) { state.schoolDays = it }
        Row { Text(stringResource(R.string.rule_holiday_advance), Modifier.weight(1f)); Switch(state.advance, { state.advance = it }) }
    }
    if (state.kind == RepeatKind.DATES) OutlinedTextField(state.meetingDates, { state.meetingDates = it }, label = { Text(stringResource(R.string.rule_meeting_dates)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.holidayDates, { state.holidayDates = it }, label = { Text(stringResource(R.string.rule_holidays)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.term, { state.term = it }, label = { Text(stringResource(R.string.rule_term)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.group, { state.group = it }, label = { Text(stringResource(R.string.rule_group)) }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("LECTURE" to R.string.rule_lecture, "LAB" to R.string.rule_lab, "BLOCK" to R.string.rule_block).forEach { (value, label) -> FilterChip(state.type == value, { state.type = value }, label = { Text(stringResource(label)) }) }
    }
}
@Composable private fun RuleWeekdays(selected: Set<DayOfWeek>, changed: (Set<DayOfWeek>) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day -> FilterChip(day in selected, { changed(if(day in selected) selected - day else selected + day) }, label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) }) }
    }
}
