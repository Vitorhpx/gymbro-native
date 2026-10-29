package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedMealTest {
    @Test fun savedMealItemsProducePreciseFoodEntriesInSelectedSlot() {
        val meal = SavedMealWithItems(
            SavedMealEntity(id = 7, name = "Lunch prep", createdAt = 1),
            listOf(
                SavedMealItemEntity(mealId = 7, position = 0, foodId = "chicken", name = "Chicken", grams = 125, calorieMilliUnitsPer100g = 165_000, proteinMilligramsPer100g = 31_000),
                SavedMealItemEntity(mealId = 7, position = 1, foodId = "rice", name = "Rice", grams = 150, calorieMilliUnitsPer100g = 130_000, proteinMilligramsPer100g = 2_700),
            ),
        )

        val entries = meal.toFoodEntries(MealSlot.LUNCH, loggedAt = 99)

        assertEquals(listOf(MealSlot.LUNCH, MealSlot.LUNCH), entries.map { it.mealSlot })
        assertEquals(listOf(206_250L, 195_000L), entries.map { it.calorieMilliUnits })
        assertEquals(401, entries.totalCalories())
        assertEquals(43, entries.totalProteinGrams())
    }

    @Test fun customFoodConvertsToLogWithoutRoundingEachRow() {
        val food = CustomFoodEntity(id = 3, name = "Yogurt", calorieMilliUnitsPer100g = 58_750, proteinMilligramsPer100g = 10_250, createdAt = 1)
        val entry = FoodEntryEntity.from(food, grams = 17, mealSlot = MealSlot.BREAKFAST, loggedAt = 2)

        assertEquals(9_988L, entry.calorieMilliUnits)
        assertEquals(1_743L, entry.proteinMilligrams)
        assertEquals(MealSlot.BREAKFAST, entry.mealSlot)
    }
}
