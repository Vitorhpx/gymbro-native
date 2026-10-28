package com.gymbro.local.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

@Entity(tableName = "food_entries", indices = [Index(value = ["loggedAt"])])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: String,
    val name: String,
    val grams: Int,
    val calorieMilliUnits: Long,
    val proteinMilligrams: Long,
    val loggedAt: Long,
) {
    companion object {
        fun from(food: FoodPreset, grams: Int, loggedAt: Long) = FoodEntryEntity(
            foodId = food.id,
            name = food.name,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
        )
        fun from(food: UsdaFood, grams: Int, loggedAt: Long) = FoodEntryEntity(
            foodId = "usda-${food.fdcId}",
            name = food.description,
            grams = grams,
            calorieMilliUnits = (food.caloriesPer100g * grams * 10).roundToLong(),
            proteinMilligrams = (food.proteinPer100g * grams * 10).roundToLong(),
            loggedAt = loggedAt,
        )
    }
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

    @Query("SELECT * FROM food_entries ORDER BY loggedAt DESC")
    fun foodEntries(): Flow<List<FoodEntryEntity>>
    @Insert suspend fun insertFood(value: FoodEntryEntity)
    @Query("DELETE FROM food_entries WHERE id=:id") suspend fun deleteFood(id: Long)

    @Query("SELECT * FROM weight_entries ORDER BY loggedAt DESC")
    fun weightEntries(): Flow<List<WeightEntryEntity>>
    @Insert suspend fun insertWeight(value: WeightEntryEntity)

    @Query("SELECT * FROM settings WHERE id=1") fun settings(): Flow<SettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensureSettings(value: SettingsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(value: SettingsEntity)
}

@Database(
    entities = [WorkoutSessionEntity::class, SetCompletionEntity::class, FoodEntryEntity::class, WeightEntryEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GymBroDatabase : RoomDatabase() {
    abstract fun dao(): GymBroDao

    companion object {
        @Volatile private var instance: GymBroDatabase? = null
        fun get(context: Context): GymBroDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymBroDatabase::class.java, "gymbro.db")
                .build().also { instance = it }
        }
    }
}
