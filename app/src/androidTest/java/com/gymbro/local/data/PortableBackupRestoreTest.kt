package com.gymbro.local.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        dao.restoreSessions(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)))
        dao.restoreSetCompletions(listOf(SetCompletionEntity(7, "bench", 0)))
        dao.restoreExerciseCompletions(listOf(ExerciseCompletionEntity(7, "bench", 50_000, 8, 120)))
        dao.restoreFoodEntries(listOf(FoodEntryEntity(8, "rice", "Rice", 100, 130_000, 2_700, 130, MealSlot.LUNCH)))
        dao.restoreCustomFoods(listOf(CustomFoodEntity(9, "Food", 100_000, 10_000, 140)))
        dao.restoreSavedMeals(listOf(SavedMealEntity(10, "Meal", 150)))
        dao.restoreSavedMealItems(listOf(SavedMealItemEntity(10, 0, "rice", "Rice", 100, 130_000, 2_700)))
        dao.restoreWeightEntries(listOf(WeightEntryEntity(11, 80.0, 160)))
        dao.restoreSettings(listOf(SettingsEntity(1, 2100, 170)))
        val (json, exportedCounts) = repository.exportJson()

        dao.clearSetCompletions(); dao.clearExerciseCompletions(); dao.clearSavedMealItems(); dao.clearSessions()
        dao.clearFoodEntries(); dao.clearCustomFoods(); dao.clearSavedMeals(); dao.clearWeightEntries(); dao.clearSettings()
        val restoredCounts = repository.importJson(json)

        assertEquals(exportedCounts, restoredCounts)
        assertEquals(9, restoredCounts.total)
        assertEquals(7, dao.allSessionsOnce().single().id)
        assertNull(dao.allSessionsOnce().single().completedAt)
        assertEquals(SetCompletionEntity(7, "bench", 0), dao.allSetCompletionsOnce().single())
        assertEquals(10, dao.allSavedMealItemsOnce().single().mealId)
    }

    @Test fun malformedImportDoesNotMutateExistingDatabase() = runBlocking {
        val dao = database.dao()
        dao.restoreSessions(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)))

        runCatching { repository.importJson("not-json") }

        assertEquals(listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)), dao.allSessionsOnce())
    }
}
