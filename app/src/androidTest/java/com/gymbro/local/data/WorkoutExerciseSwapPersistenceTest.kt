package com.gymbro.local.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutExerciseSwapPersistenceTest {
    private lateinit var database: GymBroDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GymBroDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun swapSurvivesPersistenceAndCompletesTheSelectedExercise() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val sessionId = dao.startSessionIfNone(WorkoutSessionEntity(dayId = "push", dayName = "Push", startedAt = 100))!!

        dao.setSessionExerciseSwap(sessionId, "chest-press", "pec-deck")
        dao.completePlannedExercise(sessionId, "chest-press", 50_000, 10, 200)

        assertEquals(
            listOf(SessionExerciseSwapEntity(sessionId, "chest-press", "pec-deck")),
            dao.allSessionExerciseSwapsOnce(),
        )
        assertEquals("pec-deck", dao.allExerciseCompletionsOnce().single().exerciseId)
        assertTrue(
            runCatching { dao.setSessionExerciseSwap(sessionId, "chest-press", "pec-deck") }.isFailure,
        )
    }

    @Test fun originalCanBeRestoredBeforeCompletion() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val sessionId = dao.startSessionIfNone(WorkoutSessionEntity(dayId = "push", dayName = "Push", startedAt = 100))!!
        dao.setSessionExerciseSwap(sessionId, "chest-press", "pec-deck")

        dao.clearSessionExerciseSwap(sessionId, "chest-press")
        dao.completePlannedExercise(sessionId, "chest-press", 45_000, 8, 200)

        assertTrue(dao.allSessionExerciseSwapsOnce().isEmpty())
        assertEquals("chest-press", dao.allExerciseCompletionsOnce().single().exerciseId)
    }

    @Test fun completedWorkoutHistoryCannotBeUncompleted() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val sessionId = dao.startSessionIfNone(WorkoutSessionEntity(dayId = "push", dayName = "Push", startedAt = 100))!!
        dao.completePlannedExercise(sessionId, "chest-press", 45_000, 8, 150)
        dao.completeSession(sessionId, 200, 20_000)

        val result = runCatching { dao.uncompletePlannedExercise(sessionId, "chest-press") }

        assertTrue(result.isFailure)
        assertEquals("chest-press", dao.allExerciseCompletionsOnce().single().exerciseId)
    }

    @Test fun twoWorkoutSlotsCannotResolveToTheSameExercise() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val sessionId = dao.startSessionIfNone(WorkoutSessionEntity(dayId = "legs", dayName = "Legs", startedAt = 100))!!
        dao.setSessionExerciseSwap(sessionId, "hip-thrust", "leg-press")

        val result = runCatching { dao.setSessionExerciseSwap(sessionId, "smith-squat", "leg-press") }

        assertTrue(result.isFailure)
        assertEquals(listOf(SessionExerciseSwapEntity(sessionId, "hip-thrust", "leg-press")), dao.allSessionExerciseSwapsOnce())
    }
}