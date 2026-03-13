package com.classing.wear.timetable.data.sync

import androidx.room.withTransaction
import com.classing.wear.timetable.data.local.AppDatabase
import com.classing.wear.timetable.data.local.entity.CourseEntity
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity
import com.classing.wear.timetable.domain.model.SyncMode

data class ApplyPayloadResult(
    val recordsWritten: Int,
    val dataVersion: Long,
)

class SyncPayloadApplier(
    private val database: AppDatabase,
) {
    suspend fun apply(payload: RemoteSchedulePayload, mode: SyncMode): ApplyPayloadResult {
        val semesterDao = database.semesterDao()
        val slotDao = database.timeSlotDao()
        val courseDao = database.courseDao()
        val sessionDao = database.courseSessionDao()
        val exceptionDao = database.scheduleExceptionDao()

        var total = 0
        val semesterIdMap = mutableMapOf<String, Long>()
        val slotIdMap = mutableMapOf<String, Long>()
        val courseIdMap = mutableMapOf<String, Long>()
        val sessionIdMap = mutableMapOf<String, Long>()

        // Keep a remoteId -> localId cache so cross-table references can be resolved in one transaction.
        suspend fun resolveSemesterId(remoteId: String): Long? {
            semesterIdMap[remoteId]?.let { return it }
            val id = semesterDao.getByRemoteId(remoteId)?.localId
            if (id != null) semesterIdMap[remoteId] = id
            return id
        }

        suspend fun resolveSlotId(remoteId: String): Long? {
            slotIdMap[remoteId]?.let { return it }
            val id = slotDao.getByRemoteId(remoteId)?.localId
            if (id != null) slotIdMap[remoteId] = id
            return id
        }

        suspend fun resolveCourseId(remoteId: String): Long? {
            courseIdMap[remoteId]?.let { return it }
            val id = courseDao.getByRemoteId(remoteId)?.localId
            if (id != null) courseIdMap[remoteId] = id
            return id
        }

        suspend fun resolveSessionId(remoteId: String): Long? {
            sessionIdMap[remoteId]?.let { return it }
            val id = sessionDao.getByRemoteId(remoteId)?.localId
            if (id != null) sessionIdMap[remoteId] = id
            return id
        }

        database.withTransaction {
            payload.semesters.forEach { remote ->
                val existing = semesterDao.getByRemoteId(remote.remoteId)
                val entity = SemesterEntity(
                    localId = existing?.localId ?: 0,
                    remoteId = remote.remoteId,
                    name = remote.name,
                    startDate = remote.startDate,
                    endDate = remote.endDate,
                    totalWeeks = remote.totalWeeks,
                    isActive = remote.isActive,
                    version = remote.version,
                )
                val id = if (existing == null) semesterDao.upsert(entity) else {
                    semesterDao.upsert(entity)
                    existing.localId
                }
                semesterIdMap[remote.remoteId] = id
                total += 1
            }

            if (mode == SyncMode.FULL) {
                semesterIdMap.values.forEach { semesterId ->
                    exceptionDao.deleteBySemester(semesterId)
                    sessionDao.deleteBySemester(semesterId)
                    courseDao.deleteBySemester(semesterId)
                    slotDao.deleteBySemester(semesterId)
                }
            }

            payload.timeSlots.forEach { remote ->
                val semesterId = resolveSemesterId(remote.semesterRemoteId) ?: return@forEach
                val existing = slotDao.getByRemoteId(remote.remoteId)
                val entity = TimeSlotEntity(
                    localId = existing?.localId ?: 0,
                    remoteId = remote.remoteId,
                    semesterId = semesterId,
                    indexInDay = remote.indexInDay,
                    label = remote.label,
                    startTime = remote.startTime,
                    endTime = remote.endTime,
                    version = remote.version,
                )
                val id = if (existing == null) slotDao.upsert(entity) else {
                    slotDao.upsert(entity)
                    existing.localId
                }
                slotIdMap[remote.remoteId] = id
                total += 1
            }

            payload.courses.forEach { remote ->
                val semesterId = resolveSemesterId(remote.semesterRemoteId) ?: return@forEach
                val existing = courseDao.getByRemoteId(remote.remoteId)
                val entity = CourseEntity(
                    localId = existing?.localId ?: 0,
                    remoteId = remote.remoteId,
                    semesterId = semesterId,
                    name = remote.name,
                    teacher = remote.teacher,
                    classroom = remote.classroom,
                    note = remote.note,
                    colorLabel = remote.colorLabel,
                    isFavorite = remote.isFavorite,
                    version = remote.version,
                )
                val id = if (existing == null) courseDao.upsert(entity) else {
                    courseDao.upsert(entity)
                    existing.localId
                }
                courseIdMap[remote.remoteId] = id
                total += 1
            }

            payload.sessions.forEach { remote ->
                val semesterId = resolveSemesterId(remote.semesterRemoteId) ?: return@forEach
                val courseId = resolveCourseId(remote.courseRemoteId) ?: return@forEach
                val slotId = resolveSlotId(remote.timeSlotRemoteId) ?: return@forEach

                val existing = sessionDao.getByRemoteId(remote.remoteId)
                val entity = CourseSessionEntity(
                    localId = existing?.localId ?: 0,
                    remoteId = remote.remoteId,
                    semesterId = semesterId,
                    courseId = courseId,
                    dayOfWeek = remote.dayOfWeek,
                    timeSlotId = slotId,
                    startWeek = remote.startWeek,
                    endWeek = remote.endWeek,
                    weekParity = remote.weekParity,
                    version = remote.version,
                )
                val id = if (existing == null) sessionDao.upsert(entity) else {
                    sessionDao.upsert(entity)
                    existing.localId
                }
                sessionIdMap[remote.remoteId] = id
                total += 1
            }

            payload.exceptions.forEach { remote ->
                val semesterId = resolveSemesterId(remote.semesterRemoteId) ?: return@forEach
                val sessionId = remote.sessionRemoteId?.let { resolveSessionId(it) }
                val courseId = remote.courseRemoteId?.let { resolveCourseId(it) }
                val slotId = remote.timeSlotRemoteId?.let { resolveSlotId(it) }
                val newCourseId = remote.newCourseRemoteId?.let { resolveCourseId(it) }
                val newSlotId = remote.newTimeSlotRemoteId?.let { resolveSlotId(it) }

                val existing = exceptionDao.getByRemoteId(remote.remoteId)
                val entity = ScheduleExceptionEntity(
                    localId = existing?.localId ?: 0,
                    remoteId = remote.remoteId,
                    semesterId = semesterId,
                    sessionId = sessionId,
                    exceptionType = remote.exceptionType,
                    date = remote.date,
                    reason = remote.reason,
                    courseId = courseId,
                    timeSlotId = slotId,
                    dayOfWeek = remote.dayOfWeek,
                    newCourseId = newCourseId,
                    newTimeSlotId = newSlotId,
                    version = remote.version,
                )
                if (remote.exceptionType == "CANCEL" && sessionId == null) return@forEach
                if (remote.exceptionType == "MAKE_UP" && (courseId == null || slotId == null)) return@forEach
                if (remote.exceptionType == "RESCHEDULE" && (sessionId == null || newCourseId == null || newSlotId == null)) return@forEach

                exceptionDao.upsert(entity)
                total += 1
            }
        }

        return ApplyPayloadResult(
            recordsWritten = total,
            dataVersion = payload.dataVersion,
        )
    }
}
