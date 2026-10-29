package com.gymbro.local.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutPlanPersistenceTest {
    private lateinit var database: GymBroDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymBroDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun seedEditReorderAndCustomExerciseSurviveReconciliation() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val before = dao.planExercises("gymbro-ppl-45-v2").first().filter { it.dayId == "push" }
        val first = dao.prescription(before[0].prescriptionId)!!
        val second = dao.prescription(before[1].prescriptionId)!!

        dao.editPrescription(first.id, 4, 5, 8, 150)
        dao.swapPrescriptionPositions(first, second)
        dao.upsertExercise(ExerciseEntity("custom-stable-test", "My movement", "back", "cable", false))
        dao.reconcileWorkoutSeeds()

        val edited = dao.prescription(first.id)!!
        assertEquals(4, edited.sets)
        assertEquals(150, edited.restSeconds)
        assertEquals(second.position, edited.position)
        assertFalse(edited.isSeeded)
        assertEquals("My movement", dao.exercise("custom-stable-test")!!.name)
    }

    @Test fun oldBackupRestoreReconcilesRequiredSeeds() = runBlocking {
        val legacy = """{"version":1,"workoutSessions":[],"setCompletions":[],"exerciseCompletions":[],"foodEntries":[],"customFoods":[],"savedMeals":[],"savedMealItems":[],"weightEntries":[],"settings":[{"id":1,"calorieGoal":2000,"proteinGoal":160}]}"""

        val counts = PortableBackupRepository(database).importJson(legacy)

        assertEquals(29, counts.exercises)
        assertEquals(15, counts.exercisePrescriptions)
        assertEquals("GymBro PPL 45 V2", database.dao().allWorkoutPlansOnce().single().name)
    }

    @Test fun movePrescriptionSelectsAndSwapsInsideDaoTransaction() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()

        assertTrue(dao.movePrescription("push", "push-chest-press", 1))

        val rows = dao.dayPrescriptions("push")
        assertEquals(listOf("push-incline-press", "push-chest-press"), rows.take(2).map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), rows.map { it.position })
        assertFalse(rows.take(2).any { it.isSeeded })
        assertFalse(dao.movePrescription("push", "push-incline-press", -1))
    }

    @Test fun restoredUserEditedSeedSurvivesStartupReconciliationExactly() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        dao.editPrescription("push-chest-press", 4, 5, 8, 150)
        val (json, _) = PortableBackupRepository(database).exportJson()

        PortableBackupRepository(database).importJson(json)
        val beforeStartup = PortableBackupRepository(database).exportJson().first
        dao.reconcileWorkoutSeeds()
        val afterStartup = PortableBackupRepository(database).exportJson().first

        assertEquals(beforeStartup, afterStartup)
        assertEquals(4, dao.prescription("push-chest-press")!!.sets)
        assertFalse(dao.prescription("push-chest-press")!!.isSeeded)
    }
}
