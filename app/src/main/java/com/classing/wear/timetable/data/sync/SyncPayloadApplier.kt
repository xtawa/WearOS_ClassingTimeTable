package com.classing.wear.timetable.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.classing.wear.timetable.data.local.AppDatabase
import com.classing.wear.timetable.data.local.dao.CourseDao
import com.classing.wear.timetable.data.local.dao.CourseSessionDao
import com.classing.wear.timetable.data.local.dao.ScheduleExceptionDao
import com.classing.wear.timetable.data.local.dao.SemesterDao
import com.classing.wear.timetable.data.local.dao.TimeSlotDao
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
        var total = 0

        database.withTransaction {
            val semesterDao = database.semesterDao()
            val slotDao = database.timeSlotDao()
            val courseDao = database.courseDao()
            val sessionDao = database.courseSessionDao()
            val exceptionDao = database.scheduleExceptionDao()

            val semesterIdMap = mutableMapOf<String, Long>()
            val slotIdMap = mutableMapOf<String, Long>()
            val courseIdMap = mutableMapOf<String, Long>()
            val sessionIdMap = mutableMapOf<String, Long>()
            var activeSemesterId: Long? = null

            if (mode == SyncMode.DELTA) {
                payload.deletedExceptionRemoteIds.takeIf { it.isNotEmpty() }
                    ?.let { exceptionDao.deleteByRemoteIds(it.toList()) }
                payload.deletedSessionRemoteIds.takeIf { it.isNotEmpty() }
                    ?.let { sessionDao.deleteByRemoteIds(it.toList()) }
                payload.deletedCourseRemoteIds.takeIf { it.isNotEmpty() }
                    ?.let { courseDao.deleteByRemoteIds(it.toList()) }
            }

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
                val id = stableLocalId(
                    existingLocalId = existing?.localId,
                    upsertResult = semesterDao.upsert(entity),
                    entityName = "semester",
                )
                semesterIdMap[remote.remoteId] = id
                if (remote.isActive) activeSemesterId = id
                total += 1
            }

            activeSemesterId?.let { semesterDao.setActiveSemester(it) }

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
                val id = stableLocalId(
                    existingLocalId = existing?.localId,
                    upsertResult = slotDao.upsert(entity),
                    entityName = "time slot",
                )
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
                val id = stableLocalId(
                    existingLocalId = existing?.localId,
                    upsertResult = courseDao.upsert(entity),
                    entityName = "course",
                )
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
                val id = stableLocalId(
                    existingLocalId = existing?.localId,
                    upsertResult = sessionDao.upsert(entity),
                    entityName = "course session",
                )
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

                val invalidReason = when (remote.exceptionType) {
                    "CANCEL" -> if (sessionId == null) "missing session reference" else null
                    "MAKE_UP" -> when {
                        courseId == null -> "missing course reference"
                        slotId == null -> "missing time slot reference"
                        else -> null
                    }
                    "RESCHEDULE" -> when {
                        sessionId == null -> "missing session reference"
                        newCourseId == null -> "missing new course reference"
                        newSlotId == null -> "missing new time slot reference"
                        else -> null
                    }
                    else -> "unknown exception type ${remote.exceptionType}"
                }
                if (invalidReason != null) {
                    Log.w(TAG, "Skipping invalid exception remoteId=${remote.remoteId}: $invalidReason")
                    return@forEach
                }

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

                exceptionDao.upsert(entity)
                total += 1
            }

            if (mode == SyncMode.FULL) {
                pruneFullSyncRows(
                    payload = payload,
                    semesterDao = semesterDao,
                    slotDao = slotDao,
                    courseDao = courseDao,
                    sessionDao = sessionDao,
                    exceptionDao = exceptionDao,
                    semesterIdMap = semesterIdMap,
                )
            }
        }

        return ApplyPayloadResult(
            recordsWritten = total,
            dataVersion = payload.dataVersion,
        )
    }

    private suspend fun pruneFullSyncRows(
        payload: RemoteSchedulePayload,
        semesterDao: SemesterDao,
        slotDao: TimeSlotDao,
        courseDao: CourseDao,
        sessionDao: CourseSessionDao,
        exceptionDao: ScheduleExceptionDao,
        semesterIdMap: Map<String, Long>,
    ) {
        payload.semesters.forEach { semester ->
            val semesterId = semesterIdMap[semester.remoteId] ?: return@forEach

            // Delete dependants before their referenced rows. The previous slot/course-first
            // order failed whenever an old session still referenced a row being pruned.
            pruneSemesterRows(
                remoteIds = payload.exceptions
                    .filter { it.semesterRemoteId == semester.remoteId }
                    .map(RemoteException::remoteId),
                deleteAll = { exceptionDao.deleteBySemester(semesterId) },
                deleteMissing = { remoteIds -> exceptionDao.deleteMissingRemoteIds(semesterId, remoteIds) },
            )
            pruneSemesterRows(
                remoteIds = payload.sessions
                    .filter { it.semesterRemoteId == semester.remoteId }
                    .map(RemoteSession::remoteId),
                deleteAll = { sessionDao.deleteBySemester(semesterId) },
                deleteMissing = { remoteIds -> sessionDao.deleteMissingRemoteIds(semesterId, remoteIds) },
            )
            pruneSemesterRows(
                remoteIds = payload.courses
                    .filter { it.semesterRemoteId == semester.remoteId }
                    .map(RemoteCourse::remoteId),
                deleteAll = { courseDao.deleteBySemester(semesterId) },
                deleteMissing = { remoteIds -> courseDao.deleteMissingRemoteIds(semesterId, remoteIds) },
            )
            pruneSemesterRows(
                remoteIds = payload.timeSlots
                    .filter { it.semesterRemoteId == semester.remoteId }
                    .map(RemoteTimeSlot::remoteId),
                deleteAll = { slotDao.deleteBySemester(semesterId) },
                deleteMissing = { remoteIds -> slotDao.deleteMissingRemoteIds(semesterId, remoteIds) },
            )
        }

        val remoteSemesterIds = payload.semesters.map(RemoteSemester::remoteId)
        val obsoleteSemesterIds = semesterDao.getAll()
            .filter { it.remoteId == null || it.remoteId !in remoteSemesterIds }
            .map { it.localId }
        obsoleteSemesterIds.forEach { semesterId ->
            // Clear the dependent graph explicitly before replacing semesters. This keeps full
            // sync safe across older database schemas and source/mode switches.
            exceptionDao.deleteBySemester(semesterId)
            sessionDao.deleteBySemester(semesterId)
            courseDao.deleteBySemester(semesterId)
            slotDao.deleteBySemester(semesterId)
        }
        if (remoteSemesterIds.isEmpty()) {
            semesterDao.deleteAll()
        } else {
            semesterDao.deleteMissingRemoteIds(remoteSemesterIds)
        }
    }

    private suspend fun pruneSemesterRows(
        remoteIds: List<String>,
        deleteAll: suspend () -> Unit,
        deleteMissing: suspend (List<String>) -> Unit,
    ) {
        if (remoteIds.isEmpty()) {
            deleteAll()
        } else {
            deleteMissing(remoteIds)
        }
    }

    companion object {
        private const val TAG = "SyncPayloadApplier"
    }
}

/**
 * Room's [androidx.room.Upsert] returns the inserted row id for a new row, but returns `-1`
 * after resolving a uniqueness conflict through an update. Parent ids used by later payload
 * stages therefore have to keep the id that was already stored in the database.
 */
internal fun stableLocalId(
    existingLocalId: Long?,
    upsertResult: Long,
    entityName: String,
): Long {
    val localId = existingLocalId ?: upsertResult
    require(localId > 0) {
        "Unable to resolve a persisted local id for $entityName (upsert result: $upsertResult)"
    }
    return localId
}
