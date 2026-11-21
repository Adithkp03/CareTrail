package com.caretrail.app

import com.caretrail.app.data.MedicationReminder
import com.caretrail.app.data.HydrationReminder
import com.caretrail.app.data.ReminderTimes
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderTimesTest {
    private val medication = MedicationReminder("test", "Prescribed tablet", start = "2026-11-21", times = listOf("08:00", "20:00"))
    @Test fun choosesNextDoseToday() {
        assertEquals(LocalDateTime.parse("2026-11-21T20:00"), ReminderTimes.next(medication, LocalDateTime.parse("2026-11-21T08:01")))
    }
    @Test fun respectsFutureStart() {
        assertEquals(LocalDateTime.parse("2026-12-01T08:00"), ReminderTimes.next(medication.copy(start = "2026-12-01"), LocalDateTime.parse("2026-11-21T08:01")))
    }
    @Test fun endDateIsInclusive() {
        val ending = medication.copy(end = "2026-11-21")
        assertNotNull(ReminderTimes.next(ending, LocalDateTime.parse("2026-11-21T19:00")))
        assertNull(ReminderTimes.next(ending, LocalDateTime.parse("2026-11-21T20:01")))
    }
    @Test fun skipsUnselectedDays() {
        assertEquals(LocalDateTime.parse("2026-11-23T08:00"), ReminderTimes.next(medication.copy(weekdays = listOf(1)), LocalDateTime.parse("2026-11-21T07:00")))
    }
    @Test fun pausedMedicationHasNoAlarm() {
        assertNull(ReminderTimes.next(medication.copy(enabled = false), LocalDateTime.parse("2026-11-21T07:00")))
    }
    @Test fun hydrationWaitsUntilMorning() {
        assertEquals(LocalDateTime.parse("2026-11-22T08:00"), ReminderTimes.nextHydration(HydrationReminder(enabled = true), LocalDateTime.parse("2026-11-21T20:00")))
    }
    @Test fun hydrationUsesIntervalFromStart() {
        assertEquals(LocalDateTime.parse("2026-11-21T10:00"), ReminderTimes.nextHydration(HydrationReminder(enabled = true), LocalDateTime.parse("2026-11-21T08:01")))
    }
    @Test fun schedulesStartBeyondSevenDays() {
        val later = medication.copy(start = "2026-12-20", times = listOf("08:00"))
        assertEquals(LocalDateTime.parse("2026-12-20T08:00"), ReminderTimes.next(later, LocalDateTime.parse("2026-11-21T08:01")))
    }
    @Test fun dosesOnRespectsWindowAndWeekdays() {
        val day = LocalDate.parse("2026-11-21")
        assertEquals(2, ReminderTimes.dosesOn(medication, day).size)
        assertTrue(ReminderTimes.dosesOn(medication.copy(weekdays = listOf(1)), day).isEmpty())
        assertTrue(ReminderTimes.dosesOn(medication.copy(end = "2026-11-20"), day).isEmpty())
        assertTrue(ReminderTimes.dosesOn(medication.copy(enabled = false), day).isEmpty())
    }
    @Test fun hydrationRejectsDisabledAndInvalidWindows() {
        val now = LocalDateTime.parse("2026-11-21T08:01")
        assertNull(ReminderTimes.nextHydration(HydrationReminder(), now))
        assertNull(ReminderTimes.nextHydration(HydrationReminder(enabled = true, start = "20:00", end = "08:00"), now))
    }
}

