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
    val exercises: Int,
    val workoutPlans: Int,
    val workoutPlanDays: Int,
    val exercisePrescriptions: Int,
) {
    val total: Int get() = workoutSessions + setCompletions + exerciseCompletions + foodEntries + customFoods + savedMeals + savedMealItems + weightEntries + settings + exercises + workoutPlans + workoutPlanDays + exercisePrescriptions
}

class PortableBackupRepository(
    private val database: GymBroDatabase,
    private val codec: PortableBackupCodec = PortableBackupCodec(),
) {
    private val dao get() = database.dao()

    suspend fun exportJson(): Pair<String, BackupCounts> = database.withTransaction {
        dao.ensureSettings(SettingsEntity())
        dao.reconcileWorkoutSeeds()
        val backup = snapshot()
        val json = codec.encode(backup)
        val validated = codec.decodeAndValidate(json)
        check(validated == backup) { "Backup export validation did not preserve the exact database snapshot." }
        json to validated.counts()
    }

    suspend fun importJson(json: String): BackupCounts {
        val decoded = codec.decodeAndValidate(json)
        val seed = WorkoutPlanSeedData.ppl45v2
        val expected = if (decoded.version == 1) codec.decodeAndValidate(codec.encode(decoded.copy(
            version = PortableBackup.CURRENT_VERSION,
            exercises = seed.exercises, workoutPlans = listOf(seed.plan),
            workoutPlanDays = seed.days, exercisePrescriptions = seed.prescriptions,
        ))) else decoded
        return database.withTransaction {
            dao.clearSetCompletions(); dao.clearExerciseCompletions(); dao.clearSavedMealItems()
            dao.clearExercisePrescriptions(); dao.clearWorkoutPlanDays(); dao.clearWorkoutPlans(); dao.clearExercises()
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
            dao.restoreExercises(expected.exercises)
            dao.restoreWorkoutPlans(expected.workoutPlans)
            dao.restoreWorkoutPlanDays(expected.workoutPlanDays)
            dao.restoreExercisePrescriptions(expected.exercisePrescriptions)
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
        exercises = dao.allExercisesOnce(), workoutPlans = dao.allWorkoutPlansOnce(),
        workoutPlanDays = dao.allWorkoutPlanDaysOnce(), exercisePrescriptions = dao.allExercisePrescriptionsOnce(),
    )

    private fun PortableBackup.counts() = BackupCounts(
        workoutSessions.size, setCompletions.size, exerciseCompletions.size, foodEntries.size,
        customFoods.size, savedMeals.size, savedMealItems.size, weightEntries.size, settings.size,
        exercises.size, workoutPlans.size, workoutPlanDays.size, exercisePrescriptions.size,
    )
}