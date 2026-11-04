package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSwapTest {
    @Test fun `every planned exercise has a valid seeded alternative`() {
        val seed = WorkoutPlanSeedData.ppl45v2
        val exerciseIds = seed.exercises.mapTo(mutableSetOf()) { it.id }
        val plannedExerciseIds = seed.prescriptions.mapTo(mutableSetOf()) { it.exerciseId }

        assertEquals(plannedExerciseIds, seed.swapOptions.keys)
        seed.swapOptions.forEach { (originalId, alternatives) ->
            val dayId = seed.prescriptions.single { it.exerciseId == originalId }.dayId
            val sameDayExerciseIds = seed.prescriptions.filter { it.dayId == dayId }.mapTo(mutableSetOf()) { it.exerciseId }
            assertTrue("$originalId has no alternative", alternatives.isNotEmpty())
            assertTrue("$originalId contains itself", originalId !in alternatives)
            assertTrue("$originalId references an unknown alternative", alternatives.all { it in exerciseIds })
            assertTrue("$originalId has no alternative outside its workout day", alternatives.any { it !in sameDayExerciseIds })
        }
    }

    @Test fun `a swap cannot reuse another slot's active exercise`() {
        val planned = listOf("smith-squat", "smith-rdl", "hip-thrust", "hip-abductor", "calf-raise")
        val swaps = mapOf("hip-thrust" to "leg-press")

        assertFalse(isSwapExerciseAvailable("smith-squat", "leg-press", planned, swaps))
        assertTrue(isSwapExerciseAvailable("hip-thrust", "leg-press", planned, swaps))
        assertTrue(isSwapExerciseAvailable("smith-rdl", "leg-curl-machine", planned, swaps))
    }
}