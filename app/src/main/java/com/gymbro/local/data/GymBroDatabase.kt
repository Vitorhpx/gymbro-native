package com.gymbro.local.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gymbro.local.domain.FoodPreset
import com.gymbro.local.network.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.SortedMap
import java.util.UUID
import kotlin.math.roundToLong

@Entity(tableName = "workout_sessions", indices = [Index(value = ["completedAt"])])
@Serializable
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val dayName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val completedDateEpochDay: Long? = null,
)

@Entity(tableName = "set_completions", primaryKeys = ["sessionId", "exerciseId", "setIndex"])
@Serializable
data class SetCompletionEntity(val sessionId: Long, val exerciseId: String, val setIndex: Int)

@Entity(
    tableName = "exercise_completions",
    primaryKeys = ["sessionId", "exerciseId"],
    indices = [Index(value = ["exerciseId", "completedAt"])],
)
@Serializable
data class ExerciseCompletionEntity(
    val sessionId: Long,
    val exerciseId: String,
    val weightMilliUnits: Long,
    val reps: Int,
    val completedAt: Long,
)

fun Iterable<ExerciseCompletionEntity>.bestPrevious(
    exerciseId: String,
    excludingSessionId: Long,
): ExerciseCompletionEntity? = filter {
    it.exerciseId == exerciseId && it.sessionId != excludingSessionId
}.maxWithOrNull(compareBy<ExerciseCompletionEntity> { it.weightMilliUnits }.thenBy { it.reps }.thenBy { it.completedAt })

@Serializable enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

class GymBroConverters {
    @TypeConverter fun mealSlot(value: String): MealSlot = MealSlot.valueOf(value)
    @TypeConverter fun mealSlot(value: MealSlot): String = value.name
}

@Entity(tableName = "food_entries", indices = [Index(value = ["loggedAt"]), Index(value = ["preparedMealId"]), Index(value = ["preparedLogId"])])
@Serializable
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: String,
    val name: String,
    val grams: Int,
    val calorieMilliUnits: Long,
    val proteinMilligrams: Long,
    val loggedAt: Long,
    @ColumnInfo(defaultValue = "'SNACK'")
    val mealSlot: MealSlot = MealSlot.SNACK,
    val preparedMealId: String? = null,
    val preparedLogId: String? = null,
    val loggedDateEpochDay: Long? = null,
    val calorieTarget: Int? = null,
) {
    companion object {
        fun from(food: FoodPreset, grams: Int, loggedAt: Long, mealSlot: MealSlot = MealSlot.SNACK, loggedDateEpochDay: Long? = null) = FoodEntryEntity(
            foodId = food.id,
            name = food.name,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
            mealSlot = mealSlot,
            loggedDateEpochDay = loggedDateEpochDay,
        )
        fun from(food: UsdaFood, grams: Int, loggedAt: Long, mealSlot: MealSlot = MealSlot.SNACK, loggedDateEpochDay: Long? = null) = FoodEntryEntity(
            foodId = "usda-${food.fdcId}",
            name = food.description,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
            mealSlot = mealSlot,
            loggedDateEpochDay = loggedDateEpochDay,
        )
        fun from(food: CustomFoodEntity, grams: Int, mealSlot: MealSlot, loggedAt: Long, loggedDateEpochDay: Long? = null) = FoodEntryEntity(
            foodId = "custom-${food.id}", name = food.name, grams = grams,
            calorieMilliUnits = (food.calorieMilliUnitsPer100g * grams + 50) / 100,
            proteinMilligrams = (food.proteinMilligramsPer100g * grams + 50) / 100,
            loggedAt = loggedAt, mealSlot = mealSlot, loggedDateEpochDay = loggedDateEpochDay,
        )
    }
}

@Entity(tableName = "custom_foods", indices = [Index(value = ["name"])])
@Serializable
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calorieMilliUnitsPer100g: Long,
    val proteinMilligramsPer100g: Long,
    val createdAt: Long,
)

@Entity(tableName = "saved_meals", indices = [Index(value = ["name"])])
@Serializable
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "saved_meal_items", primaryKeys = ["mealId", "position"], indices = [Index("mealId")])
@Serializable
data class SavedMealItemEntity(
    val mealId: Long,
    val position: Int,
    val foodId: String,
    val name: String,
    val grams: Int,
    val calorieMilliUnitsPer100g: Long,
    val proteinMilligramsPer100g: Long,
)

data class SavedMealWithItems(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId") val items: List<SavedMealItemEntity>,
)

@Entity(
    tableName = "prepared_meals",
    indices = [Index("savedMealId"), Index(value = ["weekStartEpochDay", "archived"])],
)
@Serializable
data class PreparedMealEntity(
    @PrimaryKey val id: String,
    val savedMealId: Long,
    val weekStartEpochDay: Long,
    val plannedPortions: Int,
    val loggedPortions: Int,
    val archived: Boolean,
    val createdAt: Long,
) {
    val remainingPortions: Int get() = plannedPortions - loggedPortions

    fun validationError(): String? = when {
        id.isBlank() || id.length > 100 -> "Prepared meal ID is invalid."
        savedMealId <= 0 -> "Prepared meal recipe is invalid."
        weekStartEpochDay !in MIN_WEEK_START_EPOCH_DAY..MAX_WEEK_START_EPOCH_DAY ->
            "Prepared meal week is outside the supported date range."
        weekStartEpochDay % 7 != 4L -> "Prepared meal week must start on Monday."
        plannedPortions !in 1..100 -> "Planned portions must be from 1 to 100."
        loggedPortions !in 0..plannedPortions -> "Logged portions must be between zero and planned portions."
        createdAt < 0 -> "Prepared meal creation time is invalid."
        else -> null
    }

    fun containsLogDate(loggedDateEpochDay: Long): Boolean =
        weekStartEpochDay in MIN_WEEK_START_EPOCH_DAY..MAX_WEEK_START_EPOCH_DAY &&
            loggedDateEpochDay >= weekStartEpochDay && loggedDateEpochDay <= weekStartEpochDay + 6

    companion object {
        val MIN_WEEK_START_EPOCH_DAY: Long = LocalDate.of(1970, 1, 5).toEpochDay()
        val MAX_WEEK_START_EPOCH_DAY: Long = LocalDate.of(9999, 12, 20).toEpochDay()
    }
}

fun Iterable<PreparedMealEntity>.planningCompleteFor(weekStartEpochDay: Long): Boolean =
    any { !it.archived && it.weekStartEpochDay == weekStartEpochDay && it.plannedPortions > 0 }

fun Iterable<PreparedMealEntity>.reachablePools(): List<PreparedMealEntity> =
    filterNot { it.archived }.sortedWith(compareByDescending<PreparedMealEntity> { it.weekStartEpochDay }.thenByDescending { it.createdAt })

val FoodEntryEntity.isRemovableFromUi: Boolean get() = preparedMealId == null

fun SavedMealWithItems.toFoodEntries(
    slot: MealSlot,
    loggedAt: Long,
    preparedMealId: String? = null,
    preparedLogId: String? = null,
    loggedDateEpochDay: Long? = null,
): List<FoodEntryEntity> = items.map { item ->
    FoodEntryEntity(
        foodId = "meal-${meal.id}-${item.foodId}", name = item.name, grams = item.grams,
        calorieMilliUnits = (item.calorieMilliUnitsPer100g * item.grams + 50) / 100,
        proteinMilligrams = (item.proteinMilligramsPer100g * item.grams + 50) / 100,
        loggedAt = loggedAt, mealSlot = slot,
        preparedMealId = preparedMealId, preparedLogId = preparedLogId,
        loggedDateEpochDay = loggedDateEpochDay,
    )
}

fun FoodEntryEntity.loggedDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    loggedDateEpochDay?.let(LocalDate::ofEpochDay)
        ?: Instant.ofEpochMilli(loggedAt).atZone(zoneId).toLocalDate()

fun Iterable<FoodEntryEntity>.loggedOn(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<FoodEntryEntity> =
    filter { it.loggedDate(zoneId) == date }

fun Iterable<FoodEntryEntity>.datedHistory(
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): SortedMap<LocalDate, List<FoodEntryEntity>> =
    filterNot { it.loggedDate(zoneId) == today }
        .groupBy { it.loggedDate(zoneId) }
        .toSortedMap(compareByDescending { it })

fun Iterable<FoodEntryEntity>.totalCalories(): Int =
    ((sumOf { it.calorieMilliUnits } + 500) / 1_000).toInt()

fun Iterable<FoodEntryEntity>.totalProteinGrams(): Int =
    ((sumOf { it.proteinMilligrams } + 500) / 1_000).toInt()

fun FoodEntryEntity.displayCalories(): Int = ((calorieMilliUnits + 500) / 1_000).toInt()

fun FoodEntryEntity.displayProteinGrams(): String {
    val tenths = (proteinMilligrams + 50) / 100
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

@Entity(tableName = "weight_entries", indices = [Index(value = ["loggedAt"])])
@Serializable
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kilograms: Double,
    val loggedAt: Long,
    val loggedDateEpochDay: Long? = null,
)

@Entity(tableName = "settings")
@Serializable
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 160,
    @ColumnInfo(defaultValue = "2000")
    val weekendCalorieGoal: Int = 2000,
)

data class PlanExerciseRow(
    val prescriptionId: String, val dayId: String, val dayName: String, val dayPosition: Int,
    val exerciseId: String, val exerciseName: String, val exercisePosition: Int,
    val sets: Int, val minReps: Int, val maxReps: Int, val restSeconds: Int,
)

@Dao
interface GymBroDao {
    @Query("SELECT * FROM workout_sessions ORDER BY id") suspend fun allSessionsOnce(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM set_completions ORDER BY sessionId, exerciseId, setIndex") suspend fun allSetCompletionsOnce(): List<SetCompletionEntity>
    @Query("SELECT * FROM exercise_completions ORDER BY sessionId, exerciseId") suspend fun allExerciseCompletionsOnce(): List<ExerciseCompletionEntity>
    @Query("SELECT * FROM food_entries ORDER BY id") suspend fun allFoodEntriesOnce(): List<FoodEntryEntity>
    @Query("SELECT * FROM custom_foods ORDER BY id") suspend fun allCustomFoodsOnce(): List<CustomFoodEntity>
    @Query("SELECT * FROM saved_meals ORDER BY id") suspend fun allSavedMealsOnce(): List<SavedMealEntity>
    @Query("SELECT * FROM saved_meal_items ORDER BY mealId, position") suspend fun allSavedMealItemsOnce(): List<SavedMealItemEntity>
    @Query("SELECT * FROM prepared_meals ORDER BY id") suspend fun allPreparedMealsOnce(): List<PreparedMealEntity>
    @Query("SELECT * FROM weight_entries ORDER BY id") suspend fun allWeightEntriesOnce(): List<WeightEntryEntity>
    @Query("SELECT * FROM settings ORDER BY id") suspend fun allSettingsOnce(): List<SettingsEntity>
    @Query("SELECT * FROM exercises ORDER BY id") suspend fun allExercisesOnce(): List<ExerciseEntity>
    @Query("SELECT * FROM workout_plans ORDER BY id") suspend fun allWorkoutPlansOnce(): List<WorkoutPlanEntity>
    @Query("SELECT * FROM workout_plan_days ORDER BY planId, position") suspend fun allWorkoutPlanDaysOnce(): List<WorkoutPlanDayEntity>
    @Query("SELECT * FROM exercise_prescriptions ORDER BY dayId, position") suspend fun allExercisePrescriptionsOnce(): List<ExercisePrescriptionEntity>

    @Insert suspend fun restoreSessions(values: List<WorkoutSessionEntity>)
    @Insert suspend fun restoreSetCompletions(values: List<SetCompletionEntity>)
    @Insert suspend fun restoreExerciseCompletions(values: List<ExerciseCompletionEntity>)
    @Insert suspend fun restoreFoodEntries(values: List<FoodEntryEntity>)
    @Insert suspend fun restoreCustomFoods(values: List<CustomFoodEntity>)
    @Insert suspend fun restoreSavedMeals(values: List<SavedMealEntity>)
    @Insert suspend fun restoreSavedMealItems(values: List<SavedMealItemEntity>)
    @Insert suspend fun restorePreparedMeals(values: List<PreparedMealEntity>)
    @Insert suspend fun restoreWeightEntries(values: List<WeightEntryEntity>)
    @Insert suspend fun restoreSettings(values: List<SettingsEntity>)
    @Insert suspend fun restoreExercises(values: List<ExerciseEntity>)
    @Insert suspend fun restoreWorkoutPlans(values: List<WorkoutPlanEntity>)
    @Insert suspend fun restoreWorkoutPlanDays(values: List<WorkoutPlanDayEntity>)
    @Insert suspend fun restoreExercisePrescriptions(values: List<ExercisePrescriptionEntity>)

    @Query("DELETE FROM set_completions") suspend fun clearSetCompletions()
    @Query("DELETE FROM exercise_completions") suspend fun clearExerciseCompletions()
    @Query("DELETE FROM saved_meal_items") suspend fun clearSavedMealItems()
    @Query("DELETE FROM prepared_meals") suspend fun clearPreparedMeals()
    @Query("DELETE FROM workout_sessions") suspend fun clearSessions()
    @Query("DELETE FROM food_entries") suspend fun clearFoodEntries()
    @Query("DELETE FROM custom_foods") suspend fun clearCustomFoods()
    @Query("DELETE FROM saved_meals") suspend fun clearSavedMeals()
    @Query("DELETE FROM weight_entries") suspend fun clearWeightEntries()
    @Query("DELETE FROM settings") suspend fun clearSettings()
    @Query("DELETE FROM exercise_prescriptions") suspend fun clearExercisePrescriptions()
    @Query("DELETE FROM workout_plan_days") suspend fun clearWorkoutPlanDays()
    @Query("DELETE FROM workout_plans") suspend fun clearWorkoutPlans()
    @Query("DELETE FROM exercises") suspend fun clearExercises()

    @Query("SELECT * FROM exercises ORDER BY name") fun exercises(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM workout_plans ORDER BY name") fun workoutPlans(): Flow<List<WorkoutPlanEntity>>
    @Query("""SELECT p.id AS prescriptionId, d.id AS dayId, d.name AS dayName, d.position AS dayPosition,
        e.id AS exerciseId, e.name AS exerciseName, p.position AS exercisePosition,
        p.sets, p.minReps, p.maxReps, p.restSeconds FROM exercise_prescriptions p
        JOIN workout_plan_days d ON d.id=p.dayId JOIN exercises e ON e.id=p.exerciseId
        WHERE d.planId=:planId ORDER BY d.position, p.position""")
    fun planExercises(planId: String): Flow<List<PlanExerciseRow>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercise(value: ExerciseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlan(value: WorkoutPlanEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlanDay(value: WorkoutPlanDayEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPrescription(value: ExercisePrescriptionEntity)
    @Query("SELECT * FROM exercises WHERE id=:id") suspend fun exercise(id: String): ExerciseEntity?
    @Query("SELECT * FROM workout_plans WHERE id=:id") suspend fun workoutPlan(id: String): WorkoutPlanEntity?
    @Query("SELECT * FROM workout_plan_days WHERE id=:id") suspend fun workoutPlanDay(id: String): WorkoutPlanDayEntity?
    @Query("SELECT * FROM exercise_prescriptions WHERE id=:id") suspend fun prescription(id: String): ExercisePrescriptionEntity?
    @Query("SELECT * FROM exercise_prescriptions WHERE dayId=:dayId ORDER BY position") suspend fun dayPrescriptions(dayId: String): List<ExercisePrescriptionEntity>
    @Transaction suspend fun reconcileWorkoutSeeds(seed: WorkoutPlanSeed = WorkoutPlanSeedData.ppl45v2) {
        seed.exercises.forEach { if (exercise(it.id)?.isSeeded != false) upsertExercise(it) }
        if (workoutPlan(seed.plan.id)?.isSeeded != false) upsertPlan(seed.plan)
        seed.days.forEach { if (workoutPlanDay(it.id)?.isSeeded != false) upsertPlanDay(it) }
        seed.prescriptions.forEach { if (prescription(it.id)?.isSeeded != false) upsertPrescription(it) }
    }
    @Query("UPDATE exercise_prescriptions SET sets=:sets,minReps=:minReps,maxReps=:maxReps,restSeconds=:restSeconds,isSeeded=0 WHERE id=:id")
    suspend fun editPrescription(id: String, sets: Int, minReps: Int, maxReps: Int, restSeconds: Int)
    @Query("UPDATE exercise_prescriptions SET position=:position,isSeeded=0 WHERE id=:id")
    suspend fun setPrescriptionPosition(id: String, position: Int)
    @Transaction suspend fun swapPrescriptionPositions(first: ExercisePrescriptionEntity, second: ExercisePrescriptionEntity) {
        setPrescriptionPosition(first.id, -1)
        setPrescriptionPosition(second.id, first.position)
        setPrescriptionPosition(first.id, second.position)
    }
    @Transaction suspend fun movePrescription(dayId: String, prescriptionId: String, direction: Int): Boolean {
        if (direction !in setOf(-1, 1)) return false
        val rows = dayPrescriptions(dayId)
        val index = rows.indexOfFirst { it.id == prescriptionId }
        val target = index + direction
        if (index !in rows.indices || target !in rows.indices) return false
        swapPrescriptionPositions(rows[index], rows[target])
        return true
    }

    @Query("SELECT * FROM workout_sessions WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun sessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSessionOnce(): WorkoutSessionEntity?
    @Insert suspend fun insertSession(value: WorkoutSessionEntity): Long
    @Transaction suspend fun startSessionIfNone(value: WorkoutSessionEntity): Long? {
        if (activeSessionOnce() != null) return null
        return insertSession(value)
    }
    @Query("UPDATE workout_sessions SET completedAt = :completedAt, completedDateEpochDay = :completedDateEpochDay WHERE id = :id")
    suspend fun completeSession(id: Long, completedAt: Long, completedDateEpochDay: Long)

    @Query("SELECT * FROM set_completions")
    fun completions(): Flow<List<SetCompletionEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun checkSet(value: SetCompletionEntity)
    @Query("DELETE FROM set_completions WHERE sessionId=:sessionId AND exerciseId=:exerciseId AND setIndex=:setIndex")
    suspend fun uncheckSet(sessionId: Long, exerciseId: String, setIndex: Int)

    @Query("SELECT * FROM exercise_completions ORDER BY completedAt DESC")
    fun exerciseCompletions(): Flow<List<ExerciseCompletionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun completeExercise(value: ExerciseCompletionEntity)
    @Query("DELETE FROM exercise_completions WHERE sessionId=:sessionId AND exerciseId=:exerciseId")
    suspend fun uncompleteExercise(sessionId: Long, exerciseId: String)

    @Query("SELECT * FROM food_entries ORDER BY loggedAt DESC")
    fun foodEntries(): Flow<List<FoodEntryEntity>>
    @Insert suspend fun insertFood(value: FoodEntryEntity)
    @Query("SELECT * FROM settings WHERE id=1") suspend fun settingsOnce(): SettingsEntity?
    @Transaction suspend fun insertFoodWithTarget(value: FoodEntryEntity) {
        val settings = settingsOnce() ?: SettingsEntity()
        val date = value.loggedDate()
        val target = if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            settings.weekendCalorieGoal
        } else {
            settings.calorieGoal
        }
        insertFood(value.copy(calorieTarget = value.calorieTarget ?: target))
    }
    @Query("DELETE FROM food_entries WHERE id=:id AND preparedMealId IS NULL") suspend fun deleteFood(id: Long): Int

    @Query("SELECT * FROM custom_foods ORDER BY name") fun customFoods(): Flow<List<CustomFoodEntity>>
    @Insert suspend fun insertCustomFood(value: CustomFoodEntity): Long

    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY name")
    fun savedMeals(): Flow<List<SavedMealWithItems>>
    @Transaction
    @Query("SELECT * FROM saved_meals WHERE id=:id")
    suspend fun savedMeal(id: Long): SavedMealWithItems?
    @Insert suspend fun insertSavedMeal(value: SavedMealEntity): Long
    @Insert suspend fun insertSavedMealItems(values: List<SavedMealItemEntity>)
    @Transaction suspend fun createSavedMeal(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long {
        val id = insertSavedMeal(meal)
        insertSavedMealItems(items.map { it.copy(mealId = id) })
        return id
    }
    @Transaction suspend fun logSavedMeal(meal: SavedMealWithItems, slot: MealSlot, loggedAt: Long, loggedDateEpochDay: Long) {
        meal.toFoodEntries(slot, loggedAt, loggedDateEpochDay = loggedDateEpochDay).forEach { insertFoodWithTarget(it) }
    }

    @Query("SELECT * FROM prepared_meals ORDER BY weekStartEpochDay DESC, createdAt DESC")
    fun preparedMeals(): Flow<List<PreparedMealEntity>>
    @Query("SELECT * FROM prepared_meals WHERE id=:id") suspend fun preparedMeal(id: String): PreparedMealEntity?
    @Insert suspend fun insertPreparedMeal(value: PreparedMealEntity)
    @Query("UPDATE prepared_meals SET loggedPortions=loggedPortions+1 WHERE id=:id AND archived=0 AND loggedPortions < plannedPortions")
    suspend fun consumePreparedPortion(id: String): Int
    @Query("UPDATE prepared_meals SET plannedPortions=plannedPortions+1, loggedPortions=loggedPortions+1 WHERE id=:id AND archived=0 AND loggedPortions=plannedPortions AND plannedPortions < 100")
    suspend fun addAndConsumeExtraPortion(id: String): Int
    @Query("UPDATE prepared_meals SET archived=1 WHERE id=:id AND archived=0") suspend fun archivePreparedMealGuarded(id: String): Int

    @Transaction suspend fun createPreparedMeal(value: PreparedMealEntity) {
        require(value.validationError() == null) { value.validationError()!! }
        require(savedMeal(value.savedMealId) != null) { "Saved recipe does not exist." }
        insertPreparedMeal(value)
    }

    @Transaction suspend fun logPreparedPortion(id: String, slot: MealSlot, loggedAt: Long, loggedDateEpochDay: Long, addExtra: Boolean = false) {
        require(loggedAt >= 0) { "Log date is invalid." }
        val prepared = requireNotNull(preparedMeal(id)) { "Prepared meal does not exist." }
        require(!prepared.archived) { "Prepared meal is archived." }
        require(prepared.containsLogDate(loggedDateEpochDay)) { "Log date must be within the prepared meal week." }
        val recipe = requireNotNull(savedMeal(prepared.savedMealId)) { "Saved recipe does not exist." }
        require(recipe.items.isNotEmpty()) { "Saved recipe has no items." }
        val changed = if (addExtra) addAndConsumeExtraPortion(id) else consumePreparedPortion(id)
        require(changed == 1) { if (addExtra) "Extra portion cannot be added." else "No prepared portions remain." }
        val logId = "prepared-log-${UUID.randomUUID()}"
        recipe.toFoodEntries(slot, loggedAt, prepared.id, logId, loggedDateEpochDay).forEach { insertFoodWithTarget(it) }
    }

    @Transaction suspend fun archivePreparedMeal(id: String) {
        require(archivePreparedMealGuarded(id) == 1) { "Prepared meal is missing or already archived." }
    }

    @Query("SELECT * FROM weight_entries ORDER BY loggedAt DESC")
    fun weightEntries(): Flow<List<WeightEntryEntity>>
    @Insert suspend fun insertWeight(value: WeightEntryEntity)

    @Query("SELECT * FROM settings WHERE id=1") fun settings(): Flow<SettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensureSettings(value: SettingsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(value: SettingsEntity)
}

@Database(
    entities = [WorkoutSessionEntity::class, SetCompletionEntity::class, ExerciseCompletionEntity::class, FoodEntryEntity::class, CustomFoodEntity::class, SavedMealEntity::class, SavedMealItemEntity::class, PreparedMealEntity::class, WeightEntryEntity::class, SettingsEntity::class, ExerciseEntity::class, WorkoutPlanEntity::class, WorkoutPlanDayEntity::class, ExercisePrescriptionEntity::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(GymBroConverters::class)
abstract class GymBroDatabase : RoomDatabase() {
    abstract fun dao(): GymBroDao

    companion object {
        @Volatile private var instance: GymBroDatabase? = null
        fun get(context: Context): GymBroDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymBroDatabase::class.java, "gymbro.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `exercise_completions` (`sessionId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `weightMilliUnits` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `exerciseId`))"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_completions_exerciseId_completedAt` ON `exercise_completions` (`exerciseId`, `completedAt`)")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `mealSlot` TEXT NOT NULL DEFAULT 'SNACK'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `calorieMilliUnitsPer100g` INTEGER NOT NULL, `proteinMilligramsPer100g` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_foods_name` ON `custom_foods` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_meals_name` ON `saved_meals` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_meal_items` (`mealId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `foodId` TEXT NOT NULL, `name` TEXT NOT NULL, `grams` INTEGER NOT NULL, `calorieMilliUnitsPer100g` INTEGER NOT NULL, `proteinMilligramsPer100g` INTEGER NOT NULL, PRIMARY KEY(`mealId`, `position`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_meal_items_mealId` ON `saved_meal_items` (`mealId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE `food_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foodId` TEXT NOT NULL, `name` TEXT NOT NULL, `grams` INTEGER NOT NULL, `calorieMilliUnits` INTEGER NOT NULL, `proteinMilligrams` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `mealSlot` TEXT NOT NULL DEFAULT 'SNACK')"""
                )
                db.execSQL(
                    """INSERT INTO `food_entries_new` (`id`, `foodId`, `name`, `grams`, `calorieMilliUnits`, `proteinMilligrams`, `loggedAt`, `mealSlot`) SELECT `id`, `foodId`, `name`, `grams`, `calorieMilliUnits`, `proteinMilligrams`, `loggedAt`, `mealSlot` FROM `food_entries`"""
                )
                db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = 'food_entries_new'")
                db.execSQL(
                    """INSERT INTO `sqlite_sequence` (`name`, `seq`) SELECT 'food_entries_new', MAX(COALESCE((SELECT `seq` FROM `sqlite_sequence` WHERE `name` = 'food_entries'), 0), COALESCE(MAX(`id`), 0)) FROM `food_entries_new`"""
                )
                db.execSQL("DROP TABLE `food_entries`")
                db.execSQL("ALTER TABLE `food_entries_new` RENAME TO `food_entries`")
                db.execSQL("UPDATE `sqlite_sequence` SET `name` = 'food_entries' WHERE `name` = 'food_entries_new'")
                db.execSQL("CREATE INDEX `index_food_entries_loggedAt` ON `food_entries` (`loggedAt`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, `category` TEXT NOT NULL, `isSeeded` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `sessionsPerWeek` INTEGER NOT NULL, `estimatedMinutes` INTEGER NOT NULL, `isSeeded` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plan_days` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, `isSeeded` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_plan_days_planId_position` ON `workout_plan_days` (`planId`, `position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_prescriptions` (`id` TEXT NOT NULL, `dayId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `minReps` INTEGER NOT NULL, `maxReps` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, `isSeeded` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_prescriptions_dayId_position` ON `exercise_prescriptions` (`dayId`, `position`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_prescriptions_dayId_exerciseId` ON `exercise_prescriptions` (`dayId`, `exerciseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_prescriptions_exerciseId` ON `exercise_prescriptions` (`exerciseId`)")
                val seed = WorkoutPlanSeedData.ppl45v2
                seed.exercises.forEach { db.execSQL("INSERT INTO exercises VALUES (?,?,?,?,?)", arrayOf<Any>(it.id, it.name, it.muscleGroup, it.category, 1)) }
                db.execSQL("INSERT INTO workout_plans VALUES (?,?,?,?,?,?)", arrayOf<Any>(seed.plan.id, seed.plan.name, seed.plan.description, seed.plan.sessionsPerWeek, seed.plan.estimatedMinutes, 1))
                seed.days.forEach { db.execSQL("INSERT INTO workout_plan_days VALUES (?,?,?,?,?)", arrayOf<Any>(it.id, it.planId, it.name, it.position, 1)) }
                seed.prescriptions.forEach { db.execSQL("INSERT INTO exercise_prescriptions VALUES (?,?,?,?,?,?,?,?,?)", arrayOf<Any>(it.id, it.dayId, it.exerciseId, it.position, it.sets, it.minReps, it.maxReps, it.restSeconds, 1)) }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `preparedMealId` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `preparedLogId` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `loggedDateEpochDay` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_preparedMealId` ON `food_entries` (`preparedMealId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_preparedLogId` ON `food_entries` (`preparedLogId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `prepared_meals` (`id` TEXT NOT NULL, `savedMealId` INTEGER NOT NULL, `weekStartEpochDay` INTEGER NOT NULL, `plannedPortions` INTEGER NOT NULL, `loggedPortions` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prepared_meals_savedMealId` ON `prepared_meals` (`savedMealId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prepared_meals_weekStartEpochDay_archived` ON `prepared_meals` (`weekStartEpochDay`, `archived`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `completedDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `weight_entries` ADD COLUMN `loggedDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `calorieTarget` INTEGER")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `weekendCalorieGoal` INTEGER NOT NULL DEFAULT 2000")
                db.execSQL("UPDATE `settings` SET `weekendCalorieGoal` = `calorieGoal`")
                db.execSQL("UPDATE `food_entries` SET `calorieTarget` = COALESCE((SELECT `calorieGoal` FROM `settings` WHERE `id` = 1), 2000)")
            }
        }
    }
}
