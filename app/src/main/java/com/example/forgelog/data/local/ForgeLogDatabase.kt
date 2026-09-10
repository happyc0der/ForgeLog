package com.example.forgelog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.forgelog.data.local.converter.ForgeLogTypeConverters
import com.example.forgelog.data.local.dao.ExerciseDao
import com.example.forgelog.data.local.dao.ProgramDao
import com.example.forgelog.data.local.dao.WorkoutSessionDao
import com.example.forgelog.data.local.entity.ExerciseEntity
import com.example.forgelog.data.local.entity.ProgramDayEntity
import com.example.forgelog.data.local.entity.ProgramExerciseEntity
import com.example.forgelog.data.local.entity.SessionExerciseEntity
import com.example.forgelog.data.local.entity.SetLogEntity
import com.example.forgelog.data.local.entity.WorkoutProgramEntity
import com.example.forgelog.data.local.entity.WorkoutSessionEntity

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
    version = 2,
    exportSchema = true,
)
@TypeConverters(ForgeLogTypeConverters::class)
abstract class ForgeLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
