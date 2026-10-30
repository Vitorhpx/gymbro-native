package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitTrackerTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val monday = LocalDate.of(2026, 10, 26)

    @Test fun `meal habit uses inclusive weekday and weekend calorie ranges`() {
        val entries = listOf(
            food(1, monday, 1_800),
            food(2, monday.plusDays(1), 2_200),
            food(3, monday.plusDays(5), 2_070),
            food(4, monday.plusDays(6), 2_530),
        )

        val days = HabitTracker.days(
            start = monday,
            endInclusive = monday.plusDays(6),
            asOf = monday.plusDays(6),
            zoneId = zone,
            foodEntries = entries,
            workoutSessions = emptyList(),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        ).associateBy { it.date }

        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday).mealStatus)
        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday.plusDays(1)).mealStatus)
        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday.plusDays(5)).mealStatus)
        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday.plusDays(6)).mealStatus)
    }

    @Test fun `closed days retain the calorie target captured with their latest food log`() {
        val entry = FoodEntryEntity(
            id = 1, foodId = "meal", name = "Meal", grams = 100,
            calorieMilliUnits = 2_000_000, proteinMilligrams = 0, loggedAt = 10,
            loggedDateEpochDay = monday.toEpochDay(), calorieTarget = 2_000,
        )

        val day = HabitTracker.days(
            monday, monday, monday.plusDays(1), zone, listOf(entry), emptyList(), 3_000, 3_500,
        ).single()

        assertEquals(2_000, day.calorieTarget)
        assertEquals(MealDayStatus.QUALIFIED, day.mealStatus)
    }

    @Test fun `meal qualification uses precise milliunits at inclusive boundaries`() {
        val justBelow = FoodEntryEntity(1, "below", "Below", 100, 1_804_499, 0, 1, loggedDateEpochDay = monday.toEpochDay())
        val exactMinimum = FoodEntryEntity(2, "minimum", "Minimum", 100, 1_804_500, 0, 1, loggedDateEpochDay = monday.plusDays(1).toEpochDay())
        val exactMaximum = FoodEntryEntity(3, "maximum", "Maximum", 100, 2_205_500, 0, 1, loggedDateEpochDay = monday.plusDays(2).toEpochDay())
        val justAbove = FoodEntryEntity(4, "above", "Above", 100, 2_205_501, 0, 1, loggedDateEpochDay = monday.plusDays(3).toEpochDay())

        val days = HabitTracker.days(
            monday, monday.plusDays(3), monday.plusDays(4), zone,
            listOf(justBelow, exactMinimum, exactMaximum, justAbove), emptyList(), 2_005, 2_300,
        ).associateBy { it.date }

        assertEquals(MealDayStatus.MISSED, days.getValue(monday).mealStatus)
        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday.plusDays(1)).mealStatus)
        assertEquals(MealDayStatus.QUALIFIED, days.getValue(monday.plusDays(2)).mealStatus)
        assertEquals(MealDayStatus.MISSED, days.getValue(monday.plusDays(3)).mealStatus)
    }

    @Test fun `archived prepared pools retain historical planning success`() {
        val archived = PreparedMealEntity("pool", 1, monday.toEpochDay(), 4, 0, true, 1)

        val summary = HabitTracker.week(
            monday, monday.plusDays(7), zone, emptyList(), emptyList(), emptyList(), listOf(archived), 2_000, 2_300,
        )

        assertTrue(summary.mealPlanningComplete)
    }

    @Test fun `past missed today incomplete and future days stay distinct`() {
        val asOf = monday.plusDays(2)
        val days = HabitTracker.days(
            start = monday,
            endInclusive = monday.plusDays(4),
            asOf = asOf,
            zoneId = zone,
            foodEntries = listOf(
                food(1, monday.plusDays(2), 500),
                food(2, monday.plusDays(3), 2_000),
            ),
            workoutSessions = emptyList(),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        ).associateBy { it.date }

        assertEquals(MealDayStatus.MISSED, days.getValue(monday).mealStatus)
        assertEquals(MealDayStatus.IN_PROGRESS, days.getValue(asOf).mealStatus)
        assertEquals(MealDayStatus.FUTURE, days.getValue(asOf.plusDays(1)).mealStatus)
    }

    @Test fun `workout marker is separate from meal qualification`() {
        val completedAt = monday.plusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val session = WorkoutSessionEntity(
            id = 1,
            dayId = "push",
            dayName = "Push",
            startedAt = completedAt - 1_000,
            completedAt = completedAt,
            completedDateEpochDay = monday.plusDays(1).toEpochDay(),
        )

        val day = HabitTracker.days(
            start = monday.plusDays(1),
            endInclusive = monday.plusDays(1),
            asOf = monday.plusDays(2),
            zoneId = ZoneId.of("Pacific/Kiritimati"),
            foodEntries = emptyList(),
            workoutSessions = listOf(session),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        ).single()

        assertEquals(MealDayStatus.MISSED, day.mealStatus)
        assertTrue(day.workoutCompleted)
    }

    @Test fun `week succeeds only when every daily and weekly habit succeeds`() {
        val entries = (0L..6L).map { food(it + 1, monday.plusDays(it), if (it >= 5) 2_300 else 2_000) }
        val sessions = (0L..2L).map { offset ->
            val date = monday.plusDays(offset)
            WorkoutSessionEntity(offset + 1, "day-$offset", "Day", 1, 2, date.toEpochDay())
        }
        val weight = WeightEntryEntity(1, 80.0, 1, monday.plusDays(3).toEpochDay())
        val prepared = PreparedMealEntity("pool", 1, monday.toEpochDay(), 4, 0, false, 1)

        val summary = HabitTracker.week(
            weekStart = monday,
            asOf = monday.plusDays(6),
            zoneId = zone,
            foodEntries = entries,
            workoutSessions = sessions,
            weightEntries = listOf(weight),
            preparedMeals = listOf(prepared),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        )

        assertEquals(7, summary.qualifiedMealDays)
        assertEquals(3, summary.workoutDays)
        assertTrue(summary.weighInComplete)
        assertTrue(summary.mealPlanningComplete)
        assertEquals(WeekHabitStatus.SUCCESS, summary.status)
        assertFalse(summary.copy(mealPlanningComplete = false).allHabitsComplete)
    }

    @Test fun `current and future weeks ignore future dated activity`() {
        val futureDate = monday.plusDays(4)
        val futureSession = WorkoutSessionEntity(1, "push", "Push", 1, 2, futureDate.toEpochDay())
        val futureWeight = WeightEntryEntity(1, 80.0, 1, futureDate.toEpochDay())
        val current = HabitTracker.week(
            weekStart = monday,
            asOf = monday.plusDays(2),
            zoneId = zone,
            foodEntries = emptyList(),
            workoutSessions = listOf(futureSession),
            weightEntries = listOf(futureWeight),
            preparedMeals = emptyList(),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        )
        val future = HabitTracker.week(
            weekStart = monday.plusWeeks(1),
            asOf = monday.plusDays(2),
            zoneId = zone,
            foodEntries = emptyList(),
            workoutSessions = emptyList(),
            weightEntries = emptyList(),
            preparedMeals = emptyList(),
            weekdayCalorieTarget = 2_000,
            weekendCalorieTarget = 2_300,
        )

        assertEquals(WeekHabitStatus.IN_PROGRESS, current.status)
        assertEquals(0, current.workoutDays)
        assertFalse(current.weighInComplete)
        assertFalse(current.days.first { it.date == futureDate }.workoutCompleted)
        assertEquals(WeekHabitStatus.FUTURE, future.status)
    }

    private fun food(id: Long, date: LocalDate, calories: Int) = FoodEntryEntity(
        id = id,
        foodId = "food-$id",
        name = "Food $id",
        grams = 100,
        calorieMilliUnits = calories * 1_000L,
        proteinMilligrams = 0,
        loggedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        loggedDateEpochDay = date.toEpochDay(),
    )
}
