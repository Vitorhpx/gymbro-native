package com.gymbro.local.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test fun `delivery window prevents notifications at disruptive shifted times`() {
        val evening = ZonedDateTime.of(2026, 11, 2, 19, 30, 0, 0, zone)
        val early = ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, zone)
        val overnight = ZonedDateTime.of(2026, 11, 3, 2, 0, 0, 0, zone)

        assertTrue(ReminderSchedule.isDeliveryWindow(evening))
        assertTrue(!ReminderSchedule.isDeliveryWindow(early))
        assertTrue(!ReminderSchedule.isDeliveryWindow(overnight))
    }

    @Test fun `next reminder uses today when evening time has not passed`() {
        val now = ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, zone)
        val next = ReminderSchedule.nextRun(now, LocalTime.of(19, 30))

        assertEquals(ZonedDateTime.of(2026, 11, 2, 19, 30, 0, 0, zone), next)
    }

    @Test fun `next reminder moves to tomorrow after evening time`() {
        val now = ZonedDateTime.of(2026, 11, 2, 20, 0, 0, 0, zone)
        val next = ReminderSchedule.nextRun(now, LocalTime.of(19, 30))

        assertEquals(ZonedDateTime.of(2026, 11, 3, 19, 30, 0, 0, zone), next)
        assertTrue(Duration.between(now, next).toMillis() > 0)
    }
}
