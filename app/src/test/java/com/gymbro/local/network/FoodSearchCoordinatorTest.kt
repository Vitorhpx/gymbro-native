package com.gymbro.local.network

import com.gymbro.local.FoodSearchUiState
import com.gymbro.local.FoodSearchCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodSearchCoordinatorTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `slower first search cannot replace faster second result`() = runTest {
        val slow = UsdaFood(1, "slow", 1.0, 1.0)
        val fast = UsdaFood(2, "fast", 2.0, 2.0)
        val coordinator = FoodSearchCoordinator(this) { query ->
            if (query == "slow") {
                withContext(NonCancellable) { delay(1_000) }
                listOf(slow)
            } else {
                delay(10)
                listOf(fast)
            }
        }

        coordinator.search("slow")
        runCurrent()
        coordinator.search("fast")
        advanceUntilIdle()

        assertEquals(FoodSearchUiState.Results(listOf(fast)), coordinator.state.value)
    }
}