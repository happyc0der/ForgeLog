package dev.happyc0der.forgelog.data.local.converter

import androidx.room.TypeConverter
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.RestTimerType
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType

class ForgeLogTypeConverters {
    @TypeConverter
    fun fromExerciseCategory(value: ExerciseCategory): String = value.storageValue

    @TypeConverter
    fun toExerciseCategory(value: String): ExerciseCategory = ExerciseCategory.fromStorage(value)

    @TypeConverter
    fun fromExerciseUnit(value: ExerciseUnit): String = value.storageValue

    @TypeConverter
    fun toExerciseUnit(value: String): ExerciseUnit = ExerciseUnit.fromStorage(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.storageValue

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.fromStorage(value)

    @TypeConverter
    fun fromSetType(value: SetType): String = value.storageValue

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.fromStorage(value)

    /** Kept for leftover restTimerType column values. Not written as live data. */
    @TypeConverter
    fun fromRestTimerType(value: RestTimerType): String = value.storageValue

    @TypeConverter
    fun toRestTimerType(value: String): RestTimerType = RestTimerType.fromStorage(value)
}
