package com.classing.wear.timetable.domain.repository

import com.classing.shared.importer.CourseDraft
import com.classing.shared.importer.ImportResult

interface CourseImportRepository {
    suspend fun parseForPreview(raw: String): ImportResult
    suspend fun confirmImport(drafts: List<CourseDraft>): Result<Int>
    suspend fun importFromText(raw: String): Result<Int>
    suspend fun importFromCompanion(payload: ByteArray): Result<Int>
}
