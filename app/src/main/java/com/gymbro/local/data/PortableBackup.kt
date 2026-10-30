package com.gymbro.local.data

import com.gymbro.local.domain.SeedData
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private val MAX_SUPPORTED_EPOCH_DAY = LocalDate.of(9999, 12, 31).toEpochDay()

@Serializable
data class PortableBackup(
    val version: Int,
    val workoutSessions: List<WorkoutSessionEntity>,
    val setCompletions: List<SetCompletionEntity>,
    val exerciseCompletions: List<ExerciseCompletionEntity>,
    val foodEntries: List<FoodEntryEntity>,
    val customFoods: List<CustomFoodEntity>,
    val savedMeals: List<SavedMealEntity>,
    val savedMealItems: List<SavedMealItemEntity>,
    val preparedMeals: List<PreparedMealEntity> = emptyList(),
    val weightEntries: List<WeightEntryEntity>,
    val settings: List<SettingsEntity>,
    val exercises: List<ExerciseEntity> = emptyList(),
    val workoutPlans: List<WorkoutPlanEntity> = emptyList(),
    val workoutPlanDays: List<WorkoutPlanDayEntity> = emptyList(),
    val exercisePrescriptions: List<ExercisePrescriptionEntity> = emptyList(),
) {
    companion object { const val CURRENT_VERSION = 4 }
}

class BackupValidationException(message: String) : IllegalArgumentException(message)

fun decodeStrictUtf8(value: ByteArray): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(value))
        .toString()
} catch (_: java.nio.charset.CharacterCodingException) {
    throw BackupValidationException("Backup is not valid UTF-8.")
}

class PortableBackupCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(value: PortableBackup): String = json.encodeToString(
        PortableBackup.serializer(),
        value.canonical(),
    )

    fun decodeAndValidate(value: String): PortableBackup {
        val backup = try {
            json.decodeFromString(PortableBackup.serializer(), value)
        } catch (_: SerializationException) {
            throw BackupValidationException("Malformed backup JSON.")
        } catch (_: IllegalArgumentException) {
            throw BackupValidationException("Malformed backup JSON.")
        }
        var compatible = backup
        if (compatible.version < 3) compatible = compatible.copy(
            preparedMeals = emptyList(),
            foodEntries = compatible.foodEntries.map {
                it.copy(preparedMealId = null, preparedLogId = null, loggedDateEpochDay = null)
            },
        )
        if (compatible.version < 4) {
            val legacyCalorieTarget = compatible.settings.firstOrNull { it.id == 1 }?.calorieGoal
            compatible = compatible.copy(
                workoutSessions = compatible.workoutSessions.map { it.copy(completedDateEpochDay = null) },
                foodEntries = compatible.foodEntries.map { it.copy(calorieTarget = legacyCalorieTarget) },
                weightEntries = compatible.weightEntries.map { it.copy(loggedDateEpochDay = null) },
                settings = compatible.settings.map { it.copy(weekendCalorieGoal = it.calorieGoal) },
            )
        }
        validate(compatible)
        return compatible.canonical()
    }

    private fun validate(value: PortableBackup) {
        if (value.version !in 1..PortableBackup.CURRENT_VERSION) {
            throw BackupValidationException("Unsupported backup version: ${value.version}.")
        }
        duplicate(value.workoutSessions.map { it.id })?.let { fail("Duplicate workout session identity: $it.") }
        duplicate(value.setCompletions.map { Triple(it.sessionId, it.exerciseId, it.setIndex) })?.let {
            fail("Duplicate set completion identity: ${it.first}/${it.second}/${it.third}.")
        }
        duplicate(value.exerciseCompletions.map { it.sessionId to it.exerciseId })?.let {
            fail("Duplicate exercise completion identity: ${it.first}/${it.second}.")
        }
        duplicate(value.foodEntries.map { it.id })?.let { fail("Duplicate food entry identity: $it.") }
        duplicate(value.customFoods.map { it.id })?.let { fail("Duplicate custom food identity: $it.") }
        duplicate(value.savedMeals.map { it.id })?.let { fail("Duplicate saved meal identity: $it.") }
        duplicate(value.savedMealItems.map { it.mealId to it.position })?.let { fail("Duplicate saved meal item identity: ${it.first}/${it.second}.") }
        duplicate(value.preparedMeals.map { it.id })?.let { fail("Duplicate prepared meal identity: $it.") }
        duplicate(value.weightEntries.map { it.id })?.let { fail("Duplicate weight entry identity: $it.") }
        duplicate(value.settings.map { it.id })?.let { fail("Duplicate settings identity: $it.") }
        duplicate(value.exercises.map { it.id })?.let { fail("Duplicate exercise identity: $it.") }
        duplicate(value.workoutPlans.map { it.id })?.let { fail("Duplicate workout plan identity: $it.") }
        duplicate(value.workoutPlanDays.map { it.id })?.let { fail("Duplicate workout plan day identity: $it.") }
        duplicate(value.exercisePrescriptions.map { it.id })?.let { fail("Duplicate exercise prescription identity: $it.") }

        if (value.version >= 2) {
            duplicate(value.workoutPlanDays.map { it.planId to it.position })?.let {
                fail("Duplicate workout plan day position: ${it.first}/${it.second}.")
            }
            duplicate(value.exercisePrescriptions.map { it.dayId to it.position })?.let {
                fail("Duplicate exercise prescription position: ${it.first}/${it.second}.")
            }
            duplicate(value.exercisePrescriptions.map { it.dayId to it.exerciseId })?.let {
                fail("Duplicate exercise prescription exercise: ${it.first}/${it.second}.")
            }
            val exerciseIds = value.exercises.mapTo(mutableSetOf()) { it.id }
            val planIds = value.workoutPlans.mapTo(mutableSetOf()) { it.id }
            val dayIds = value.workoutPlanDays.mapTo(mutableSetOf()) { it.id }
            value.exercises.firstOrNull { it.id.isBlank() || it.name.isBlank() || it.muscleGroup.isBlank() || it.category.isBlank() }
                ?.let { fail("Invalid exercise ${it.id}.") }
            value.workoutPlans.firstOrNull { it.id.isBlank() || it.name.isBlank() || it.sessionsPerWeek !in 1..7 || it.estimatedMinutes !in 1..300 }
                ?.let { fail("Invalid workout plan ${it.id}.") }
            value.workoutPlanDays.firstOrNull { it.id.isBlank() || it.planId !in planIds || it.name.isBlank() || it.position < 0 }
                ?.let { fail("Invalid workout plan day ${it.id}.") }
            value.exercisePrescriptions.firstOrNull { it.id.isBlank() || it.dayId !in dayIds || it.exerciseId !in exerciseIds || it.position < 0 || it.sets !in 1..20 || it.minReps !in 1..100 || it.maxReps !in it.minReps..100 || it.restSeconds !in 0..1800 }
                ?.let { fail("Invalid exercise prescription ${it.id}.") }
            validateRequiredSeeds(value)
        }

        val legacyDaysById = SeedData.plan.days.associateBy { it.id }
        val persistedDayIds = value.workoutPlanDays.mapTo(mutableSetOf()) { it.id }
        val persistedPrescriptions = value.exercisePrescriptions.groupBy { it.dayId }
        val sessionsById = value.workoutSessions.associateBy { it.id }
        val validSessionDayIds = if (value.version == 1) legacyDaysById.keys else persistedDayIds
        value.workoutSessions.firstOrNull {
            it.id <= 0 || it.dayId !in validSessionDayIds || it.dayName.isBlank() || it.startedAt < 0 ||
                (it.completedAt != null && it.completedAt < it.startedAt) ||
                (it.completedDateEpochDay != null && (it.completedAt == null || it.completedDateEpochDay !in 0..MAX_SUPPORTED_EPOCH_DAY))
        }
            ?.let { fail("Invalid workout session ${it.id}.") }
        if (value.workoutSessions.count { it.completedAt == null } > 1) fail("Backup contains multiple active workout sessions.")
        value.setCompletions.forEach { completion ->
            val session = sessionsById[completion.sessionId]
                ?: fail("Invalid set completion ${completion.sessionId}/${completion.exerciseId}/${completion.setIndex}.")
            val maximumSetIndexExclusive = if (value.version == 1) {
                legacyDaysById[session.dayId]?.exercises?.firstOrNull { it.id == completion.exerciseId }?.sets
            } else {
                persistedPrescriptions[session.dayId]?.firstOrNull { it.exerciseId == completion.exerciseId }
                    ?.let { 20 }
            }
                ?: fail("Set completion references an exercise outside session ${session.id}: ${completion.exerciseId}.")
            if (completion.setIndex !in 0 until maximumSetIndexExclusive) {
                fail("Invalid set completion ${completion.sessionId}/${completion.exerciseId}/${completion.setIndex}.")
            }
        }
        value.exerciseCompletions.forEach { completion ->
            val session = sessionsById[completion.sessionId]
                ?: fail("Invalid exercise completion ${completion.sessionId}/${completion.exerciseId}.")
            val belongsToSession = if (value.version == 1) {
                legacyDaysById[session.dayId]?.exercises?.any { it.id == completion.exerciseId } == true
            } else {
                persistedPrescriptions[session.dayId]?.any { it.exerciseId == completion.exerciseId } == true
            }
            if (!belongsToSession) {
                fail("Exercise completion references an exercise outside session ${session.id}: ${completion.exerciseId}.")
            }
            if (completion.weightMilliUnits !in 0..5_000_000 || completion.reps !in 1..100) {
                fail("Invalid exercise completion ${completion.sessionId}/${completion.exerciseId}.")
            }
            if (completion.completedAt < session.startedAt || (session.completedAt != null && completion.completedAt > session.completedAt)) {
                fail("Exercise completion time is outside session ${session.id}: ${completion.exerciseId}.")
            }
        }
        value.foodEntries.firstOrNull { it.id <= 0 || it.foodId.isBlank() || it.name.isBlank() || it.grams !in 1..5000 || it.calorieMilliUnits < 0 || it.proteinMilligrams < 0 || it.loggedAt < 0 || (it.loggedDateEpochDay != null && it.loggedDateEpochDay !in 0..MAX_SUPPORTED_EPOCH_DAY) || (it.calorieTarget != null && it.calorieTarget !in 800..6000) }
            ?.let { fail("Invalid food entry ${it.id}.") }
        value.customFoods.firstOrNull { it.id <= 0 || it.name.isBlank() || it.calorieMilliUnitsPer100g !in 0..2_000_000 || it.proteinMilligramsPer100g !in 0..100_000 || it.createdAt < 0 }
            ?.let { fail("Invalid custom food ${it.id}.") }
        value.savedMeals.firstOrNull { it.id <= 0 || it.name.isBlank() || it.createdAt < 0 }
            ?.let { fail("Invalid saved meal ${it.id}.") }
        val mealIds = value.savedMeals.mapTo(mutableSetOf()) { it.id }
        value.savedMealItems.firstOrNull { it.mealId !in mealIds }?.let { fail("Saved meal item references missing meal: ${it.mealId}.") }
        value.savedMeals.firstOrNull { meal -> value.savedMealItems.none { it.mealId == meal.id } }
            ?.let { fail("Saved meal has no items: ${it.id}.") }
        value.savedMealItems.firstOrNull { it.position < 0 || it.foodId.isBlank() || it.name.isBlank() || it.grams !in 1..5000 || it.calorieMilliUnitsPer100g !in 0..2_000_000 || it.proteinMilligramsPer100g !in 0..100_000 }
            ?.let { fail("Invalid saved meal item ${it.mealId}/${it.position}.") }
        if (value.version >= 3) value.preparedMeals.firstOrNull { it.validationError() != null }
            ?.let { fail("Invalid prepared meal ${it.id}: ${it.validationError()}") }
        if (value.version >= 3) {
            value.preparedMeals.firstOrNull { it.savedMealId !in mealIds }
                ?.let { fail("Prepared meal references missing recipe: ${it.id}/${it.savedMealId}.") }
            val preparedIds = value.preparedMeals.mapTo(mutableSetOf()) { it.id }
            value.foodEntries.firstOrNull {
                (it.preparedMealId == null) != (it.preparedLogId == null) ||
                    it.preparedMealId?.isBlank() == true || it.preparedLogId?.isBlank() == true ||
                    (it.preparedMealId != null && (it.loggedDateEpochDay == null || it.preparedMealId !in preparedIds))
            }?.let { fail("Invalid prepared food lineage ${it.id}.") }
            value.foodEntries.filter { it.preparedLogId != null }.groupBy { it.preparedLogId }.values
                .firstOrNull { rows ->
                    rows.map { it.preparedMealId }.distinct().size != 1 ||
                        rows.map { it.loggedAt }.distinct().size != 1 ||
                        rows.map { it.loggedDateEpochDay }.distinct().size != 1 ||
                        rows.map { it.mealSlot }.distinct().size != 1 ||
                        rows.map { it.calorieTarget }.distinct().size != 1
                }
                ?.let { fail("Inconsistent prepared food log group: ${it.first().preparedLogId}.") }
            val preparedLogsByPool = value.foodEntries
                .filter { it.preparedLogId != null }
                .groupBy { it.preparedMealId }
                .mapValues { (_, rows) -> rows.groupBy { it.preparedLogId!! } }
            val recipeItemsByMeal = value.savedMealItems.groupBy { it.mealId }
            value.preparedMeals.forEach { prepared ->
                val logGroups = preparedLogsByPool[prepared.id].orEmpty()
                if (logGroups.size != prepared.loggedPortions) {
                    fail("Prepared meal ${prepared.id} has ${logGroups.size} log groups but records ${prepared.loggedPortions} logged portions.")
                }
                val expectedRows = recipeItemsByMeal.getValue(prepared.savedMealId).map { item ->
                    PreparedRecipeRow(
                        foodId = "meal-${prepared.savedMealId}-${item.foodId}",
                        name = item.name,
                        grams = item.grams,
                        calorieMilliUnits = (item.calorieMilliUnitsPer100g * item.grams + 50) / 100,
                        proteinMilligrams = (item.proteinMilligramsPer100g * item.grams + 50) / 100,
                    )
                }.groupingBy { it }.eachCount()
                logGroups.forEach { (logId, rows) ->
                    if (!prepared.containsLogDate(rows.first().loggedDateEpochDay!!)) {
                        fail("Prepared food log group $logId is outside prepared meal week ${prepared.id}.")
                    }
                    val actualRows = rows.map { row ->
                        PreparedRecipeRow(
                            row.foodId,
                            row.name,
                            row.grams,
                            row.calorieMilliUnits,
                            row.proteinMilligrams,
                        )
                    }.groupingBy { it }.eachCount()
                    if (actualRows != expectedRows) {
                        fail("Prepared food log group $logId does not match recipe ${prepared.savedMealId}.")
                    }
                }
            }
        }
        value.weightEntries.firstOrNull { it.id <= 0 || !it.kilograms.isFinite() || it.kilograms !in 25.0..400.0 || it.loggedAt < 0 || (it.loggedDateEpochDay != null && it.loggedDateEpochDay !in 0..MAX_SUPPORTED_EPOCH_DAY) }
            ?.let { fail("Invalid weight entry ${it.id}.") }
        if (value.settings.size != 1 || value.settings.single().id != 1 || value.settings.single().calorieGoal !in 800..6000 || value.settings.single().weekendCalorieGoal !in 800..6000 || value.settings.single().proteinGoal !in 20..400) {
            fail("Invalid settings record.")
        }
    }

    private fun validateRequiredSeeds(value: PortableBackup) {
        val seed = WorkoutPlanSeedData.ppl45v2
        validateSeedRecords("exercise", seed.exercises, value.exercises, ExerciseEntity::id, ExerciseEntity::isSeeded)
        validateSeedRecords("workout plan", listOf(seed.plan), value.workoutPlans, WorkoutPlanEntity::id, WorkoutPlanEntity::isSeeded)
        validateSeedRecords("workout plan day", seed.days, value.workoutPlanDays, WorkoutPlanDayEntity::id, WorkoutPlanDayEntity::isSeeded)
        validateSeedRecords(
            "exercise prescription",
            seed.prescriptions,
            value.exercisePrescriptions,
            ExercisePrescriptionEntity::id,
            ExercisePrescriptionEntity::isSeeded,
        )
    }

    private fun <T> validateSeedRecords(
        label: String,
        canonical: List<T>,
        actual: List<T>,
        id: (T) -> String,
        isSeeded: (T) -> Boolean,
    ) {
        val canonicalById = canonical.associateBy(id)
        val actualById = actual.associateBy(id)
        canonicalById.keys.firstOrNull { it !in actualById }
            ?.let { fail("Missing required seeded $label: $it.") }
        actual.firstOrNull { row -> isSeeded(row) && canonicalById[id(row)] != row }
            ?.let { fail("Modified seeded $label: ${id(it)}.") }
    }

    private fun PortableBackup.canonical() = copy(
        workoutSessions = workoutSessions.sortedBy { it.id },
        setCompletions = setCompletions.sortedWith(compareBy({ it.sessionId }, { it.exerciseId }, { it.setIndex })),
        exerciseCompletions = exerciseCompletions.sortedWith(compareBy({ it.sessionId }, { it.exerciseId })),
        foodEntries = foodEntries.sortedBy { it.id },
        customFoods = customFoods.sortedBy { it.id },
        savedMeals = savedMeals.sortedBy { it.id },
        savedMealItems = savedMealItems.sortedWith(compareBy({ it.mealId }, { it.position })),
        preparedMeals = preparedMeals.sortedBy { it.id },
        weightEntries = weightEntries.sortedBy { it.id },
        settings = settings.sortedBy { it.id },
        exercises = exercises.sortedBy { it.id },
        workoutPlans = workoutPlans.sortedBy { it.id },
        workoutPlanDays = workoutPlanDays.sortedWith(compareBy({ it.planId }, { it.position }, { it.id })),
        exercisePrescriptions = exercisePrescriptions.sortedWith(compareBy({ it.dayId }, { it.position }, { it.id })),
    )


    private fun <T> duplicate(values: List<T>): T? {
        val seen = mutableSetOf<T>()
        return values.firstOrNull { !seen.add(it) }
    }

    private fun fail(message: String): Nothing = throw BackupValidationException(message)

    private data class PreparedRecipeRow(
        val foodId: String,
        val name: String,
        val grams: Int,
        val calorieMilliUnits: Long,
        val proteinMilligrams: Long,
    )
}
