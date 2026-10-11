package com.classing.wear.timetable.sync

import com.classing.shared.sync.CloudSyncV2
import com.classing.shared.model.MAX_SCHEDULE_WEEK
import com.classing.shared.model.MIN_SCHEDULE_WEEK
import com.classing.wear.timetable.data.sync.RemoteCourse
import com.classing.wear.timetable.data.sync.RemoteException
import com.classing.wear.timetable.data.sync.RemoteSchedulePayload
import com.classing.wear.timetable.data.sync.RemoteSemester
import com.classing.wear.timetable.data.sync.RemoteSession
import com.classing.wear.timetable.data.sync.RemoteTimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import org.json.JSONArray
import org.json.JSONObject

data class OfficialCloudTimetableSnapshot(
    val payload: RemoteSchedulePayload,
    val revision: Long,
    val lessonCount: Int,
)

/** Converts the mobile cloud-v2 timetable domains into the Wear database model. */
object OfficialCloudTimetableMapper {
    fun map(
        document: JSONObject,
        today: LocalDate = LocalDate.now(),
        missingDomainsAreEmpty: Boolean = false,
    ): OfficialCloudTimetableSnapshot? {
        val records = document.optJSONObject("records") ?: return null
        if (!records.has(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS) &&
            !records.has(CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS) &&
            !records.has(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS) &&
            !missingDomainsAreEmpty
        ) {
            return null
        }

        val examRecords = latestRecords(domainRecords(records, CloudSyncV2.DOMAIN_TIMETABLE_EXAMS))
        val exams = if (records.has(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS)) examRecords.filterValues { !it.deleted }.map { (id, record) ->
            com.classing.client.exam.ExamJson.decode(requireNotNull(record.payload)).also { require(it.id == id) }
        }.also { com.classing.shared.exam.validateExams(it) } else if (missingDomainsAreEmpty) emptyList() else null
        val lessonRecords = latestRecords(domainRecords(records, CloudSyncV2.DOMAIN_TIMETABLE_LESSONS))
        val exceptionRecords = latestRecords(domainRecords(records, CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS))
        val mobileSettings = settingValues(domainRecords(records, CloudSyncV2.DOMAIN_MOBILE_SETTINGS))
        val weekNumberMode = mobileSettings["weekNumberMode"]?.toString()?.uppercase().orEmpty()
        val configuredStart = mobileSettings["semesterWeekStartDate"]?.toString()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val isoWeekStart = LocalDate.of(today.get(WeekFields.ISO.weekBasedYear()), 1, 4)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val semesterStart = if (weekNumberMode == "SEMESTER") configuredStart ?: today else isoWeekStart
        val semesterRemoteId = if (weekNumberMode == "SEMESTER") {
            "mobile-sync-semester-semester"
        } else {
            "mobile-sync-semester-natural"
        }
        // A wear-settings upload also changes document.updatedAt. Use only timetable record
        // versions so an unrelated setting change cannot trigger another full database apply.
        val revision = (lessonRecords.values + exceptionRecords.values + examRecords.values)
            .maxOfOrNull(CloudRecord::counter)
            ?.coerceAtLeast(1L)
            ?: document.optLong("updatedAt", 1L).coerceAtLeast(1L)
        val semester = RemoteSemester(
            remoteId = semesterRemoteId,
            name = "Official Cloud (${if (weekNumberMode == "SEMESTER") "SEMESTER" else "NATURAL"})",
            startDate = semesterStart,
            endDate = semesterStart.plusWeeks(520).minusDays(1),
            totalWeeks = 520,
            isActive = true,
            version = revision,
        )
        val slots = linkedMapOf<String, RemoteTimeSlot>()
        val courses = mutableListOf<RemoteCourse>()
        val sessions = mutableListOf<RemoteSession>()
        val exceptions = mutableListOf<RemoteException>()
        val expectedLessonCount = lessonRecords.values.count { !it.deleted }

        lessonRecords.toSortedMap().forEach { (recordId, record) ->
            if (record.deleted) return@forEach
            val item = record.payload ?: return@forEach
            val title = item.optString("title").ifBlank { return@forEach }
            val startMinute = item.optInt("startMinute", -1)
            val endMinute = item.optInt("endMinute", -1)
            if (startMinute !in 0..1439 || endMinute !in 1..1439 || endMinute <= startMinute) return@forEach
            val start = LocalTime.of(startMinute / 60, startMinute % 60)
            val end = LocalTime.of(endMinute / 60, endMinute % 60)
            val slotKey = "$start-$end"
            val slot = slots.getOrPut(slotKey) {
                RemoteTimeSlot(
                    remoteId = "mobile-slot-$slotKey",
                    semesterRemoteId = semesterRemoteId,
                    indexInDay = slots.size + 1,
                    label = slotKey,
                    startTime = start,
                    endTime = end,
                    version = revision,
                )
            }
            val stableId = item.optString("id").ifBlank { recordId }
            val courseId = "mobile-course-$stableId"
            val startWeek = item.optInt("startWeek", MIN_SCHEDULE_WEEK)
                .coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK)
            courses += RemoteCourse(
                remoteId = courseId,
                semesterRemoteId = semesterRemoteId,
                name = title,
                teacher = item.optNullableString("teacher").orEmpty(),
                classroom = item.optNullableString("location").orEmpty(),
                note = item.optNullableString("note").orEmpty(),
                colorLabel = "teal",
                isFavorite = false,
                version = revision,
            )
            sessions += RemoteSession(
                remoteId = "mobile-session-$stableId",
                semesterRemoteId = semesterRemoteId,
                courseRemoteId = courseId,
                dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                timeSlotRemoteId = slot.remoteId,
                startWeek = startWeek,
                endWeek = item.optInt("endWeek", MAX_SCHEDULE_WEEK).coerceIn(startWeek, MAX_SCHEDULE_WEEK),
                weekParity = parseWeekParity(item.optString("weekParity", "ALL")),
                scheduleRuleJson = item.optNullableString("scheduleRuleJson")?.also { com.classing.client.schedule.ScheduleRuleJson.decode(it) },
                version = revision,
            )
        }
        require(sessions.size == expectedLessonCount) {
            "Official cloud timetable contains ${expectedLessonCount - sessions.size} invalid lesson record(s)"
        }

        val expectedExceptionCount = exceptionRecords.values.count { !it.deleted }
        exceptionRecords.toSortedMap().forEach { (recordId, record) ->
            if (record.deleted) return@forEach
            val item = record.payload ?: return@forEach
            val stableId = item.optString("id").ifBlank { recordId }
            val type = item.optString("type").uppercase()
            val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: return@forEach
            if (type !in setOf("CANCEL", "MAKE_UP", "RESCHEDULE")) return@forEach
            val lessonId = item.optNullableString("lessonId")
            var syntheticCourseId: String? = null
            var syntheticSlotId: String? = null
            if (type == "MAKE_UP" || type == "RESCHEDULE") {
                val startMinute = item.optInt("startMinute", -1)
                val endMinute = item.optInt("endMinute", -1)
                if (startMinute in 0..1439 && endMinute in 1..1439 && endMinute > startMinute) {
                    val start = LocalTime.of(startMinute / 60, startMinute % 60)
                    val end = LocalTime.of(endMinute / 60, endMinute % 60)
                    syntheticSlotId = "mobile-exception-slot-$stableId"
                    slots[syntheticSlotId] = RemoteTimeSlot(
                        remoteId = syntheticSlotId,
                        semesterRemoteId = semesterRemoteId,
                        indexInDay = slots.size + 1,
                        label = "$start-$end",
                        startTime = start,
                        endTime = end,
                        version = revision,
                    )
                    syntheticCourseId = "mobile-exception-course-$stableId"
                    courses += RemoteCourse(
                        remoteId = syntheticCourseId,
                        semesterRemoteId = semesterRemoteId,
                        name = item.optNullableString("title").orEmpty().ifBlank { "Adjusted course" },
                        teacher = item.optNullableString("teacher").orEmpty(),
                        classroom = item.optNullableString("location").orEmpty(),
                        note = item.optNullableString("note").orEmpty(),
                        colorLabel = "teal",
                        isFavorite = false,
                        version = revision,
                    )
                }
            }
            exceptions += RemoteException(
                remoteId = "mobile-exception-$stableId",
                semesterRemoteId = semesterRemoteId,
                sessionRemoteId = lessonId?.let { "mobile-session-$it" },
                exceptionType = type,
                date = date,
                reason = item.optNullableString("note").orEmpty(),
                courseRemoteId = if (type == "MAKE_UP") syntheticCourseId else null,
                timeSlotRemoteId = if (type == "MAKE_UP") syntheticSlotId else null,
                dayOfWeek = item.optInt("dayOfWeek", date.dayOfWeek.value).coerceIn(1, 7),
                newCourseRemoteId = if (type == "RESCHEDULE") syntheticCourseId else null,
                newTimeSlotRemoteId = if (type == "RESCHEDULE") syntheticSlotId else null,
                version = revision,
            )
        }
        require(exceptions.size == expectedExceptionCount) {
            "Official cloud timetable contains ${expectedExceptionCount - exceptions.size} invalid exception record(s)"
        }

        return OfficialCloudTimetableSnapshot(
            payload = RemoteSchedulePayload(
                dataVersion = revision,
                exams = exams,
                applyTimetable = records.has(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS) ||
                    records.has(CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS) || missingDomainsAreEmpty,
                semesters = listOf(semester),
                timeSlots = slots.values.toList(),
                courses = courses,
                sessions = sessions,
                exceptions = exceptions,
            ),
            revision = revision,
            lessonCount = sessions.size,
        )
    }

    private data class CloudRecord(
        val payload: JSONObject?,
        val counter: Long,
        val deviceId: String,
        val deleted: Boolean,
    )

    private fun latestRecords(array: JSONArray): Map<String, CloudRecord> {
        val result = mutableMapOf<String, CloudRecord>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: error("Invalid cloud record at $index")
            val id = item.optString("id")
            require(id.isNotBlank()) { "Cloud record has no ID" }
            val version = item.optJSONObject("version") ?: JSONObject()
            val candidate = CloudRecord(
                payload = item.optString("payload").takeIf(String::isNotBlank)
                    ?.let { runCatching { JSONObject(it) }.getOrNull() },
                counter = version.optLong("counter"),
                deviceId = version.optString("deviceId"),
                deleted = item.has("deletedAt") && !item.isNull("deletedAt"),
            )
            val current = result[id]
            if (current == null || candidate.counter > current.counter ||
                candidate.counter == current.counter && candidate.deviceId > current.deviceId ||
                candidate.counter == current.counter && candidate.deviceId == current.deviceId && candidate.deleted && !current.deleted
            ) {
                result[id] = candidate
            }
        }
        return result
    }

    private fun domainRecords(records: JSONObject, domain: String): JSONArray {
        if (!records.has(domain) || records.isNull(domain)) return JSONArray()
        return when (val value = records.get(domain)) {
            is JSONArray -> value
            is JSONObject -> JSONArray().also { array -> value.keys().asSequence().sorted().forEach { id ->
                val record = value.getJSONObject(id)
                val copy = JSONObject(record.toString())
                if (copy.optString("id").isBlank()) copy.put("id", id)
                array.put(copy)
            } }
            else -> error("Cloud domain $domain must be an array or object")
        }
    }

    private fun settingValues(array: JSONArray): Map<String, Any?> = latestRecords(array)
        .filterValues { !it.deleted }
        .mapNotNull { (key, record) ->
            record.payload?.takeIf { it.has("value") }?.opt("value")?.let { key to it }
        }
        .toMap()

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf(String::isNotBlank)
    }

    private fun parseWeekParity(raw: String): String = when (raw.trim().uppercase()) {
        "ODD" -> "ODD"
        "EVEN" -> "EVEN"
        else -> "ALL"
    }
}
