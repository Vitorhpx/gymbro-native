package com.gymbro.local.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar state for the automatically derived daily meal habit. */
enum class MealDayStatus { QUALIFIED, MISSED, IN_PROGRESS, FUTURE }

enum class WeekHabitStatus { SUCCESS, MISSED, IN_PROGRESS, FUTURE }

data class HabitDay(
    val date: LocalDate,
    val calories: Int,
    val calorieTarget: Int,
    val mealStatus: MealDayStatus,
    val workoutCompleted: Boolean,
)

data class HabitWeek(
    val weekStart: LocalDate,
    val days: List<HabitDay>,
    val qualifiedMealDays: Int,
    val workoutDays: Int,
    val weighInComplete: Boolean,
    val mealPlanningComplete: Boolean,
    val status: WeekHabitStatus,
) {
    val allHabitsComplete: Boolean
        get() = qualifiedMealDays == 7 && workoutDays >= 3 && weighInComplete && mealPlanningComplete
}

fun WorkoutSessionEntity.completedDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    completedAt?.let { instant ->
        completedDateEpochDay?.let(LocalDate::ofEpochDay)
            ?: Instant.ofEpochMilli(instant).atZone(zoneId).toLocalDate()
    }

fun WeightEntryEntity.loggedDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    loggedDateEpochDay?.let(LocalDate::ofEpochDay)
        ?: Instant.ofEpochMilli(loggedAt).atZone(zoneId).toLocalDate()

fun Iterable<FoodEntryEntity>.qualifiesCalorieTarget(target: Int): Boolean {
    require(target > 0) { "Calorie target must be positive." }
    val totalMilliUnits = fold(0L) { total, entry ->
        if (entry.calorieMilliUnits > Long.MAX_VALUE - total) Long.MAX_VALUE else total + entry.calorieMilliUnits
    }
    return totalMilliUnits in target * 900L..target * 1_100L
}

object HabitTracker {
    fun days(
        start: LocalDate,
        endInclusive: LocalDate,
        asOf: LocalDate,
        zoneId: ZoneId,
        foodEntries: List<FoodEntryEntity>,
        workoutSessions: List<WorkoutSessionEntity>,
        weekdayCalorieTarget: Int,
        weekendCalorieTarget: Int,
    ): List<HabitDay> {
        require(!endInclusive.isBefore(start)) { "Habit calendar end must not precede start." }
        require(weekdayCalorieTarget > 0 && weekendCalorieTarget > 0) { "Calorie targets must be positive." }
        val entriesByDate = foodEntries.groupBy { it.loggedDate(zoneId) }
        val workoutDates = workoutSessions.mapNotNull { it.completedDate(zoneId) }
            .filterNot { it.isAfter(asOf) }
            .toSet()
        return generateSequence(start) { date -> date.plusDays(1).takeUnless { it.isAfter(endInclusive) } }
            .map { date ->
                val defaultTarget = if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                    weekendCalorieTarget
                } else {
                    weekdayCalorieTarget
                }
                val entries = entriesByDate[date].orEmpty()
                val target = if (date.isBefore(asOf)) {
                    entries.maxByOrNull { it.loggedAt }?.calorieTarget ?: defaultTarget
                } else {
                    defaultTarget
                }
                val calories = entries.totalCalories()
                val status = when {
                    date.isAfter(asOf) -> MealDayStatus.FUTURE
                    entries.qualifiesCalorieTarget(target) -> MealDayStatus.QUALIFIED
                    date == asOf -> MealDayStatus.IN_PROGRESS
                    else -> MealDayStatus.MISSED
                }
                HabitDay(date, calories, target, status, date in workoutDates)
            }
            .toList()
    }

    fun week(
        weekStart: LocalDate,
        asOf: LocalDate,
        zoneId: ZoneId,
        foodEntries: List<FoodEntryEntity>,
        workoutSessions: List<WorkoutSessionEntity>,
        weightEntries: List<WeightEntryEntity>,
        preparedMeals: List<PreparedMealEntity>,
        weekdayCalorieTarget: Int,
        weekendCalorieTarget: Int,
    ): HabitWeek {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Habit week must start on Monday." }
        val weekEnd = weekStart.plusDays(6)
        val days = days(
            weekStart,
            weekEnd,
            asOf,
            zoneId,
            foodEntries,
            workoutSessions,
            weekdayCalorieTarget,
            weekendCalorieTarget,
        )
        val qualifiedMealDays = days.count { it.mealStatus == MealDayStatus.QUALIFIED }
        val workoutDays = days.count { it.workoutCompleted }
        val weighInComplete = weightEntries.any {
            val date = it.loggedDate(zoneId)
            date in weekStart..weekEnd && !date.isAfter(asOf)
        }
        val mealPlanningComplete = preparedMeals.any {
            it.weekStartEpochDay == weekStart.toEpochDay() && it.plannedPortions > 0
        }
        val complete = qualifiedMealDays == 7 && workoutDays >= 3 && weighInComplete && mealPlanningComplete
        val status = when {
            weekStart.isAfter(asOf) -> WeekHabitStatus.FUTURE
            complete -> WeekHabitStatus.SUCCESS
            weekEnd.isBefore(asOf) -> WeekHabitStatus.MISSED
            else -> WeekHabitStatus.IN_PROGRESS
        }
        return HabitWeek(
            weekStart,
            days,
            qualifiedMealDays,
            workoutDays,
            weighInComplete,
            mealPlanningComplete,
            status,
        )
    }
}
