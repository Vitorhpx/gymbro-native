package com.gymbro.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PreparedMealTest {
    @Test fun `remaining portions are planned minus logged`() {
        val meal = PreparedMealEntity("prep-1", 7, LocalDate.of(2026, 10, 26).toEpochDay(), 4, 2, false, 1)
        assertEquals(2, meal.remainingPortions)
    }

    @Test fun `prepared meal validation rejects unstable and impossible values`() {
        val valid = PreparedMealEntity("prep-1", 7, 4, 2, 1, false, 1)
        assertTrue(valid.validationError() == null)
        assertTrue(valid.copy(id = " ").validationError() != null)
        assertTrue(valid.copy(savedMealId = 0).validationError() != null)
        assertTrue(valid.copy(plannedPortions = 0).validationError() != null)
        assertTrue(valid.copy(loggedPortions = 3).validationError() != null)
        assertTrue(valid.copy(weekStartEpochDay = 2).validationError() != null)
    }

    @Test fun `planning complete is derived only from active plans in requested week`() {
        val week = LocalDate.of(2026, 10, 26).toEpochDay()
        val meals = listOf(
            PreparedMealEntity("old", 7, week - 7, 2, 0, false, 1),
            PreparedMealEntity("archived", 7, week, 2, 0, true, 1),
        )
        assertFalse(meals.planningCompleteFor(week))
        assertTrue((meals + PreparedMealEntity("current", 7, week, 1, 1, false, 1)).planningCompleteFor(week))
    }

    @Test fun `prepared pool accepts only explicit dates in its Monday through Sunday week`() {
        val monday = LocalDate.of(2026, 10, 26)
        val pool = PreparedMealEntity("prep-1", 7, monday.toEpochDay(), 4, 0, false, 1)
        assertFalse(pool.containsLogDate(monday.minusDays(1).toEpochDay()))
        assertTrue(pool.containsLogDate(monday.toEpochDay()))
        assertTrue(pool.containsLogDate(monday.plusDays(6).toEpochDay()))
        assertFalse(pool.containsLogDate(monday.plusDays(7).toEpochDay()))
    }

    @Test fun `dated food filtering has an exclusive next-day bound`() {
        val date = LocalDate.of(2026, 10, 29)
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entries = listOf(
            FoodEntryEntity(1, "before", "Before", 1, 1, 1, start - 1),
            FoodEntryEntity(2, "today", "Today", 1, 1, 1, start),
            FoodEntryEntity(3, "future", "Future", 1, 1, 1, start + 86_400_000),
        )
        assertEquals(listOf("Today"), entries.loggedOn(date).map { it.name })
    }

    @Test fun `prepared log date remains stable after timezone change`() {
        val selectedDate = LocalDate.of(2026, 11, 1)
        val loggedAt = selectedDate.atStartOfDay(ZoneId.of("Pacific/Honolulu")).toInstant().toEpochMilli()
        val entry = FoodEntryEntity(
            1, "rice", "Rice", 100, 1, 1, loggedAt, MealSlot.LUNCH,
            "prep", "log", selectedDate.toEpochDay(),
        )

        assertEquals(selectedDate, entry.loggedDate(ZoneId.of("Pacific/Kiritimati")))
        assertEquals(listOf(entry), listOf(entry).loggedOn(selectedDate, ZoneId.of("Pacific/Kiritimati")))
    }

    @Test fun `prepared week is bounded to dates safely convertible to epoch milliseconds`() {
        val tooLate = PreparedMealEntity(
            "too-late", 1, LocalDate.MAX.minusDays(6).toEpochDay(), 1, 0, false, 0,
        )
        val latestSupportedMonday = LocalDate.of(9999, 12, 20)
        val supported = tooLate.copy(id = "supported", weekStartEpochDay = latestSupportedMonday.toEpochDay())

        assertEquals("Prepared meal week is outside the supported date range.", tooLate.validationError())
        assertEquals(null, supported.validationError())
        supported.let {
            latestSupportedMonday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    @Test fun `dated history exposes every date without truncation`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 29)
        val entries = (1L..15L).map { offset ->
            val date = today.minusDays(offset)
            FoodEntryEntity(offset, "food-$offset", "Food $offset", 1, 1, 1,
                date.atStartOfDay(zone).toInstant().toEpochMilli())
        }

        val history = entries.datedHistory(today, zone)

        assertEquals(15, history.size)
        assertEquals(today.minusDays(1), history.keys.first())
        assertEquals(today.minusDays(15), history.keys.last())
    }

    @Test fun `all non archived pools remain reachable across weeks`() {
        val pools = listOf(
            PreparedMealEntity("current", 7, 20_388, 4, 0, false, 3),
            PreparedMealEntity("older", 7, 20_381, 2, 0, false, 2),
            PreparedMealEntity("archived", 7, 20_374, 2, 0, true, 1),
        )
        assertEquals(listOf("current", "older"), pools.reachablePools().map { it.id })
    }

    @Test fun `prepared origin rows are not removable from UI`() {
        val regular = FoodEntryEntity(1, "rice", "Rice", 100, 1, 1, 1)
        val prepared = regular.copy(preparedMealId = "prep", preparedLogId = "log")
        assertTrue(regular.isRemovableFromUi)
        assertFalse(prepared.isRemovableFromUi)
    }
}
