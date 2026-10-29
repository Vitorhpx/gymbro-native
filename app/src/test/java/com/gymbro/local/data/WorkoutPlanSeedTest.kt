package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanSeedTest {
    @Test fun `canonical PPL seed has stable ids and complete V2 library and prescriptions`() {
        val seed = WorkoutPlanSeedData.ppl45v2

        assertEquals("gymbro-ppl-45-v2", seed.plan.id)
        assertEquals(listOf("push", "pull", "legs"), seed.days.map { it.id })
        assertEquals(29, seed.exercises.size)
        assertEquals(
            listOf("chest-press", "incline-press", "shoulder-press", "lateral-raise", "triceps-pushdown"),
            seed.prescriptions.filter { it.dayId == "push" }.sortedBy { it.position }.map { it.exerciseId },
        )
        assertEquals("Rope Triceps Pushdown", seed.exercises.single { it.id == "triceps-pushdown" }.name)
        assertTrue(seed.exercises.none { it.id == "rope-triceps-pushdown" })
        assertEquals(
            ExercisePrescriptionEntity("legs-hip-abductor", "legs", "hip-abductor", 3, 2, 12, 20, 60, true),
            seed.prescriptions.single { it.dayId == "legs" && it.exerciseId == "hip-abductor" },
        )
    }

    @Test fun `untouched recognized seed refreshes but edited record is preserved`() {
        val oldCanonical = ExerciseEntity("stable", "Old seed", "back", "machine", true)
        val newCanonical = oldCanonical.copy(name = "New seed")

        assertEquals(newCanonical, refreshSeedRecord(oldCanonical, oldCanonical, newCanonical))
        assertEquals(
            oldCanonical.copy(name = "My edit", isSeeded = false),
            refreshSeedRecord(oldCanonical.copy(name = "My edit", isSeeded = false), oldCanonical, newCanonical),
        )
        assertEquals(newCanonical, refreshSeedRecord(null, oldCanonical, newCanonical))
    }

    @Test fun `prescription edit and reorder mark recognized seed as user edited`() {
        val original = WorkoutPlanSeedData.ppl45v2.prescriptions.first()

        val edited = original.userEdited(sets = 4, minReps = 5, maxReps = 8, restSeconds = 150, position = 2)

        assertEquals(4, edited.sets)
        assertEquals(5, edited.minReps)
        assertEquals(8, edited.maxReps)
        assertEquals(150, edited.restSeconds)
        assertEquals(2, edited.position)
        assertFalse(edited.isSeeded)
        assertTrue(original.isSeeded)
    }
}
