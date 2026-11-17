package com.gymbro.local.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTimerTest {
    @Test fun `elapsed workout time starts at zero and includes seconds`() {
        assertEquals("00:00", formatWorkoutElapsed(startedAt = 1_000, now = 1_000))
        assertEquals("00:01", formatWorkoutElapsed(startedAt = 1_000, now = 2_000))
        assertEquals("01:05", formatWorkoutElapsed(startedAt = 1_000, now = 66_000))
    }

    @Test fun `elapsed workout time includes hours without wrapping`() {
        assertEquals("1:02:03", formatWorkoutElapsed(startedAt = 1_000, now = 3_724_000))
    }

    @Test fun `elapsed workout time never becomes negative after a clock correction`() {
        assertEquals("00:00", formatWorkoutElapsed(startedAt = 2_000, now = 1_000))
    }

    @Test fun `timer accessibility label identifies the live workout duration`() {
        assertEquals("Workout time 01:05", workoutTimerAccessibilityLabel("01:05"))
    }
}
