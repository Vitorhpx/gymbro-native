package com.vitorperles.gymbro.domain

import kotlin.math.max
import kotlin.math.roundToInt

data class FoodPreset(val id: String, val name: String, val caloriesPer100g: Double, val proteinPer100g: Double)
data class FoodLog(val food: FoodPreset, val grams: Int)
data class NutritionTotals(
    val calories: Int,
    val proteinGrams: Int,
    val caloriesRemaining: Int,
    val proteinRemaining: Int,
)

object NutritionCalculator {
    fun totals(logs: List<FoodLog>, calorieGoal: Int, proteinGoal: Int): NutritionTotals {
        val calories = logs.sumOf { it.food.caloriesPer100g * it.grams / 100.0 }.roundToInt()
        val protein = logs.sumOf { it.food.proteinPer100g * it.grams / 100.0 }.roundToInt()
        return NutritionTotals(calories, protein, max(0, calorieGoal - calories), max(0, proteinGoal - protein))
    }
}
