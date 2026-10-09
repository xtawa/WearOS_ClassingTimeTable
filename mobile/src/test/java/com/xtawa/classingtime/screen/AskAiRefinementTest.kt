package com.xtawa.classingtime.screen

import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskAiRefinementTest {
 private val course = LessonUi("course-1", "Math", null, "Room 1", null, DayOfWeek.MONDAY, LocalTime.of(9,0), LocalTime.of(10,0))
 @Test fun updatesAndCreatesCoursesAsOneValidatedBatch() {
  val proposal = JSONObject("""{"version":1,"actions":[{"operation":"update","lessonId":"course-1","lesson":{"title":"Physics","dayOfWeek":2,"startTime":"10:00","endTime":"11:00"}},{"operation":"create","lesson":{"title":"English","dayOfWeek":3,"startTime":"12:00","endTime":"13:00"}}]}""")
  val result = applyAskAiCourseProposal(listOf(course), proposal)
  assertEquals(2, result.size); assertEquals("course-1", result.first().id); assertEquals("Physics", result.first().title)
  assertEquals("Math", course.title)
  proposal.getJSONArray("actions").getJSONObject(1).getJSONObject("lesson").put("endTime", "11:00")
  assertTrue(runCatching { applyAskAiCourseProposal(listOf(course), proposal) }.isFailure)
  assertEquals("Math", course.title)
 }
 @Test fun timetableFingerprintChangesWhenCourseChangesButNotOrdering() {
  val other = course.copy(id = "course-2", title = "English")
  fun fingerprint(items: List<LessonUi>) = timetableFingerprint(items,"Asia/Shanghai", WeekNumberMode.SEMESTER, LocalDate.of(2026,9,1), DayOfWeek.MONDAY)
  assertEquals(fingerprint(listOf(course,other)), fingerprint(listOf(other,course)))
  assertNotEquals(fingerprint(listOf(course)), fingerprint(listOf(course.copy(location="Room 2"))))
 }
 @Test fun encoderProducesRealMpegAudioFramesAt16kHz() {
  val output = ByteArrayOutputStream()
  VoiceMp3Encoder(output).use { encoder ->
   val buffer = ShortArray(1600)
   for (chunk in 0 until 10) { for (i in buffer.indices) buffer[i] = (sin(2*Math.PI*440*(chunk*1600+i)/16000)*8000).toInt().toShort(); encoder.write(buffer, buffer.size) }
  }
  val bytes = output.toByteArray(); assertTrue(bytes.size in 3000..10000)
  val header = (0 until bytes.size-4).firstOrNull { (bytes[it].toInt() and 255) == 255 && (bytes[it+1].toInt() and 0xe0) == 0xe0 }
  assertNotNull("No MPEG frame found", header)
  val index = header!!; assertEquals(2, (bytes[index+1].toInt() ushr 3) and 3) // MPEG 2
  assertEquals(1, (bytes[index+1].toInt() ushr 1) and 3) // Layer III
  assertEquals(2, (bytes[index+2].toInt() ushr 2) and 3) // 16 kHz
 }
}
