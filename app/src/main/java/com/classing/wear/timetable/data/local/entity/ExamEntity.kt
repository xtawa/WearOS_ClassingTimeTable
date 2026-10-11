package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.classing.client.exam.ExamJson
import com.classing.shared.exam.Exam
import org.json.JSONObject

@Entity(tableName = "exams")
data class ExamEntity(@PrimaryKey val id: String, val payload: String) {
    fun toExam(): Exam = ExamJson.decode(JSONObject(payload)).also { require(it.id == id) }
    companion object { fun from(exam: Exam) = ExamEntity(exam.id, ExamJson.encode(exam).toString()) }
}
