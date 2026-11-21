package com.caretrail.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class MedicationReminder(
    val id: String,
    val name: String,
    val form: String = "Tablet",
    val dose: String = "1",
    val times: List<String> = listOf("08:00"),
    val start: String = LocalDate.now().toString(),
    val end: String = "",
    val weekdays: List<Int> = (1..7).toList(),
    val instructions: String = "",
    val refillDate: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class DailyReminderPlan(
    val setupDone: Boolean = false,
    val medications: List<MedicationReminder> = emptyList(),
    val hydration: HydrationReminder = HydrationReminder(),
)

@Serializable
data class HydrationReminder(
    val enabled: Boolean = false,
    val start: String = "08:00",
    val end: String = "20:00",
    val intervalMinutes: Int = 120,
)

object ReminderTimes {
    fun nextHydration(h: HydrationReminder, now: LocalDateTime): LocalDateTime? {
        if (!h.enabled || h.intervalMinutes < 30) return null
        val start = LocalTime.parse(h.start)
        val end = LocalTime.parse(h.end)
        if (end <= start) return null
        for (offset in 0L..1L) {
            val date = now.toLocalDate().plusDays(offset)
            var time = date.atTime(start)
            while (time < date.atTime(end)) {
                if (time > now) return time
                time = time.plusMinutes(h.intervalMinutes.toLong())
            }
        }
        return null
    }

    fun next(m: MedicationReminder, now: LocalDateTime): LocalDateTime? {
        if (!m.enabled || m.weekdays.isEmpty()) return null
        val start = LocalDate.parse(m.start)
        val end = m.end.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        val first = maxOf(now.toLocalDate(), start)
        return (0L..7L).flatMap { offset ->
            val date = first.plusDays(offset)
            if ((end != null && date > end) || date.dayOfWeek.value !in m.weekdays) emptyList()
            else m.times.map { date.atTime(LocalTime.parse(it)) }
        }.filter { it > now }.minOrNull()
    }
}
