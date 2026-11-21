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
)

object ReminderTimes {
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
