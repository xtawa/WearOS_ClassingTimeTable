package com.classing.wear.timetable.data.repository

import com.classing.wear.timetable.domain.repository.CourseImportRepository

class PlaceholderCourseImportRepository : CourseImportRepository {
    override suspend fun importFromText(raw: String): Result<Int> {
        return Result.failure(UnsupportedOperationException("待接入教务系统/第三方课程表解析"))
    }

    override suspend fun importFromCompanion(payload: ByteArray): Result<Int> {
        return Result.failure(UnsupportedOperationException("待接入手机 Companion 导入协议"))
    }
}
