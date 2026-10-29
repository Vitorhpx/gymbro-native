package com.gymbro.local

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymbro.local.data.CustomFoodEntity
import com.gymbro.local.data.ExerciseCompletionEntity
import com.gymbro.local.data.ExerciseEntity
import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.GymBroDatabase
import com.gymbro.local.data.MealSlot
import com.gymbro.local.data.PortableBackupRepository
import com.gymbro.local.data.PreparedMealEntity
import com.gymbro.local.data.decodeStrictUtf8
import com.gymbro.local.data.SavedMealEntity
import com.gymbro.local.data.SavedMealItemEntity
import com.gymbro.local.data.SavedMealWithItems
import com.gymbro.local.data.SettingsEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.domain.FoodPreset
import com.gymbro.local.network.BarcodeProduct
import com.gymbro.local.network.BarcodeProductResolver
import com.gymbro.local.network.FoodSearchService
import com.gymbro.local.network.FoodSearchValidation
import com.gymbro.local.network.HttpOpenFoodFactsResolver
import com.gymbro.local.network.HttpUsdaFoodSearchService
import com.gymbro.local.network.UsdaFood
import com.gymbro.local.update.ApkIntegrity
import com.gymbro.local.update.AppRelease
import com.gymbro.local.update.AppUpdateService
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
import java.util.UUID
import kotlin.math.roundToLong

sealed interface FoodSearchUiState {
    data object Idle : FoodSearchUiState
    data object Loading : FoodSearchUiState
    data class Results(val foods: List<UsdaFood>) : FoodSearchUiState
    data class Error(val message: String) : FoodSearchUiState
}

sealed interface BarcodeUiState {
    data object Idle : BarcodeUiState
    data object Loading : BarcodeUiState
    data class Found(val product: BarcodeProduct) : BarcodeUiState
    data class Error(val message: String) : BarcodeUiState
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

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

sealed interface PreparedMealActionStatus {
    data object Idle : PreparedMealActionStatus
    data class Success(val message: String) : PreparedMealActionStatus
    data class Error(val message: String) : PreparedMealActionStatus
}

internal suspend fun runPreparedMealAction(action: suspend () -> Unit): PreparedMealActionStatus = try {
    action()
    PreparedMealActionStatus.Success("Prepared meal updated.")
} catch (error: IllegalArgumentException) {
    PreparedMealActionStatus.Error(error.message ?: "Prepared meal changed; refresh and try again.")
}

object UpdateUiCopy {
    fun downloadButton(release: AppRelease): String = if (release.apk?.sha256 == null) "Download source-validated APK" else "Download APK"
    fun downloadedMessage(downloaded: UpdateUiState.Downloaded): String = when (downloaded.integrity) {
        ApkIntegrity.SHA256_VERIFIED -> "APK downloaded and SHA-256 verified. Android will ask you to confirm installation."
        ApkIntegrity.STRUCTURE_VALIDATED -> "APK downloaded and structurally validated from the approved GitHub source. Android will ask you to confirm installation."
    }
}

class MainViewModel(
    application: Application,
    private val foodSearchService: FoodSearchService,
    private val updateService: AppUpdateService,
    private val barcodeResolver: BarcodeProductResolver = HttpOpenFoodFactsResolver(),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, HttpUsdaFoodSearchService(), GitHubAppUpdateService(), HttpOpenFoodFactsResolver())

    private val database = GymBroDatabase.get(application)
    private val dao = database.dao()
    private val backupRepository = PortableBackupRepository(database)
    private val preferences = application.getSharedPreferences(PRIVATE_SETTINGS, Context.MODE_PRIVATE)
    val active = dao.activeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exerciseCompletions = dao.exerciseCompletions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foodEntries = dao.foodEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customFoods = dao.customFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedMeals = dao.savedMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preparedMeals = dao.preparedMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val weights = dao.weightEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = dao.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val exercises = dao.exercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val planExercises = dao.planExercises("gymbro-ppl-45-v2").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val foodSearchCoordinator = FoodSearchCoordinator(viewModelScope) { query ->
        val key = preferences.getString(USDA_KEY, null)?.takeIf(String::isNotBlank) ?: DEMO_KEY
        withContext(Dispatchers.IO) { foodSearchService.search(query, key) }
    }
    val foodSearch = foodSearchCoordinator.state
    private val _barcode = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Idle)
    val barcode = _barcode.asStateFlow()
    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update = _update.asStateFlow()
    private val _backup = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backup = _backup.asStateFlow()
    private val _preparedMealAction = MutableStateFlow<PreparedMealActionStatus>(PreparedMealActionStatus.Idle)
    val preparedMealAction = _preparedMealAction.asStateFlow()
    private val _hasPersonalUsdaKey = MutableStateFlow(preferences.contains(USDA_KEY))
    val hasPersonalUsdaKey = _hasPersonalUsdaKey.asStateFlow()

    init { viewModelScope.launch { dao.ensureSettings(SettingsEntity()); dao.reconcileWorkoutSeeds() } }

    fun startWorkout(dayId: String, dayName: String) = viewModelScope.launch {
        dao.startSessionIfNone(WorkoutSessionEntity(dayId = dayId, dayName = dayName, startedAt = System.currentTimeMillis()))
    }
    fun setExerciseCompleted(sessionId: Long, exerciseId: String, weight: Double, reps: Int, completed: Boolean) = viewModelScope.launch {
        if (completed) {
            if (weight !in 0.0..5000.0 || reps !in 1..100) return@launch
            dao.completeExercise(ExerciseCompletionEntity(sessionId, exerciseId, (weight * 1000).roundToLong(), reps, System.currentTimeMillis()))
        } else dao.uncompleteExercise(sessionId, exerciseId)
    }
    fun completeWorkout(id: Long) = viewModelScope.launch { dao.completeSession(id, System.currentTimeMillis()) }
    fun createExercise(name: String, muscleGroup: String, category: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.length !in 2..100) return@launch
        dao.upsertExercise(ExerciseEntity("custom-${UUID.randomUUID()}", clean, muscleGroup.trim().ifBlank { "other" }, category.trim().ifBlank { "other" }, false))
    }
    fun editPrescription(id: String, sets: Int, minReps: Int, maxReps: Int, restSeconds: Int) = viewModelScope.launch {
        if (sets in 1..20 && minReps in 1..100 && maxReps in minReps..100 && restSeconds in 0..1800) {
            dao.editPrescription(id, sets, minReps, maxReps, restSeconds)
        }
    }
    fun moveExercise(dayId: String, prescriptionId: String, direction: Int) = viewModelScope.launch {
        dao.movePrescription(dayId, prescriptionId, direction)
    }

    fun logFood(food: FoodPreset, grams: Int, slot: MealSlot = MealSlot.SNACK) = viewModelScope.launch {
        if (grams in 1..5000) dao.insertFood(FoodEntryEntity.from(food, grams, System.currentTimeMillis(), slot))
    }
    fun logFood(food: UsdaFood, grams: Int, slot: MealSlot = MealSlot.SNACK) = viewModelScope.launch {
        if (grams in 1..5000) dao.insertFood(FoodEntryEntity.from(food, grams, System.currentTimeMillis(), slot))
    }
    fun createCustomFood(name: String, caloriesPer100g: Double, proteinPer100g: Double) = viewModelScope.launch {
        if (name.trim().length !in 2..100 || caloriesPer100g !in 0.0..2000.0 || proteinPer100g !in 0.0..100.0) return@launch
        dao.insertCustomFood(CustomFoodEntity(name = name.trim(), calorieMilliUnitsPer100g = (caloriesPer100g * 1000).roundToLong(), proteinMilligramsPer100g = (proteinPer100g * 1000).roundToLong(), createdAt = System.currentTimeMillis()))
    }
    fun logFood(food: CustomFoodEntity, grams: Int, slot: MealSlot) = viewModelScope.launch {
        if (grams in 1..5000) dao.insertFood(FoodEntryEntity.from(food, grams, slot, System.currentTimeMillis()))
    }
    fun createSavedMeal(name: String, items: List<SavedMealItemEntity>) = viewModelScope.launch {
        if (name.trim().length in 2..100 && items.isNotEmpty()) dao.createSavedMeal(SavedMealEntity(name = name.trim(), createdAt = System.currentTimeMillis()), items)
    }
    fun logSavedMeal(meal: SavedMealWithItems, slot: MealSlot) = viewModelScope.launch { dao.logSavedMeal(meal, slot, System.currentTimeMillis()) }
    fun createPreparedMeal(savedMealId: Long, weekStartEpochDay: Long, plannedPortions: Int) = viewModelScope.launch {
        val value = PreparedMealEntity(
            id = "prepared-${UUID.randomUUID()}", savedMealId = savedMealId,
            weekStartEpochDay = weekStartEpochDay, plannedPortions = plannedPortions,
            loggedPortions = 0, archived = false, createdAt = System.currentTimeMillis(),
        )
        if (value.validationError() == null) dao.createPreparedMeal(value)
    }
    fun logPreparedMeal(id: String, slot: MealSlot, loggedAt: Long, loggedDateEpochDay: Long, addExtra: Boolean = false) = viewModelScope.launch {
        if (loggedAt < 0 || loggedDateEpochDay < 0) {
            _preparedMealAction.value = PreparedMealActionStatus.Error("Choose a valid date within the prepared meal week.")
            return@launch
        }
        _preparedMealAction.value = runPreparedMealAction {
            dao.logPreparedPortion(id, slot, loggedAt, loggedDateEpochDay, addExtra)
        }
    }
    fun archivePreparedMeal(id: String) = viewModelScope.launch {
        _preparedMealAction.value = runPreparedMealAction { dao.archivePreparedMeal(id) }
    }
    fun clearPreparedMealStatus() { _preparedMealAction.value = PreparedMealActionStatus.Idle }

    fun resolveBarcode(value: String) = viewModelScope.launch {
        _barcode.value = BarcodeUiState.Loading
        _barcode.value = try { BarcodeUiState.Found(withContext(Dispatchers.IO) { barcodeResolver.resolve(value) }) }
        catch (e: Exception) { BarcodeUiState.Error(e.message ?: "Product lookup failed.") }
    }
    fun barcodeScanFailed(message: String) { _barcode.value = BarcodeUiState.Error(message) }
    fun clearBarcode() { _barcode.value = BarcodeUiState.Idle }
    fun logBarcodeProduct(product: BarcodeProduct, grams: Int, slot: MealSlot) = viewModelScope.launch {
        if (grams !in 1..5000) return@launch
        dao.insertFood(FoodEntryEntity(
            foodId = "off-${product.barcode}", name = product.name, grams = grams,
            calorieMilliUnits = (product.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (product.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = System.currentTimeMillis(), mealSlot = slot,
        ))
        _barcode.value = BarcodeUiState.Idle
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
            if (release.apk != null && SemVer.isNewer(release.tagName, BuildConfig.VERSION_NAME)) UpdateUiState.Available(release) else UpdateUiState.Current
        } catch (e: Exception) { UpdateUiState.Error(e.message ?: "Update check failed.") }
    }
    fun downloadUpdate(release: AppRelease) = viewModelScope.launch {
        val asset = release.apk ?: return@launch
        _update.value = UpdateUiState.Downloading
        _update.value = try {
            val destination = File(File(getApplication<Application>().cacheDir, "updates"), "gymbro-update.apk")
            val downloaded = withContext(Dispatchers.IO) { updateService.download(asset, destination) }
            UpdateUiState.Downloaded(downloaded.file, downloaded.integrity)
        } catch (e: Exception) { UpdateUiState.Error(e.message ?: "APK download failed.") }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        _backup.value = BackupUiState.Working
        _backup.value = try {
            val (json, counts) = backupRepository.exportJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                    ?: error("Could not open the selected document.")
            }
            BackupUiState.Success("Backup exported: ${counts.total} records across all 14 data domains.")
        } catch (e: Exception) {
            BackupUiState.Error(e.message ?: "Backup export failed.")
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        _backup.value = BackupUiState.Working
        _backup.value = try {
            val json = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the 20 MB size limit." }
                        output.write(buffer, 0, read)
                    }
                    decodeStrictUtf8(output.toByteArray())
                } ?: error("Could not open the selected document.")
            }
            val counts = backupRepository.importJson(json)
            BackupUiState.Success("Backup restored and verified: ${counts.total} records across all 14 data domains.")
        } catch (e: Exception) {
            BackupUiState.Error("Import failed: ${e.message ?: "invalid backup"} Existing data was not changed.")
        }
    }

    fun deleteFood(id: Long) = viewModelScope.launch { dao.deleteFood(id) }
    fun logWeight(kg: Double) = viewModelScope.launch { if (kg in 25.0..400.0) dao.insertWeight(WeightEntryEntity(kilograms = kg, loggedAt = System.currentTimeMillis())) }
    fun saveGoals(calories: Int, protein: Int) = viewModelScope.launch { if (calories in 800..6000 && protein in 20..400) dao.saveSettings(SettingsEntity(calorieGoal = calories, proteinGoal = protein)) }

    companion object {
        private const val PRIVATE_SETTINGS = "private_settings"
        private const val USDA_KEY = "usda_api_key"
        const val DEMO_KEY = "DEMO_KEY"
        private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
