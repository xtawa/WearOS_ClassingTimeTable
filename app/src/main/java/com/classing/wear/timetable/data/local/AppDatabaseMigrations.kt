package com.classing.wear.timetable.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Legacy sync repository metadata table is no longer used.
            db.execSQL("DROP TABLE IF EXISTS sync_metadata")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_semesters_remoteId ON semesters(remoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_semesters_isActive ON semesters(isActive)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_time_slots_remoteId ON time_slots(remoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_courses_remoteId ON courses(remoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_course_sessions_remoteId ON course_sessions(remoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_exceptions_remoteId ON schedule_exceptions(remoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_remoteId ON reminders(remoteId)")
        }
    }
}
