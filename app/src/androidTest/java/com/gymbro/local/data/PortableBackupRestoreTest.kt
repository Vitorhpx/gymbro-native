package com.gymbro.local.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableBackupRestoreTest {
    private lateinit var database: GymBroDatabase
    private lateinit var repository: PortableBackupRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GymBroDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = PortableBackupRepository(database)
    }

    @After fun close() = database.close()

    @Test fun restorePreservesIdsActiveStateLegacySetsAndExactCounts() = runBlocking {
        val dao = database.dao()
        dao.reconcileWorkoutSeeds()
        val expectedFood = FoodEntryEntity(
            8, "meal-10-rice", "Rice", 100, 130_000, 2_700, 130, MealSlot.LUNCH,
            "prepared-stable", "prepared-log-stable", 20_388,
        )
        val expectedPrepared = PreparedMealEntity("prepared-stable", 10, 20_388, 4, 1, false, 155)
        dao.restoreSessions(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)))
        dao.restoreSetCompletions(listOf(SetCompletionEntity(7, "chest-press", 0)))
        dao.restoreExerciseCompletions(listOf(ExerciseCompletionEntity(7, "chest-press", 50_000, 8, 120)))
        dao.restoreFoodEntries(listOf(expectedFood))
        dao.restoreCustomFoods(listOf(CustomFoodEntity(9, "Food", 100_000, 10_000, 140)))
        dao.restoreSavedMeals(listOf(SavedMealEntity(10, "Meal", 150)))
        dao.restoreSavedMealItems(listOf(SavedMealItemEntity(10, 0, "rice", "Rice", 100, 130_000, 2_700)))
        dao.restorePreparedMeals(listOf(expectedPrepared))
        dao.restoreWeightEntries(listOf(WeightEntryEntity(11, 80.0, 160)))
        dao.restoreSettings(listOf(SettingsEntity(1, 2100, 170, 2300)))
        val (json, exportedCounts) = repository.exportJson()

        dao.clearSetCompletions(); dao.clearExerciseCompletions(); dao.clearPreparedMeals(); dao.clearSavedMealItems(); dao.clearSessions()
        dao.clearFoodEntries(); dao.clearCustomFoods(); dao.clearSavedMeals(); dao.clearWeightEntries(); dao.clearSettings()
        val restoredCounts = repository.importJson(json)

        assertEquals(exportedCounts, restoredCounts)
        assertEquals(58, restoredCounts.total)
        assertEquals(7, dao.allSessionsOnce().single().id)
        assertNull(dao.allSessionsOnce().single().completedAt)
        assertEquals(SetCompletionEntity(7, "chest-press", 0), dao.allSetCompletionsOnce().single())
        assertEquals(10, dao.allSavedMealItemsOnce().single().mealId)
        assertEquals(listOf(expectedFood), dao.allFoodEntriesOnce())
        assertEquals(listOf(expectedPrepared), dao.allPreparedMealsOnce())
    }

    @Test fun malformedImportDoesNotMutateExistingDatabase() = runBlocking {
        val dao = database.dao()
        dao.restoreSessions(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)))

        runCatching { repository.importJson("not-json") }

        assertEquals(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)), dao.allSessionsOnce())
    }

    @Test fun freshDatabaseExportContainsRequiredSeedsAndIsImportable() = runBlocking {
        val (json, counts) = repository.exportJson()

        val decoded = PortableBackupCodec().decodeAndValidate(json)
        val restoredCounts = repository.importJson(json)

        assertEquals(WorkoutPlanSeedData.ppl45v2.exercises.sortedBy { it.id }, decoded.exercises)
        assertEquals(
            WorkoutPlanSeedData.ppl45v2.prescriptions.sortedWith(compareBy({ it.dayId }, { it.position }, { it.id })),
            decoded.exercisePrescriptions,
        )
        assertEquals(1, decoded.settings.size)
        assertEquals(counts, restoredCounts)
    }

    @Test fun loweringPrescriptionSetsDoesNotMakeExportedV3BackupUnrestorable() = runBlocking {
        val dao = database.dao()
        dao.ensureSettings(SettingsEntity())
        dao.reconcileWorkoutSeeds()
        dao.restoreSessions(listOf(WorkoutSessionEntity(7, "push", "Push", 100, 200)))
        dao.restoreSetCompletions(listOf(SetCompletionEntity(7, "chest-press", 2)))
        dao.editPrescription("push-chest-press", 2, 6, 10, 120)

        val (json, exportedCounts) = repository.exportJson()
        val restoredCounts = repository.importJson(json)

        assertEquals(exportedCounts, restoredCounts)
        assertEquals(SetCompletionEntity(7, "chest-press", 2), dao.allSetCompletionsOnce().single())
        assertEquals(2, dao.prescription("push-chest-press")!!.sets)
        assertTrue(json.contains("\"version\":3"))
    }
}
