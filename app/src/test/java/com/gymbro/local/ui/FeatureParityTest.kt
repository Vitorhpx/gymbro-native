package com.gymbro.local.ui

import com.gymbro.local.data.ExerciseCompletionEntity
import com.gymbro.local.data.PlanExerciseRow
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
