package com.gymbro.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.GymBroDatabase
import com.gymbro.local.data.SetCompletionEntity
import com.gymbro.local.data.SettingsEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.domain.FoodPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GymBroDatabase.get(application).dao()
    val active = dao.activeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completions = dao.completions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foodEntries = dao.foodEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val weights = dao.weightEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = dao.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { viewModelScope.launch { dao.ensureSettings(SettingsEntity()) } }

    fun startWorkout(dayId: String, dayName: String) = viewModelScope.launch {
        dao.startSessionIfNone(WorkoutSessionEntity(dayId = dayId, dayName = dayName, startedAt = System.currentTimeMillis()))
    }

    fun setChecked(sessionId: Long, exerciseId: String, index: Int, checked: Boolean) = viewModelScope.launch {
        if (checked) dao.checkSet(SetCompletionEntity(sessionId, exerciseId, index))
        else dao.uncheckSet(sessionId, exerciseId, index)
    }

    fun completeWorkout(id: Long) = viewModelScope.launch { dao.completeSession(id, System.currentTimeMillis()) }

    fun logFood(food: FoodPreset, grams: Int) = viewModelScope.launch {
        if (grams <= 0) return@launch
        dao.insertFood(FoodEntryEntity.from(food, grams, System.currentTimeMillis()))
    }

    fun deleteFood(id: Long) = viewModelScope.launch { dao.deleteFood(id) }
    fun logWeight(kg: Double) = viewModelScope.launch {
        if (kg in 25.0..400.0) dao.insertWeight(WeightEntryEntity(kilograms = kg, loggedAt = System.currentTimeMillis()))
    }
    fun saveGoals(calories: Int, protein: Int) = viewModelScope.launch {
        if (calories in 800..6000 && protein in 20..400) dao.saveSettings(SettingsEntity(calorieGoal = calories, proteinGoal = protein))
    }
}
