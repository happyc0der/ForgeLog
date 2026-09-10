package com.example.forgelog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ForgeLogMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN expandedSessionExerciseId INTEGER",
            )
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN restTimerType TEXT NOT NULL DEFAULT 'none'",
            )
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN restTimerStartedAt INTEGER",
            )
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN restTimerDurationSeconds INTEGER",
            )
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN restTimerPausedRemainingSeconds INTEGER",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
