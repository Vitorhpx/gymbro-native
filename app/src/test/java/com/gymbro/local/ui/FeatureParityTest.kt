package com.gymbro.local.ui

import com.gymbro.local.data.ExerciseCompletionEntity
import com.gymbro.local.data.ExerciseEntity
import com.gymbro.local.data.PlanExerciseRow
import com.gymbro.local.data.SessionExerciseSwapEntity
import com.gymbro.local.data.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureParityTest {
    @Test fun `train and eat expose the same primary sections as the website`() {
        assertEquals(listOf("Plans", "History", "Library"), TrainSection.entries.map { it.label })
        assertEquals(listOf("Diet", "Plan", "Recipes"), EatSection.entries.map { it.label })
    }

    @Test fun `active workout can yield to section browsing and be resumed`() {
        assertEquals(TrainSurface.ACTIVE_WORKOUT, trainSurface(activeSessionId = 7, browseSections = false))
        assertEquals(TrainSurface.SECTIONS, trainSurface(activeSessionId = 7, browseSections = true))
        assertEquals(TrainSurface.SECTIONS, trainSurface(activeSessionId = null, browseSections = false))
    }

    @Test fun `active exercise identity follows the persisted session swap`() {
        val swaps = listOf(SessionExerciseSwapEntity(7, "chest-press", "pec-deck"))

        assertEquals("pec-deck", activeExerciseId(7, "chest-press", swaps))
        assertEquals("incline-press", activeExerciseId(7, "incline-press", swaps))
        assertEquals("chest-press", activeExerciseId(8, "chest-press", swaps))
    }

    @Test fun `completed workout history is newest first and includes completed exercise details`() {
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, dayId = "push", dayName = "Push", startedAt = 100, completedAt = 200),
            WorkoutSessionEntity(id = 2, dayId = "pull", dayName = "Pull", startedAt = 300, completedAt = 400),
            WorkoutSessionEntity(id = 3, dayId = "legs", dayName = "Legs", startedAt = 500),
        )
        val rows = listOf(
            planRow("push", "bench", "Chest Press", 0),
            planRow("pull", "row", "Seated Row", 0),
        )
        val completions = listOf(
            ExerciseCompletionEntity(1, "bench", 45_000, 10, 150),
            ExerciseCompletionEntity(2, "row", 55_500, 8, 350),
        )

        val history = completedWorkoutHistory(sessions, completions, rows)

        assertEquals(listOf(2L, 1L), history.map { it.session.id })
        assertEquals("Seated Row", history.first().exercises.single().exerciseName)
        assertEquals(55_500, history.first().exercises.single().weightMilliUnits)
        assertEquals(8, history.first().exercises.single().reps)
    }

    @Test fun `swapped history keeps the original workout slot order`() {
        val session = WorkoutSessionEntity(id = 9, dayId = "push", dayName = "Push", startedAt = 100, completedAt = 200)
        val rows = listOf(
            planRow("push", "chest-press", "Chest Press", 0),
            planRow("push", "incline-press", "Incline Press", 1),
        )
        val completions = listOf(
            ExerciseCompletionEntity(9, "incline-press", 30_000, 10, 160),
            ExerciseCompletionEntity(9, "pec-deck", 20_000, 12, 150),
        )
        val exercises = listOf(ExerciseEntity("pec-deck", "Pec Deck", "chest", "machine"))
        val swaps = listOf(SessionExerciseSwapEntity(9, "chest-press", "pec-deck"))

        val history = completedWorkoutHistory(listOf(session), completions, rows, exercises, swaps)

        assertEquals(listOf("pec-deck", "incline-press"), history.single().exercises.map { it.exerciseId })
    }

    @Test fun `history uses the library name for a swapped exercise outside the plan`() {
        val session = WorkoutSessionEntity(id = 9, dayId = "push", dayName = "Push", startedAt = 100, completedAt = 200)
        val completion = ExerciseCompletionEntity(9, "db-biceps-curl", 20_000, 12, 150)
        val exercises = listOf(ExerciseEntity("db-biceps-curl", "DB Biceps Curl", "biceps", "dumbbell"))

        val history = completedWorkoutHistory(listOf(session), listOf(completion), emptyList(), exercises)

        assertEquals("DB Biceps Curl", history.single().exercises.single().exerciseName)
    }

    @Test fun `history keeps an unknown exercise visible instead of dropping completed data`() {
        val session = WorkoutSessionEntity(id = 9, dayId = "custom", dayName = "Custom", startedAt = 100, completedAt = 200)
        val completion = ExerciseCompletionEntity(9, "removed-exercise", 20_000, 12, 150)

        val history = completedWorkoutHistory(listOf(session), listOf(completion), emptyList())

        assertEquals("Removed exercise", history.single().exercises.single().exerciseName)
    }

    private fun planRow(dayId: String, exerciseId: String, name: String, position: Int) = PlanExerciseRow(
        prescriptionId = "$dayId-$exerciseId",
        dayId = dayId,
        dayName = dayId,
        dayPosition = 0,
        exerciseId = exerciseId,
        exerciseName = name,
        exercisePosition = position,
        sets = 3,
        minReps = 8,
        maxReps = 12,
        restSeconds = 90,
    )
}
