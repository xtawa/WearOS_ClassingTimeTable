package com.classing.client.exam

import com.classing.shared.exam.Exam
import com.classing.shared.exam.validateExams
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ExamJson {
    fun encode(exam: Exam): JSONObject = JSONObject().put("id", exam.id).put("title", exam.title)
        .put("startAt", exam.startAt).put("endAt", exam.endAt).put("timezone", exam.timezone)
        .put("location", exam.location).put("note", exam.note).put("courseId", exam.courseId ?: JSONObject.NULL)
        .put("reminderMinutes", exam.reminderMinutes).put("completed", exam.completed)

    fun decode(item: JSONObject): Exam = Exam(
        id = item.getString("id"), title = item.getString("title").trim(),
        startAt = integer(item, "startAt"), endAt = integer(item, "endAt"), timezone = item.getString("timezone"),
        location = optionalText(item, "location"), note = optionalText(item, "note"),
        courseId = if (item.isNull("courseId")) null else item.optString("courseId").takeIf { it.isNotBlank() },
        reminderMinutes = if (item.has("reminderMinutes")) item.getInt("reminderMinutes") else 10,
        completed = if (item.has("completed")) item.getBoolean("completed") else false,
    )

    fun array(exams: List<Exam>): JSONArray {
        validateExams(exams)
        return JSONArray().also { array -> exams.forEach { array.put(encode(it)) } }
    }

    fun list(array: JSONArray): List<Exam> {
        require(array.length() <= 500) { "Maximum 500 exams" }
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }.also(::validateExams)
    }

    private fun integer(item: JSONObject, key: String): Long {
        val value = item.get(key)
        require(value is Long || value is Int) { "$key must be integer milliseconds" }
        return (value as Number).toLong()
    }

    private fun optionalText(item: JSONObject, key: String): String =
        if (!item.has(key) || item.isNull(key)) "" else item.getString(key)

    /** Validate a whole AI batch against the exact snapshot before writing anything. */
    fun applyProposal(existing: List<Exam>, proposal: JSONObject): List<Exam> {
        validateExams(existing)
        require(proposal.getInt("version") == 1)
        val actions = proposal.getJSONArray("actions")
        require(actions.length() in 1..20)
        val result = existing.toMutableList()
        val touched = mutableSetOf<String>()
        for (index in 0 until actions.length()) {
            val action = actions.getJSONObject(index)
            val operation = action.getString("operation")
            require(operation in setOf("create", "update", "delete"))
            val id = if (operation == "create") UUID.randomUUID().toString() else action.getString("examId")
            val at = result.indexOfFirst { it.id == id }
            if (operation != "create") require(at >= 0 && touched.add(id)) { "Exam changed. Please ask again." }
            if (operation == "delete") result.removeAt(at)
            else {
                val exam = decode(JSONObject(action.getJSONObject("exam").toString()).put("id", id))
                if (operation == "create") result.add(exam) else result[at] = exam
            }
        }
        validateExams(result)
        return result.sortedWith(compareBy<Exam> { it.startAt }.thenBy { it.id })
    }
}
