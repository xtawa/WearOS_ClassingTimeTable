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
}
