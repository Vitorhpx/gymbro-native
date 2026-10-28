package com.gymbro.local

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.GymBroDatabase
import com.gymbro.local.data.SetCompletionEntity
import com.gymbro.local.data.SettingsEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.domain.FoodPreset
import com.gymbro.local.network.FoodSearchService
import com.gymbro.local.network.FoodSearchValidation
import com.gymbro.local.network.HttpUsdaFoodSearchService
import com.gymbro.local.network.UsdaFood
import com.gymbro.local.update.AppRelease
import com.gymbro.local.update.AppUpdateService
import com.gymbro.local.update.ApkIntegrity
import com.gymbro.local.update.GitHubAppUpdateService
import com.gymbro.local.update.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface FoodSearchUiState {
    data object Idle : FoodSearchUiState
    data object Loading : FoodSearchUiState
    data class Results(val foods: List<UsdaFood>) : FoodSearchUiState
    data class Error(val message: String) : FoodSearchUiState
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data class Available(val release: AppRelease) : UpdateUiState
    data object Downloading : UpdateUiState
    data class Downloaded(val file: File, val integrity: ApkIntegrity) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

object UpdateUiCopy {
    fun downloadButton(release: AppRelease): String =
        if (release.apk?.sha256 == null) "Download source-validated APK" else "Download APK"

    fun downloadedMessage(downloaded: UpdateUiState.Downloaded): String = when (downloaded.integrity) {
        ApkIntegrity.SHA256_VERIFIED ->
            "APK downloaded and SHA-256 verified. Android will ask you to confirm installation."
        ApkIntegrity.STRUCTURE_VALIDATED ->
            "APK downloaded and structurally validated from the approved GitHub source. Android will ask you to confirm installation."
    }
}

class MainViewModel(
    application: Application,
    private val foodSearchService: FoodSearchService,
    private val updateService: AppUpdateService,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        foodSearchService = HttpUsdaFoodSearchService(),
        updateService = GitHubAppUpdateService(),
    )

    private val dao = GymBroDatabase.get(application).dao()
    private val preferences = application.getSharedPreferences(PRIVATE_SETTINGS, Context.MODE_PRIVATE)
    val active = dao.activeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completions = dao.completions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foodEntries = dao.foodEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val weights = dao.weightEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = dao.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val foodSearchCoordinator = FoodSearchCoordinator(viewModelScope) { query ->
        val key = preferences.getString(USDA_KEY, null)?.takeIf(String::isNotBlank) ?: DEMO_KEY
        withContext(Dispatchers.IO) { foodSearchService.search(query, key) }
    }
    val foodSearch = foodSearchCoordinator.state
    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update = _update.asStateFlow()
    private val _hasPersonalUsdaKey = MutableStateFlow(preferences.contains(USDA_KEY))
    val hasPersonalUsdaKey = _hasPersonalUsdaKey.asStateFlow()

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
        if (grams !in 1..5000) return@launch
        dao.insertFood(FoodEntryEntity.from(food, grams, System.currentTimeMillis()))
    }

    fun logFood(food: UsdaFood, grams: Int) = viewModelScope.launch {
        if (grams !in 1..5000) return@launch
        dao.insertFood(FoodEntryEntity.from(food, grams, System.currentTimeMillis()))
    }

    fun searchFood(query: String) {
        FoodSearchValidation.queryError(query)?.let { foodSearchCoordinator.reject(it); return }
        foodSearchCoordinator.search(query.trim())
    }

    fun clearFoodSearch() = foodSearchCoordinator.clear()
    fun saveUsdaKey(value: String) {
        val clean = value.trim()
        preferences.edit().apply { if (clean.isEmpty()) remove(USDA_KEY) else putString(USDA_KEY, clean) }.apply()
        _hasPersonalUsdaKey.value = clean.isNotEmpty()
    }

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = UpdateUiState.Checking
        _update.value = try {
            val release = withContext(Dispatchers.IO) { updateService.latestRelease() }
            if (release.apk != null && SemVer.isNewer(release.tagName, BuildConfig.VERSION_NAME)) UpdateUiState.Available(release)
            else UpdateUiState.Current
        } catch (e: Exception) { UpdateUiState.Error(e.message ?: "Update check failed.") }
    }

    fun downloadUpdate(release: AppRelease) = viewModelScope.launch {
        val asset = release.apk ?: return@launch
        _update.value = UpdateUiState.Downloading
        _update.value = try {
            val directory = File(getApplication<Application>().cacheDir, "updates")
            val destination = File(directory, "gymbro-update.apk")
            val downloaded = withContext(Dispatchers.IO) { updateService.download(asset, destination) }
            UpdateUiState.Downloaded(downloaded.file, downloaded.integrity)
        } catch (e: Exception) { UpdateUiState.Error(e.message ?: "APK download failed.") }
    }

    fun deleteFood(id: Long) = viewModelScope.launch { dao.deleteFood(id) }
    fun logWeight(kg: Double) = viewModelScope.launch {
        if (kg in 25.0..400.0) dao.insertWeight(WeightEntryEntity(kilograms = kg, loggedAt = System.currentTimeMillis()))
    }
    fun saveGoals(calories: Int, protein: Int) = viewModelScope.launch {
        if (calories in 800..6000 && protein in 20..400) dao.saveSettings(SettingsEntity(calorieGoal = calories, proteinGoal = protein))
    }

    companion object {
        private const val PRIVATE_SETTINGS = "private_settings"
        private const val USDA_KEY = "usda_api_key"
        const val DEMO_KEY = "DEMO_KEY"
    }
}
