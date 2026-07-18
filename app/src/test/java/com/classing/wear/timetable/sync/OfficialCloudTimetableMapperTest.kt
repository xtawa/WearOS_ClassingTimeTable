package com.classing.wear.timetable.sync

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OfficialCloudTimetableMapperTest {
    @Test
    fun map_readsLiveLessonsAndIgnoresTombstones() {
        val root = document(
            lessonRecords = JSONArray()
                .put(record("lesson-1", lesson("lesson-1", "Math", 480, 525), 12))
                .put(record("lesson-2", lesson("lesson-2", "Deleted", 600, 645), 13, deleted = true)),
        )

        val mapped = OfficialCloudTimetableMapper.map(root, LocalDate.parse("2026-07-18"))!!

        assertEquals(1, mapped.lessonCount)
        assertEquals(1, mapped.payload.courses.size)
        assertEquals("Math", mapped.payload.courses.single().name)
        assertEquals(13L, mapped.revision)
    }

    @Test
    fun map_returnsNullWhenTimetableDomainsAreAbsent() {
        val root = JSONObject()
            .put("format", "classing_cloud_sync_v2")
            .put("records", JSONObject())

        assertNull(OfficialCloudTimetableMapper.map(root))
    }

    @Test
    fun map_treatsMissingDomainsAsEmptyForAccountSwitch() {
        val root = JSONObject()
            .put("format", "classing_cloud_sync_v2")
            .put("updatedAt", 21)
            .put("records", JSONObject())

        val mapped = OfficialCloudTimetableMapper.map(
            document = root,
            today = LocalDate.parse("2026-07-18"),
            missingDomainsAreEmpty = true,
        )!!

        assertEquals(0, mapped.lessonCount)
        assertEquals(21L, mapped.revision)
    }

    @Test
    fun map_rejectsPartialApplyWhenLiveLessonIsMalformed() {
        val root = document(
            lessonRecords = JSONArray().put(
                record("broken", lesson("broken", "Broken", 600, 590), 20),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            OfficialCloudTimetableMapper.map(root, LocalDate.parse("2026-07-18"))
        }
    }

    private fun document(lessonRecords: JSONArray): JSONObject = JSONObject()
        .put("format", "classing_cloud_sync_v2")
        .put("updatedAt", 99)
        .put(
            "records",
            JSONObject()
                .put("timetable.lessons", lessonRecords)
                .put("timetable.exceptions", JSONArray()),
        )

    private fun lesson(id: String, title: String, startMinute: Int, endMinute: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("dayOfWeek", 1)
        .put("startMinute", startMinute)
        .put("endMinute", endMinute)
        .put("startWeek", 1)
        .put("endWeek", 20)
        .put("weekParity", "ALL")

    private fun record(
        id: String,
        payload: JSONObject,
        counter: Long,
        deleted: Boolean = false,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("payload", payload.toString())
        .put(
            "version",
            JSONObject().put("counter", counter).put("deviceId", "phone").put("changedAt", counter),
        )
        .put("deletedAt", if (deleted) counter else JSONObject.NULL)
        .put("recoverableUntil", JSONObject.NULL)
}
