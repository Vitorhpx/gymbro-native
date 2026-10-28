package com.gymbro.local.network

import com.gymbro.local.data.FoodEntryEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdaFoodSearchTest {
    private val fixture = """
        {"foods":[
          {"fdcId":171077,"description":"Chicken breast, roasted","foodNutrients":[
            {"nutrientId":1008,"nutrientName":"Energy","unitName":"KCAL","value":165.0},
            {"nutrientId":1003,"nutrientName":"Protein","unitName":"G","value":31.02}
          ]},
          {"fdcId":999,"description":"Missing energy","foodNutrients":[
            {"nutrientId":1003,"nutrientName":"Protein","unitName":"G","value":5.0}
          ]}
        ]}
    """.trimIndent()

    @Test fun `parser maps complete foods and skips incomplete foods`() {
        val foods = UsdaFoodParser(Json { ignoreUnknownKeys = true }).parse(fixture)
        assertEquals(listOf(UsdaFood(171077, "Chicken breast, roasted", 165.0, 31.02)), foods)
    }

    @Test fun `USDA food scales to precise persisted integer nutrition`() {
        val entry = FoodEntryEntity.from(UsdaFood(171077, "Chicken breast", 165.0, 31.02), 37, 123L)
        assertEquals("usda-171077", entry.foodId)
        assertEquals(61_050L, entry.calorieMilliUnits)
        assertEquals(11_477L, entry.proteinMilligrams)
    }

    @Test fun `search request validates query and grams bounds`() {
        assertEquals("Enter at least 2 characters.", FoodSearchValidation.queryError("a"))
        assertEquals(null, FoodSearchValidation.queryError(" chicken "))
        assertEquals("Enter 1–5000 g.", FoodSearchValidation.gramsError("0"))
        assertEquals("Enter 1–5000 g.", FoodSearchValidation.gramsError("5001"))
        assertEquals(null, FoodSearchValidation.gramsError("250"))
    }

    @Test fun `parser reports malformed responses`() {
        val result = runCatching { UsdaFoodParser(Json).parse("not-json") }
        assertTrue(result.exceptionOrNull() is FoodSearchException)
    }
}
