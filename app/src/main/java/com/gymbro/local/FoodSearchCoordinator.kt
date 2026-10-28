package com.gymbro.local

import com.gymbro.local.network.UsdaFood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FoodSearchCoordinator(
    private val scope: CoroutineScope,
    private val searchFoods: suspend (String) -> List<UsdaFood>,
) {
    private val mutableState = MutableStateFlow<FoodSearchUiState>(FoodSearchUiState.Idle)
    val state = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L

    fun search(query: String) {
        searchJob?.cancel()
        val requestGeneration = ++generation
        searchJob = scope.launch {
            mutableState.value = FoodSearchUiState.Loading
            val result = try {
                FoodSearchUiState.Results(searchFoods(query))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                FoodSearchUiState.Error(failure.message ?: "Food search failed.")
            }
            if (requestGeneration == generation) mutableState.value = result
        }
    }

    fun reject(message: String) {
        searchJob?.cancel()
        generation++
        mutableState.value = FoodSearchUiState.Error(message)
    }

    fun clear() {
        searchJob?.cancel()
        generation++
        mutableState.value = FoodSearchUiState.Idle
    }
}
