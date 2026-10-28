package com.gymbro.local.domain

object WorkoutRecommendation {
    fun nextDay(completedDayIds: List<String>): WorkoutDay {
        val completed = completedDayIds.toSet()
        return SeedData.plan.days.firstOrNull { it.id !in completed } ?: SeedData.plan.days.first()
    }
}
