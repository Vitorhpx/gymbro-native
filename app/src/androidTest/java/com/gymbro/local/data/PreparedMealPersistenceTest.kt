package com.gymbro.local.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparedMealPersistenceTest {
    private lateinit var database: GymBroDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymBroDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun foodLogsCaptureTheEffectiveDailyTargetForStableHistory() = runBlocking {
        val dao = database.dao()
        dao.saveSettings(SettingsEntity(calorieGoal = 2_000, proteinGoal = 160, weekendCalorieGoal = 2_300))
        val monday = java.time.LocalDate.of(2026, 10, 26)
        val saturday = monday.plusDays(5)
        dao.insertFoodWithTarget(FoodEntryEntity(0, "weekday", "Weekday", 100, 1, 1, 1, loggedDateEpochDay = monday.toEpochDay()))
        dao.insertFoodWithTarget(FoodEntryEntity(0, "weekend", "Weekend", 100, 1, 1, 2, loggedDateEpochDay = saturday.toEpochDay()))

        val targets = dao.allFoodEntriesOnce().associate { it.foodId to it.calorieTarget }
        assertEquals(2_000, targets.getValue("weekday"))
        assertEquals(2_300, targets.getValue("weekend"))
    }

    @Test fun loggingAndExtraLoggingAreAtomicAndPreservePreciseNutrition() = runBlocking {
        val dao = database.dao()
        val recipeId = dao.createSavedMeal(
            SavedMealEntity(name = "Lunch", createdAt = 1),
            listOf(SavedMealItemEntity(0, 0, "rice", "Rice", 17, 58_750, 10_250)),
        )
        dao.createPreparedMeal(PreparedMealEntity("prepared-stable", recipeId, 20_388, 1, 0, false, 2))

        val monday = java.time.LocalDate.ofEpochDay(20_388)
        fun noon(day: java.time.LocalDate) = day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.logPreparedPortion("prepared-stable", MealSlot.LUNCH, noon(monday), monday.toEpochDay())
        val exhausted = dao.preparedMeal("prepared-stable")!!
        assertEquals(0, exhausted.remainingPortions)
        assertEquals(9_988L, dao.allFoodEntriesOnce().single().calorieMilliUnits)

        val failure = runCatching {
            dao.logPreparedPortion("prepared-stable", MealSlot.DINNER, noon(monday.plusDays(1)), monday.plusDays(1).toEpochDay())
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(1, dao.allFoodEntriesOnce().size)
        assertEquals(1, dao.preparedMeal("prepared-stable")!!.loggedPortions)

        dao.logPreparedPortion(
            "prepared-stable", MealSlot.DINNER, noon(monday.plusDays(2)), monday.plusDays(2).toEpochDay(), addExtra = true,
        )
        val afterExtra = dao.preparedMeal("prepared-stable")!!
        assertEquals(2, afterExtra.plannedPortions)
        assertEquals(2, afterExtra.loggedPortions)
        assertEquals(0, afterExtra.remainingPortions)
        assertEquals(listOf(MealSlot.LUNCH, MealSlot.DINNER), dao.allFoodEntriesOnce().map { it.mealSlot })
        assertEquals(listOf("prepared-stable", "prepared-stable"), dao.allFoodEntriesOnce().map { it.preparedMealId })
        assertEquals(2, dao.allFoodEntriesOnce().map { it.preparedLogId }.distinct().size)
    }

    @Test fun preparedLogUsesOneDurableGroupForAllIngredientsAndCannotBeDeleted() = runBlocking {
        val dao = database.dao()
        val recipeId = dao.createSavedMeal(
            SavedMealEntity(name = "Lunch", createdAt = 1),
            listOf(
                SavedMealItemEntity(0, 0, "rice", "Rice", 100, 130_000, 2_700),
                SavedMealItemEntity(0, 1, "beans", "Beans", 100, 120_000, 8_000),
            ),
        )
        val monday = java.time.LocalDate.of(2026, 10, 26)
        dao.createPreparedMeal(PreparedMealEntity("prepared-stable", recipeId, monday.toEpochDay(), 2, 0, false, 2))
        val loggedAt = monday.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.logPreparedPortion("prepared-stable", MealSlot.LUNCH, loggedAt, monday.toEpochDay())

        val rows = dao.allFoodEntriesOnce()
        assertEquals(2, rows.size)
        assertEquals(setOf("prepared-stable"), rows.map { it.preparedMealId }.toSet())
        assertEquals(1, rows.map { it.preparedLogId }.distinct().size)
        assertEquals(setOf(monday.toEpochDay()), rows.map { it.loggedDateEpochDay }.toSet())
        assertEquals(0, dao.deleteFood(rows.first().id))
        assertEquals(2, dao.allFoodEntriesOnce().size)
    }

    @Test fun loggingOutsidePoolWeekAndStaleArchiveAreSafelyRejected() = runBlocking {
        val dao = database.dao()
        val recipeId = dao.createSavedMeal(
            SavedMealEntity(name = "Lunch", createdAt = 1),
            listOf(SavedMealItemEntity(0, 0, "rice", "Rice", 100, 130_000, 2_700)),
        )
        val monday = java.time.LocalDate.of(2026, 10, 26)
        dao.createPreparedMeal(PreparedMealEntity("prepared-stable", recipeId, monday.toEpochDay(), 1, 0, false, 2))
        val nextMonday = monday.plusDays(7).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertTrue(runCatching {
            dao.logPreparedPortion("prepared-stable", MealSlot.LUNCH, nextMonday, monday.plusDays(7).toEpochDay())
        }.exceptionOrNull() is IllegalArgumentException)
        dao.archivePreparedMeal("prepared-stable")
        assertTrue(runCatching { dao.archivePreparedMeal("prepared-stable") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, dao.allFoodEntriesOnce().size)
    }

    @Test fun preparedMealsAreObservableWithStableIdentity() = runBlocking {
        val dao = database.dao()
        val recipeId = dao.createSavedMeal(
            SavedMealEntity(name = "Lunch", createdAt = 1),
            listOf(SavedMealItemEntity(0, 0, "rice", "Rice", 100, 130_000, 2_700)),
        )
        dao.createPreparedMeal(PreparedMealEntity("prepared-stable", recipeId, 20_388, 4, 0, false, 2))
        assertEquals("prepared-stable", dao.preparedMeals().first().single().id)
    }
}
