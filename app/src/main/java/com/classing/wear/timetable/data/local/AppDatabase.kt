package com.classing.wear.timetable.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.classing.wear.timetable.data.local.converter.AppTypeConverters
import com.classing.wear.timetable.data.local.dao.CourseDao
import com.classing.wear.timetable.data.local.dao.CourseSessionDao
import com.classing.wear.timetable.data.local.dao.ReminderDao
import com.classing.wear.timetable.data.local.dao.ScheduleExceptionDao
import com.classing.wear.timetable.data.local.dao.SemesterDao
import com.classing.wear.timetable.data.local.dao.SyncMetadataDao
import com.classing.wear.timetable.data.local.dao.TimeSlotDao
import com.classing.wear.timetable.data.local.entity.CourseEntity
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import com.classing.wear.timetable.data.local.entity.ReminderEntity
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import com.classing.wear.timetable.data.local.entity.SyncMetadataEntity
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity

@Database(
    entities = [
        SemesterEntity::class,
        TimeSlotEntity::class,
        CourseEntity::class,
        CourseSessionEntity::class,
        ScheduleExceptionEntity::class,
        ReminderEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun courseDao(): CourseDao
    abstract fun courseSessionDao(): CourseSessionDao
    abstract fun scheduleExceptionDao(): ScheduleExceptionDao
    abstract fun reminderDao(): ReminderDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
