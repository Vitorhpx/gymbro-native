package com.gymbro.local.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun migration1To2PreservesExistingLogsAndAddsLocalParityTables() {
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

        helper.runMigrationsAndValidate(databaseName, 2, true, GymBroDatabase.MIGRATION_1_2).use { db ->
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
}
