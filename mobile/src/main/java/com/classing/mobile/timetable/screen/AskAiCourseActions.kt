package com.xtawa.classingtime.screen

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import org.json.JSONObject

/** Validate the complete batch before mutating local course state. */
internal fun applyAskAiCourseProposal(existing: List<LessonUi>, proposal: JSONObject): List<LessonUi> {
 require(proposal.optInt("version") == 1)
 val actions = proposal.getJSONArray("actions"); require(actions.length() in 1..20)
 val result = existing.toMutableList(); val updatedIds = mutableSetOf<String>()
 for (i in 0 until actions.length()) {
  val action = actions.getJSONObject(i); val operation = action.getString("operation"); require(operation == "create" || operation == "update")
  val id = if (operation == "create") UUID.randomUUID().toString() else action.getString("lessonId")
  val index = result.indexOfFirst { it.id == id }
  if (operation == "update") require(index >= 0 && updatedIds.add(id)) { "Course changed. Please ask again." }
  val item = action.getJSONObject("lesson")
  val title = item.getString("title").trim(); require(title.isNotBlank() && title.length <= 1000)
  val start = LocalTime.parse(item.getString("startTime")); val end = LocalTime.parse(item.getString("endTime")); require(end > start)
  val previous = result.getOrNull(index)
  val startWeek = item.optInt("startWeek", previous?.startWeek ?: 1); val endWeek = item.optInt("endWeek", previous?.endWeek ?: 20); require(startWeek in 1..53 && endWeek in startWeek..53)
  val parity = item.optString("weekParity", previous?.weekParity?.name ?: "ALL"); require(parity in listOf("ALL", "ODD", "EVEN"))
  fun optional(key: String): String? = if (!item.has(key)) when (key) { "teacher" -> previous?.teacher; "location" -> previous?.location; else -> previous?.note } else if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotBlank() }?.also { require(it.length <= 1000) }
  val lesson = LessonUi(id, title, optional("teacher"), optional("location"), optional("note"), DayOfWeek.of(item.getInt("dayOfWeek")), start, end, startWeek, endWeek, LessonWeekParity.valueOf(parity))
  if (operation == "create") result.add(lesson) else result[index] = lesson
 }
 return result.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
}
