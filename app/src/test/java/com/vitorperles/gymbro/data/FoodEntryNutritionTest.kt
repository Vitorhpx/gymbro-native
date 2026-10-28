package com.vitorperles.gymbro.data

import com.vitorperles.gymbro.domain.SeedData
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodEntryNutritionTest {
    @Test
    fun repeatedSmallEntriesRetainPrecisionUntilTotalsAreRounded() {
        val chicken = SeedData.foods.single { it.id == "chicken" }
        val entries = List(100) { index ->
            FoodEntryEntity.from(chicken, grams = 1, loggedAt = index.toLong())
        }

        assertEquals(1_650L, entries.first().calorieMilliUnits)
        assertEquals(310L, entries.first().proteinMilligrams)
        assertEquals(165, entries.totalCalories())
        assertEquals(31, entries.totalProteinGrams())
        assertEquals(2, entries.first().displayCalories())
        assertEquals("0.3", entries.first().displayProteinGrams())
    }
}
