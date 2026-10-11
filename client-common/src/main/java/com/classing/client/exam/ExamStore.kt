package com.classing.client.exam

import android.content.Context
import android.content.SharedPreferences
import com.classing.shared.exam.AcademicMode
import com.classing.shared.exam.Exam
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray

object ExamStore {
    private fun prefs(context: Context) = context.getSharedPreferences("classing_exams_v1", Context.MODE_PRIVATE)
    fun load(context: Context): List<Exam> = ExamJson.list(JSONArray(prefs(context).getString("exams", "[]")))
    fun save(context: Context, exams: List<Exam>) {
        val encoded = ExamJson.array(exams).toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        check(prefs(context).edit().putString("exams", encoded).commit()) { "Could not save exams" }
    }
    fun mode(context: Context): AcademicMode = runCatching {
        AcademicMode.valueOf(prefs(context).getString("mode", AcademicMode.SCHEDULE.name)!!)
    }.getOrDefault(AcademicMode.SCHEDULE)
    fun setMode(context: Context, mode: AcademicMode) { prefs(context).edit().putString("mode", mode.name).apply() }
    fun observe(context: Context) = callbackFlow {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "exams") trySend(load(context))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(context))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
