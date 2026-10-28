package com.gymbro.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymBroDomainTest {
    @Test
    fun seededPlanHasStablePplPrescriptionAndGluteFocusedLegs() {
        val plan = SeedData.plan

        assertEquals("gymbro-ppl-45-v2", plan.id)
        assertEquals("GymBro PPL 45 V2", plan.name)
        assertEquals(listOf("Push", "Pull", "Legs"), plan.days.map { it.name })
        assertEquals(15, plan.days.sumOf { it.exercises.size })
        val legs = plan.days.single { it.name == "Legs" }
        assertEquals(
            listOf("Smith Squat", "Smith RDL", "Smith Hip Thrust", "Hip Abductor Machine", "Calf Raise Machine"),
            legs.exercises.map { it.name },
        )
        assertTrue(legs.exercises.single { it.name == "Hip Abductor Machine" }.reps.contains("12"))
    }

    @Test
    fun nutritionTotalsScalePresetFoodsByGramsAndClampRemainingAtZero() {
        val foods = listOf(
            FoodLog(SeedData.foods.single { it.id == "chicken" }, 150),
            FoodLog(SeedData.foods.single { it.id == "rice" }, 200),
        )

        val totals = NutritionCalculator.totals(foods, calorieGoal = 500, proteinGoal = 60)

        assertEquals(508, totals.calories)
        assertEquals(52, totals.proteinGrams)
        assertEquals(0, totals.caloriesRemaining)
        assertEquals(8, totals.proteinRemaining)
    }

    @Test
    fun recommendsNextPplDayFromThisWeeksCompletedSessions() {
        assertEquals("Push", WorkoutRecommendation.nextDay(emptyList()).name)
        assertEquals("Pull", WorkoutRecommendation.nextDay(listOf("push")).name)
        assertEquals("Legs", WorkoutRecommendation.nextDay(listOf("push", "pull")).name)
        assertEquals("Push", WorkoutRecommendation.nextDay(listOf("push", "pull", "legs")).name)
    }
}
