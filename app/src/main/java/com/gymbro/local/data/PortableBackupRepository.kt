package com.gymbro.local.data

import androidx.room.withTransaction

data class BackupCounts(
    val workoutSessions: Int,
    val setCompletions: Int,
    val exerciseCompletions: Int,
    val foodEntries: Int,
    val customFoods: Int,
    val savedMeals: Int,
    val savedMealItems: Int,
    val weightEntries: Int,
    val settings: Int,
) {
    val total: Int get() = workoutSessions + setCompletions + exerciseCompletions + foodEntries + customFoods + savedMeals + savedMealItems + weightEntries + settings
}

class PortableBackupRepository(
    private val database: GymBroDatabase,
    private val codec: PortableBackupCodec = PortableBackupCodec(),
) {
    private val dao get() = database.dao()

    suspend fun exportJson(): Pair<String, BackupCounts> = database.withTransaction {
        val backup = snapshot()
        codec.encode(backup) to backup.counts()
    }

    suspend fun importJson(json: String): BackupCounts {
        val expected = codec.decodeAndValidate(json)
        return database.withTransaction {
            dao.clearSetCompletions(); dao.clearExerciseCompletions(); dao.clearSavedMealItems()
            dao.clearSessions(); dao.clearFoodEntries(); dao.clearCustomFoods(); dao.clearSavedMeals(); dao.clearWeightEntries(); dao.clearSettings()
            dao.restoreSessions(expected.workoutSessions)
            dao.restoreSetCompletions(expected.setCompletions)
            dao.restoreExerciseCompletions(expected.exerciseCompletions)
            dao.restoreFoodEntries(expected.foodEntries)
            dao.restoreCustomFoods(expected.customFoods)
            dao.restoreSavedMeals(expected.savedMeals)
            dao.restoreSavedMealItems(expected.savedMealItems)
            dao.restoreWeightEntries(expected.weightEntries)
            dao.restoreSettings(expected.settings)
            val actual = snapshot()
            check(actual == expected) { "Backup read-back verification failed; restore was rolled back." }
            actual.counts()
        }
    }

    private suspend fun snapshot() = PortableBackup(
        version = PortableBackup.CURRENT_VERSION,
        workoutSessions = dao.allSessionsOnce(), setCompletions = dao.allSetCompletionsOnce(),
        exerciseCompletions = dao.allExerciseCompletionsOnce(), foodEntries = dao.allFoodEntriesOnce(),
        customFoods = dao.allCustomFoodsOnce(), savedMeals = dao.allSavedMealsOnce(),
        savedMealItems = dao.allSavedMealItemsOnce(), weightEntries = dao.allWeightEntriesOnce(),
        settings = dao.allSettingsOnce(),
    )

    private fun PortableBackup.counts() = BackupCounts(
        workoutSessions.size, setCompletions.size, exerciseCompletions.size, foodEntries.size,
        customFoods.size, savedMeals.size, savedMealItems.size, weightEntries.size, settings.size,
    )
}