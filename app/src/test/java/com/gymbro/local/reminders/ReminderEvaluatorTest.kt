package com.gymbro.local.reminders

import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.PreparedMealEntity
import com.gymbro.local.data.SettingsEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderEvaluatorTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val monday = LocalDate.of(2026, 11, 2)
    private val settings = SettingsEntity(calorieGoal = 2_000, proteinGoal = 160, weekendCalorieGoal = 2_300)

    @Test fun `weekday evening reminds only unfinished daily food and scheduled workout`() {
        val tasks = ReminderEvaluator.evaluate(
            asOf = monday,
            zoneId = zone,
            foodEntries = emptyList(),
            workoutSessions = emptyList(),
            weightEntries = emptyList(),
            preparedMeals = emptyList(),
            settings = settings,
        )

        assertEquals(listOf(ReminderTaskType.FOOD_LOG, ReminderTaskType.WORKOUT), tasks.map { it.type })
    }

    @Test fun `qualified food and completed workout are not reminded again`() {
        val food = FoodEntryEntity(
            id = 1, foodId = "day", name = "Day", grams = 100,
            calorieMilliUnits = 2_000_000, proteinMilligrams = 0, loggedAt = 1,
            loggedDateEpochDay = monday.toEpochDay(), calorieTarget = 2_000,
        )
        val workout = WorkoutSessionEntity(1, "push", "Push", 1, 2, monday.toEpochDay())

        val tasks = ReminderEvaluator.evaluate(
            monday, zone, listOf(food), listOf(workout), emptyList(), emptyList(), settings,
        )

        assertFalse(tasks.any { it.type == ReminderTaskType.FOOD_LOG })
        assertFalse(tasks.any { it.type == ReminderTaskType.WORKOUT })
    }

    @Test fun `sunday includes only incomplete weekly check ins`() {
        val sunday = monday.plusDays(6)
        val futureWeight = WeightEntryEntity(99, 80.0, 1, monday.plusWeeks(1).toEpochDay())
        val futurePlan = PreparedMealEntity("future", 1, monday.plusWeeks(1).toEpochDay(), 4, 0, false, 1)
        val incomplete = ReminderEvaluator.evaluate(
            sunday, zone, emptyList(), emptyList(), listOf(futureWeight), listOf(futurePlan), settings,
        )
        assertTrue(incomplete.any { it.type == ReminderTaskType.WEIGH_IN })
        assertTrue(incomplete.any { it.type == ReminderTaskType.MEAL_PLAN })

        val weight = WeightEntryEntity(1, 80.0, 1, monday.plusDays(2).toEpochDay())
        val plan = PreparedMealEntity("pool", 1, monday.toEpochDay(), 4, 0, false, 1)
        val complete = ReminderEvaluator.evaluate(
            sunday, zone, emptyList(), emptyList(), listOf(weight), listOf(plan), settings,
        )
        assertFalse(complete.any { it.type == ReminderTaskType.WEIGH_IN })
        assertFalse(complete.any { it.type == ReminderTaskType.MEAL_PLAN })
    }

    @Test fun `future dated activity never suppresses todays reminder`() {
        val futureFood = FoodEntryEntity(
            id = 1, foodId = "future", name = "Future", grams = 100,
            calorieMilliUnits = 2_000_000, proteinMilligrams = 0, loggedAt = 1,
            loggedDateEpochDay = monday.plusDays(1).toEpochDay(), calorieTarget = 2_000,
        )
        val futureWorkout = WorkoutSessionEntity(1, "push", "Push", 1, 2, monday.plusDays(1).toEpochDay())

        val tasks = ReminderEvaluator.evaluate(
            monday, zone, listOf(futureFood), listOf(futureWorkout), emptyList(), emptyList(), settings,
        )

        assertTrue(tasks.any { it.type == ReminderTaskType.FOOD_LOG })
        assertTrue(tasks.any { it.type == ReminderTaskType.WORKOUT })
    }
}
