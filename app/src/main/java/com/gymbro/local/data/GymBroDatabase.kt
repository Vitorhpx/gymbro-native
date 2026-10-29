package com.gymbro.local.data

import android.content.Context
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
import kotlin.math.roundToLong

@Entity(tableName = "workout_sessions", indices = [Index(value = ["completedAt"])])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val dayName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
)

@Entity(tableName = "set_completions", primaryKeys = ["sessionId", "exerciseId", "setIndex"])
data class SetCompletionEntity(val sessionId: Long, val exerciseId: String, val setIndex: Int)

@Entity(
    tableName = "exercise_completions",
    primaryKeys = ["sessionId", "exerciseId"],
    indices = [Index(value = ["exerciseId", "completedAt"])],
)
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

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

class GymBroConverters {
    @TypeConverter fun mealSlot(value: String): MealSlot = MealSlot.valueOf(value)
    @TypeConverter fun mealSlot(value: MealSlot): String = value.name
}

@Entity(tableName = "food_entries", indices = [Index(value = ["loggedAt"])])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: String,
    val name: String,
    val grams: Int,
    val calorieMilliUnits: Long,
    val proteinMilligrams: Long,
    val loggedAt: Long,
    val mealSlot: MealSlot = MealSlot.SNACK,
) {
    companion object {
        fun from(food: FoodPreset, grams: Int, loggedAt: Long, mealSlot: MealSlot = MealSlot.SNACK) = FoodEntryEntity(
            foodId = food.id,
            name = food.name,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
            mealSlot = mealSlot,
        )
        fun from(food: UsdaFood, grams: Int, loggedAt: Long, mealSlot: MealSlot = MealSlot.SNACK) = FoodEntryEntity(
            foodId = "usda-${food.fdcId}",
            name = food.description,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
            mealSlot = mealSlot,
        )
        fun from(food: CustomFoodEntity, grams: Int, mealSlot: MealSlot, loggedAt: Long) = FoodEntryEntity(
            foodId = "custom-${food.id}", name = food.name, grams = grams,
            calorieMilliUnits = (food.calorieMilliUnitsPer100g * grams + 50) / 100,
            proteinMilligrams = (food.proteinMilligramsPer100g * grams + 50) / 100,
            loggedAt = loggedAt, mealSlot = mealSlot,
        )
    }
}

@Entity(tableName = "custom_foods", indices = [Index(value = ["name"])])
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calorieMilliUnitsPer100g: Long,
    val proteinMilligramsPer100g: Long,
    val createdAt: Long,
)

@Entity(tableName = "saved_meals", indices = [Index(value = ["name"])])
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "saved_meal_items", primaryKeys = ["mealId", "position"], indices = [Index("mealId")])
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

fun SavedMealWithItems.toFoodEntries(slot: MealSlot, loggedAt: Long): List<FoodEntryEntity> = items.map { item ->
    FoodEntryEntity(
        foodId = "meal-${meal.id}-${item.foodId}", name = item.name, grams = item.grams,
        calorieMilliUnits = (item.calorieMilliUnitsPer100g * item.grams + 50) / 100,
        proteinMilligrams = (item.proteinMilligramsPer100g * item.grams + 50) / 100,
        loggedAt = loggedAt, mealSlot = slot,
    )
}

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
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kilograms: Double,
    val loggedAt: Long,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 160,
)

@Dao
interface GymBroDao {
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
    @Query("UPDATE workout_sessions SET completedAt = :completedAt WHERE id = :id")
    suspend fun completeSession(id: Long, completedAt: Long)

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
    @Query("DELETE FROM food_entries WHERE id=:id") suspend fun deleteFood(id: Long)

    @Query("SELECT * FROM custom_foods ORDER BY name") fun customFoods(): Flow<List<CustomFoodEntity>>
    @Insert suspend fun insertCustomFood(value: CustomFoodEntity): Long

    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY name")
    fun savedMeals(): Flow<List<SavedMealWithItems>>
    @Insert suspend fun insertSavedMeal(value: SavedMealEntity): Long
    @Insert suspend fun insertSavedMealItems(values: List<SavedMealItemEntity>)
    @Transaction suspend fun createSavedMeal(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long {
        val id = insertSavedMeal(meal)
        insertSavedMealItems(items.map { it.copy(mealId = id) })
        return id
    }
    @Transaction suspend fun logSavedMeal(meal: SavedMealWithItems, slot: MealSlot, loggedAt: Long) {
        meal.toFoodEntries(slot, loggedAt).forEach { insertFood(it) }
    }

    @Query("SELECT * FROM weight_entries ORDER BY loggedAt DESC")
    fun weightEntries(): Flow<List<WeightEntryEntity>>
    @Insert suspend fun insertWeight(value: WeightEntryEntity)

    @Query("SELECT * FROM settings WHERE id=1") fun settings(): Flow<SettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensureSettings(value: SettingsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(value: SettingsEntity)
}

@Database(
    entities = [WorkoutSessionEntity::class, SetCompletionEntity::class, ExerciseCompletionEntity::class, FoodEntryEntity::class, CustomFoodEntity::class, SavedMealEntity::class, SavedMealItemEntity::class, WeightEntryEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(GymBroConverters::class)
abstract class GymBroDatabase : RoomDatabase() {
    abstract fun dao(): GymBroDao

    companion object {
        @Volatile private var instance: GymBroDatabase? = null
        fun get(context: Context): GymBroDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymBroDatabase::class.java, "gymbro.db")
                .addMigrations(MIGRATION_1_2)
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
    }
}
