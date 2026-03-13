package com.classing.wear.timetable.data.repository

import com.classing.shared.importer.CourseDraft
import com.classing.shared.importer.IcsImportParser
import com.classing.shared.importer.ImportResult
import com.classing.shared.importer.ScheduleImportAdapter
import com.classing.wear.timetable.domain.repository.CourseImportRepository

class PlaceholderCourseImportRepository : CourseImportRepository {
    private val parser = IcsImportParser()
    private val adapter = ScheduleImportAdapter()

    override suspend fun parseForPreview(raw: String): ImportResult = parser.parse(raw)

    override suspend fun confirmImport(drafts: List<CourseDraft>): Result<Int> {
        // TODO: 将草稿映射到 Room 课程/时间段/例外实体后落库。
        return Result.success(drafts.size)
    }

    override suspend fun importFromText(raw: String): Result<Int> {
        val parsed = parseForPreview(raw)
        if (parsed is ImportResult.Failure) return Result.failure(IllegalArgumentException(parsed.reason))
        return confirmImport(adapter.toDrafts(parsed))
    }

    override suspend fun importFromCompanion(payload: ByteArray): Result<Int> {
        return importFromText(payload.toString(Charsets.UTF_8))
    }
}
