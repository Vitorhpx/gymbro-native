package com.gymbro.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedMealActionTest {
    @Test fun `expected guarded update failure becomes visible status`() = runTest {
        val status = runPreparedMealAction { throw IllegalArgumentException("No prepared portions remain.") }

        assertEquals(PreparedMealActionStatus.Error("No prepared portions remain."), status)
    }

    @Test fun `successful guarded update becomes visible status`() = runTest {
        val status = runPreparedMealAction { }

        assertEquals(PreparedMealActionStatus.Success("Prepared meal updated."), status)
    }
}
