package com.gymbro.local.reminders

import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.HabitTracker
import com.gymbro.local.data.MealDayStatus
import com.gymbro.local.data.PreparedMealEntity
import com.gymbro.local.data.SettingsEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ReminderTaskType { FOOD_LOG, WORKOUT, WEIGH_IN, MEAL_PLAN }

data class ReminderTask(val type: ReminderTaskType, val message: String)

object ReminderEvaluator {
    private val workoutReminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    fun evaluate(
        asOf: LocalDate,
        zoneId: ZoneId,
        foodEntries: List<FoodEntryEntity>,
        workoutSessions: List<WorkoutSessionEntity>,
        weightEntries: List<WeightEntryEntity>,
        preparedMeals: List<PreparedMealEntity>,
        settings: SettingsEntity,
    ): List<ReminderTask> {
        val weekStart = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = HabitTracker.week(
            weekStart = weekStart,
            asOf = asOf,
            zoneId = zoneId,
            foodEntries = foodEntries,
            workoutSessions = workoutSessions,
            weightEntries = weightEntries,
            preparedMeals = preparedMeals,
            weekdayCalorieTarget = settings.calorieGoal,
            weekendCalorieTarget = settings.weekendCalorieGoal,
        )
        val today = week.days.first { it.date == asOf }
        return buildList {
            if (today.mealStatus != MealDayStatus.QUALIFIED) {
                add(ReminderTask(ReminderTaskType.FOOD_LOG, "Today’s food log is still in progress."))
            }
            if (asOf.dayOfWeek in workoutReminderDays && week.workoutDays < 3 && !today.workoutCompleted) {
                add(ReminderTask(ReminderTaskType.WORKOUT, "A workout is still available for this week."))
            }
            if (asOf.dayOfWeek == DayOfWeek.SUNDAY && !week.weighInComplete) {
                add(ReminderTask(ReminderTaskType.WEIGH_IN, "Your weekly weigh-in is still due."))
            }
            if (asOf.dayOfWeek == DayOfWeek.SUNDAY && !week.mealPlanningComplete) {
                add(ReminderTask(ReminderTaskType.MEAL_PLAN, "This week’s meal planning is still due."))
            }
        }
    }
}
