package dev.happyc0der.forgelog.data.local

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

    /**
     * Adds planned targets to logged exercises, and provenance to sessions.
     *
     * Two unrelated changes in one migration on purpose: one migration, one schema JSON, one round
     * of migration testing. Every column is nullable or has a default, so these are plain
     * ADD COLUMNs with no table rebuild — legal on the SQLite shipped with API 26.
     *
     * The index name has to match exactly what Room generates for the entity's @Index, or
     * validateMigration fails on the next open.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN plannedSets INTEGER")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetRepMin INTEGER")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetRepMax INTEGER")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetWeight REAL")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetDurationSeconds INTEGER")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetRestSeconds INTEGER")

            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'",
            )
            db.execSQL("ALTER TABLE workout_sessions ADD COLUMN externalSource TEXT")
            db.execSQL("ALTER TABLE workout_sessions ADD COLUMN externalId TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_workout_sessions_externalSource_externalId " +
                    "ON workout_sessions (externalSource, externalId)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
