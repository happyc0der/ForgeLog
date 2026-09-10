package com.example.forgelog.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.forgelog.data.local.entity.ExerciseEntity
import com.example.forgelog.data.local.entity.ProgramDayEntity
import com.example.forgelog.data.local.entity.ProgramExerciseEntity
import com.example.forgelog.data.local.entity.SessionExerciseEntity
import com.example.forgelog.data.local.entity.SetLogEntity
import com.example.forgelog.data.local.entity.WorkoutProgramEntity
import com.example.forgelog.data.local.entity.WorkoutSessionEntity

data class ProgramExerciseDetailEntity(
    @Embedded val programExercise: ProgramExerciseEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
    )
    val exercise: ExerciseEntity,
)

data class ProgramDayDetailEntity(
    @Embedded val day: ProgramDayEntity,
    @Relation(
        entity = ProgramExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "programDayId",
    )
    val exercises: List<ProgramExerciseDetailEntity>,
)

data class ProgramDetailEntity(
    @Embedded val program: WorkoutProgramEntity,
    @Relation(
        entity = ProgramDayEntity::class,
        parentColumn = "id",
        entityColumn = "programId",
    )
    val days: List<ProgramDayDetailEntity>,
)

data class SessionExerciseWithSetsEntity(
    @Embedded val exercise: SessionExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionExerciseId",
    )
    val sets: List<SetLogEntity>,
)

data class SessionDetailEntity(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<SessionExerciseWithSetsEntity>,
)
