package com.classing.wear.timetable.domain.repository

interface CourseImportRepository {
    suspend fun importFromText(raw: String): Result<Int>
    suspend fun importFromCompanion(payload: ByteArray): Result<Int>
}
