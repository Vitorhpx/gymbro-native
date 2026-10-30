package com.gymbro.local.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymBroMigrationTest {
    private val databaseName = "gymbro-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GymBroDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To3PreservesExistingLogsAndAddsLocalParityTables() {
        helper.createDatabase(databaseName, 1).apply {
            insert(
                "food_entries",
                0,
                ContentValues().apply {
                    put("foodId", "legacy-food")
                    put("name", "Legacy food")
                    put("grams", 125)
                    put("calorieMilliUnits", 206_250L)
                    put("proteinMilligrams", 38_750L)
                    put("loggedAt", 1234L)
                },
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            GymBroDatabase.MIGRATION_1_2,
            GymBroDatabase.MIGRATION_2_3,
        ).use { db ->
            db.query("SELECT foodId, grams, mealSlot FROM food_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-food", cursor.getString(0))
                assertEquals(125, cursor.getInt(1))
                assertEquals(MealSlot.SNACK.name, cursor.getString(2))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('exercise_completions','custom_foods','saved_meals','saved_meal_items')",
            ).use { cursor ->
                val tables = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(
                    setOf("exercise_completions", "custom_foods", "saved_meals", "saved_meal_items"),
                    tables,
                )
            }
        }
    }

    @Test
    fun migrationFromOldFreshVersion2PreservesFoodDataAndAutoincrementHighWaterMark() {
        val version2Database = "$databaseName-v2"
        helper.createDatabase(version2Database, 2).apply {
            insert("food_entries", 0, foodEntry(id = 41L, mealSlot = MealSlot.DINNER))
            insert("food_entries", 0, foodEntry(id = 50L, mealSlot = MealSlot.LUNCH))
            delete("food_entries", "id = ?", arrayOf("50"))
            close()
        }

        helper.runMigrationsAndValidate(
            version2Database,
            3,
            true,
            GymBroDatabase.MIGRATION_2_3,
        ).use { db ->
            db.query(
                "SELECT id, foodId, name, grams, calorieMilliUnits, proteinMilligrams, loggedAt, mealSlot " +
                    "FROM food_entries",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(41L, cursor.getLong(0))
                assertEquals("food-41", cursor.getString(1))
                assertEquals("Food 41", cursor.getString(2))
                assertEquals(141, cursor.getInt(3))
                assertEquals(241_000L, cursor.getLong(4))
                assertEquals(3_041L, cursor.getLong(5))
                assertEquals(1_041L, cursor.getLong(6))
                assertEquals(MealSlot.DINNER.name, cursor.getString(7))
            }

            db.insert("food_entries", 0, foodEntry(id = null, mealSlot = MealSlot.SNACK))
            db.query("SELECT id FROM food_entries WHERE foodId = 'food-new'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(51L, cursor.getLong(0))
            }
            db.query("PRAGMA index_info(`index_food_entries_loggedAt`)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("loggedAt", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    @Test
    fun migration3To4PreservesExistingDataAndSeedsPersistentPplPlan() {
        val version3Database = "$databaseName-v3"
        helper.createDatabase(version3Database, 3).apply {
            insert("weight_entries", 0, ContentValues().apply {
                put("kilograms", 80.5)
                put("loggedAt", 1234L)
            })
            close()
        }

        helper.runMigrationsAndValidate(
            version3Database,
            4,
            true,
            GymBroDatabase.MIGRATION_3_4,
        ).use { db ->
            db.query("SELECT kilograms FROM weight_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(80.5, cursor.getDouble(0), 0.0)
            }
            db.query("SELECT name, isSeeded FROM workout_plans WHERE id='gymbro-ppl-45-v2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("GymBro PPL 45 V2", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM exercises").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(29, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM exercise_prescriptions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(15, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM exercises WHERE id='triceps-pushdown'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            assertUniqueConstraint(db, "INSERT INTO workout_plan_days VALUES ('duplicate-day','gymbro-ppl-45-v2','Duplicate',0,0)")
            assertUniqueConstraint(db, "INSERT INTO exercise_prescriptions VALUES ('duplicate-position','push','incline-press',0,2,8,12,90,0)")
            assertUniqueConstraint(db, "INSERT INTO exercise_prescriptions VALUES ('duplicate-exercise','push','chest-press',99,2,8,12,90,0)")
        }
    }

    @Test
    fun migration4To5PreservesExistingDataAndAddsPreparedMealPool() {
        val version4Database = "$databaseName-v4"
        helper.createDatabase(version4Database, 4).apply {
            insert("saved_meals", 0, ContentValues().apply {
                put("id", 12L); put("name", "Existing recipe"); put("createdAt", 99L)
            })
            close()
        }

        helper.runMigrationsAndValidate(version4Database, 5, true, GymBroDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT name FROM saved_meals WHERE id=12").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing recipe", cursor.getString(0))
            }
            db.query("PRAGMA table_info(food_entries)").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertTrue(columns.containsAll(setOf("preparedMealId", "preparedLogId", "loggedDateEpochDay")))
            }
            db.execSQL("INSERT INTO prepared_meals VALUES ('prepared-stable',12,20388,4,1,0,100)")
            db.query("SELECT plannedPortions, loggedPortions FROM prepared_meals WHERE id='prepared-stable'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4, cursor.getInt(0)); assertEquals(1, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration5To6PreservesHistoryAndAddsHabitDateAndWeekendGoalFields() {
        val version5Database = "$databaseName-v5"
        helper.createDatabase(version5Database, 5).apply {
            insert("workout_sessions", 0, ContentValues().apply {
                put("id", 8L); put("dayId", "push"); put("dayName", "Push"); put("startedAt", 100L); put("completedAt", 200L)
            })
            insert("weight_entries", 0, ContentValues().apply {
                put("id", 9L); put("kilograms", 80.0); put("loggedAt", 300L)
            })
            insert("settings", 0, ContentValues().apply {
                put("id", 1); put("calorieGoal", 2400); put("proteinGoal", 180)
            })
            insert("food_entries", 0, foodEntry(id = 10L, mealSlot = MealSlot.LUNCH).apply {
                putNull("preparedMealId"); putNull("preparedLogId"); put("loggedDateEpochDay", 20_388L)
            })
            close()
        }

        helper.runMigrationsAndValidate(version5Database, 6, true, GymBroDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT completedAt, completedDateEpochDay FROM workout_sessions WHERE id=8").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(200L, cursor.getLong(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT kilograms, loggedDateEpochDay FROM weight_entries WHERE id=9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(80.0, cursor.getDouble(0), 0.0)
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT calorieGoal, weekendCalorieGoal FROM settings WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2400, cursor.getInt(0))
                assertEquals(2400, cursor.getInt(1))
            }
            db.query("PRAGMA table_info(food_entries)").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertTrue(columns.contains("calorieTarget"))
            }
            db.query("SELECT calorieTarget FROM food_entries WHERE id=10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2400, cursor.getInt(0))
            }
        }
    }

    private fun assertUniqueConstraint(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String) {
        try {
            db.execSQL(sql)
            fail("Expected unique constraint failure for: $sql")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Expected.
        }
    }

    private fun foodEntry(id: Long?, mealSlot: MealSlot) = ContentValues().apply {
        if (id != null) put("id", id)
        put("foodId", id?.let { "food-$it" } ?: "food-new")
        put("name", id?.let { "Food $it" } ?: "New food")
        put("grams", id?.let { 100 + it.toInt() } ?: 100)
        put("calorieMilliUnits", id?.let { 200_000L + it * 1_000L } ?: 200_000L)
        put("proteinMilligrams", id?.let { 3_000L + it } ?: 3_000L)
        put("loggedAt", id?.let { 1_000L + it } ?: 2_000L)
        put("mealSlot", mealSlot.name)
    }
}
