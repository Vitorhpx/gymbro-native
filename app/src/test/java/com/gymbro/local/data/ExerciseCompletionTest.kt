package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseCompletionTest {
    @Test fun bestPreviousPrefersHeaviestThenMostRepsAndExcludesCurrentSession() {
        val history = listOf(
            ExerciseCompletionEntity(sessionId = 1, exerciseId = "squat", weightMilliUnits = 80_000, reps = 10, completedAt = 10),
            ExerciseCompletionEntity(sessionId = 2, exerciseId = "squat", weightMilliUnits = 85_000, reps = 6, completedAt = 20),
            ExerciseCompletionEntity(sessionId = 3, exerciseId = "squat", weightMilliUnits = 85_000, reps = 8, completedAt = 30),
            ExerciseCompletionEntity(sessionId = 4, exerciseId = "row", weightMilliUnits = 100_000, reps = 10, completedAt = 40),
        )

        assertEquals(history[2], history.bestPrevious("squat", excludingSessionId = 99))
        assertEquals(history[1], history.bestPrevious("squat", excludingSessionId = 3))
        assertNull(history.bestPrevious("press", excludingSessionId = 99))
    }
}
