package dev.happyc0der.forgelog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.happyc0der.forgelog.data.local.converter.ForgeLogTypeConverters
import dev.happyc0der.forgelog.data.local.dao.ExerciseDao
import dev.happyc0der.forgelog.data.local.dao.ProgramDao
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutProgramEntity::class,
        ProgramDayEntity::class,
        ProgramExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ForgeLogTypeConverters::class)
abstract class ForgeLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
