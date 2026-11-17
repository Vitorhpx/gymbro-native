package com.gymbro.local.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutNotificationSpecTest {
    @Test fun `active workout notification is ongoing and uses the session start as a chronometer`() {
        val spec = workoutNotificationSpec(startedAt = 12_345L)

        assertEquals("Workout in progress", spec.title)
        assertEquals("Tap to return to GymBro", spec.text)
        assertEquals(12_345L, spec.whenMillis)
        assertTrue(spec.usesChronometer)
        assertTrue(spec.ongoing)
    }
}
