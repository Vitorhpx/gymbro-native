package com.gymbro.local.data

import com.gymbro.local.domain.SeedData
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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
    val weightEntries: List<WeightEntryEntity>,
    val settings: List<SettingsEntity>,
) {
    companion object { const val CURRENT_VERSION = 1 }
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
        validate(backup)
        return backup.canonical()
    }

    private fun validate(value: PortableBackup) {
        if (value.version != PortableBackup.CURRENT_VERSION) {
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
        duplicate(value.weightEntries.map { it.id })?.let { fail("Duplicate weight entry identity: $it.") }
        duplicate(value.settings.map { it.id })?.let { fail("Duplicate settings identity: $it.") }

        val daysById = SeedData.plan.days.associateBy { it.id }
        val sessionsById = value.workoutSessions.associateBy { it.id }
        value.workoutSessions.firstOrNull { it.id <= 0 || it.dayId !in daysById || it.dayName.isBlank() || it.startedAt < 0 || (it.completedAt != null && it.completedAt < it.startedAt) }
            ?.let { fail("Invalid workout session ${it.id}.") }
        if (value.workoutSessions.count { it.completedAt == null } > 1) fail("Backup contains multiple active workout sessions.")
        value.setCompletions.forEach { completion ->
            val session = sessionsById[completion.sessionId]
                ?: fail("Invalid set completion ${completion.sessionId}/${completion.exerciseId}/${completion.setIndex}.")
            val exercise = daysById.getValue(session.dayId).exercises.firstOrNull { it.id == completion.exerciseId }
                ?: fail("Set completion references an exercise outside session ${session.id}: ${completion.exerciseId}.")
            if (completion.setIndex !in 0 until exercise.sets) {
                fail("Invalid set completion ${completion.sessionId}/${completion.exerciseId}/${completion.setIndex}.")
            }
        }
        value.exerciseCompletions.forEach { completion ->
            val session = sessionsById[completion.sessionId]
                ?: fail("Invalid exercise completion ${completion.sessionId}/${completion.exerciseId}.")
            if (daysById.getValue(session.dayId).exercises.none { it.id == completion.exerciseId }) {
                fail("Exercise completion references an exercise outside session ${session.id}: ${completion.exerciseId}.")
            }
            if (completion.weightMilliUnits !in 0..5_000_000 || completion.reps !in 1..100) {
                fail("Invalid exercise completion ${completion.sessionId}/${completion.exerciseId}.")
            }
            if (completion.completedAt < session.startedAt || (session.completedAt != null && completion.completedAt > session.completedAt)) {
                fail("Exercise completion time is outside session ${session.id}: ${completion.exerciseId}.")
            }
        }
        value.foodEntries.firstOrNull { it.id <= 0 || it.foodId.isBlank() || it.name.isBlank() || it.grams !in 1..5000 || it.calorieMilliUnits < 0 || it.proteinMilligrams < 0 || it.loggedAt < 0 }
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
        value.weightEntries.firstOrNull { it.id <= 0 || !it.kilograms.isFinite() || it.kilograms !in 25.0..400.0 || it.loggedAt < 0 }
            ?.let { fail("Invalid weight entry ${it.id}.") }
        if (value.settings.size != 1 || value.settings.single().id != 1 || value.settings.single().calorieGoal !in 800..6000 || value.settings.single().proteinGoal !in 20..400) {
            fail("Invalid settings record.")
        }
    }

    private fun PortableBackup.canonical() = copy(
        workoutSessions = workoutSessions.sortedBy { it.id },
        setCompletions = setCompletions.sortedWith(compareBy({ it.sessionId }, { it.exerciseId }, { it.setIndex })),
        exerciseCompletions = exerciseCompletions.sortedWith(compareBy({ it.sessionId }, { it.exerciseId })),
        foodEntries = foodEntries.sortedBy { it.id },
        customFoods = customFoods.sortedBy { it.id },
        savedMeals = savedMeals.sortedBy { it.id },
        savedMealItems = savedMealItems.sortedWith(compareBy({ it.mealId }, { it.position })),
        weightEntries = weightEntries.sortedBy { it.id },
        settings = settings.sortedBy { it.id },
    )

    private fun <T> duplicate(values: List<T>): T? {
        val seen = mutableSetOf<T>()
        return values.firstOrNull { !seen.add(it) }
    }

    private fun fail(message: String): Nothing = throw BackupValidationException(message)
}