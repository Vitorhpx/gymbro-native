package com.gymbro.local.ui

import com.gymbro.local.data.ExerciseCompletionEntity
import com.gymbro.local.data.ExerciseEntity
import com.gymbro.local.data.PlanExerciseRow
import com.gymbro.local.data.SessionExerciseSwapEntity
import com.gymbro.local.data.WorkoutSessionEntity

enum class TrainSection(val label: String) {
    PLANS("Plans"),
    HISTORY("History"),
    LIBRARY("Library"),
}

enum class TrainSurface { ACTIVE_WORKOUT, SECTIONS }

fun trainSurface(activeSessionId: Long?, browseSections: Boolean): TrainSurface =
    if (activeSessionId != null && !browseSections) TrainSurface.ACTIVE_WORKOUT else TrainSurface.SECTIONS

fun activeExerciseId(
    sessionId: Long,
    originalExerciseId: String,
    swaps: List<SessionExerciseSwapEntity>,
): String = swaps.firstOrNull {
    it.sessionId == sessionId && it.originalExerciseId == originalExerciseId
}?.swapExerciseId ?: originalExerciseId

enum class EatSection(val label: String) {
    DIET("Diet"),
    PLAN("Plan"),
    RECIPES("Recipes"),
}

data class WorkoutHistoryExercise(
    val exerciseId: String,
    val exerciseName: String,
    val weightMilliUnits: Long,
    val reps: Int,
)

data class WorkoutHistoryItem(
    val session: WorkoutSessionEntity,
    val exercises: List<WorkoutHistoryExercise>,
)

fun completedWorkoutHistory(
    sessions: List<WorkoutSessionEntity>,
    completions: List<ExerciseCompletionEntity>,
    planRows: List<PlanExerciseRow>,
    exercises: List<ExerciseEntity> = emptyList(),
    swaps: List<SessionExerciseSwapEntity> = emptyList(),
): List<WorkoutHistoryItem> {
    val exerciseNames = planRows.associate { it.exerciseId to it.exerciseName } +
        exercises.associate { it.id to it.name }
    val positionByDayAndExercise = planRows.associate { (it.dayId to it.exerciseId) to it.exercisePosition }
    val sessionsById = sessions.associateBy { it.id }
    val swappedPositionBySessionAndExercise = swaps.mapNotNull { swap ->
        val dayId = sessionsById[swap.sessionId]?.dayId ?: return@mapNotNull null
        val position = positionByDayAndExercise[dayId to swap.originalExerciseId] ?: return@mapNotNull null
        (swap.sessionId to swap.swapExerciseId) to position
    }.toMap()
    val completionsBySession = completions.groupBy { it.sessionId }

    return sessions.asSequence()
        .filter { it.completedAt != null }
        .sortedByDescending { it.completedAt }
        .map { session ->
            val exercises = completionsBySession[session.id].orEmpty()
                .sortedBy {
                    swappedPositionBySessionAndExercise[session.id to it.exerciseId]
                        ?: positionByDayAndExercise[session.dayId to it.exerciseId]
                        ?: Int.MAX_VALUE
                }
                .map { completion ->
                    WorkoutHistoryExercise(
                        exerciseId = completion.exerciseId,
                        exerciseName = exerciseNames[completion.exerciseId] ?: completion.exerciseId.toReadableName(),
                        weightMilliUnits = completion.weightMilliUnits,
                        reps = completion.reps,
                    )
                }
            WorkoutHistoryItem(session, exercises)
        }
        .toList()
}

private fun String.toReadableName(): String =
    split('-', '_').filter(String::isNotBlank).joinToString(" ") { it.lowercase() }
        .replaceFirstChar(Char::uppercase)
        .ifBlank { "Unknown exercise" }
