package com.caretrail.app.data

import java.time.LocalDate
import java.time.ZoneOffset

/** Pure helpers shared by screens (and unit tests). Ported from PatientHub.tsx. */
object Logic {
    val STAGES = listOf("firstTrimester", "secondTrimester", "thirdTrimester")
    val ZONE_ORDER = listOf("now", "upcoming", "next", "done")

    fun trimester(week: Int): Int = if (week < 14) 0 else if (week < 28) 1 else 2
    fun milestoneStage(m: Milestone): Int = trimester(m.window_weeks.getOrElse(0) { 0.0 }.toInt())

    val LEVEL_TEXT = mapOf(
        "self_reported" to "Reported by you",
        "evidence_confirmed" to "Backed by a report you confirmed",
        "clinician_verified" to "Verified by your doctor",
    )

    val TYPE_ICON = mapOf("visit" to "visit", "scan" to "scan", "test" to "test", "vaccination" to "vaccination", "review" to "review")
    val MILESTONE_ICONS = mapOf(
        "first_consultation" to "visit", "baseline_bloods" to "blood", "nt_scan" to "scan",
        "second_trimester_review" to "visit", "anomaly_scan" to "scan", "consultation_24w" to "visit",
        "ogtt" to "test", "tdap_vaccine" to "vaccination", "third_trimester_review" to "visit",
        "growth_scan" to "scan", "review_34w" to "visit", "review_36w" to "visit",
        "review_38w" to "visit", "birth_plan" to "hospital",
    )
    fun iconFor(m: Milestone): String = MILESTONE_ICONS[m.key] ?: TYPE_ICON[m.type] ?: "review"

    fun zones(j: Journey): Map<String, List<Milestone>> {
        val out = linkedMapOf<String, MutableList<Milestone>>("now" to mutableListOf(), "upcoming" to mutableListOf(), "next" to mutableListOf(), "done" to mutableListOf())
        j.milestones.forEach { out[it.status]?.add(it) }
        return out
    }

    /** Milestones shown up front for a trimester on the timeline (web: `highlighted`). */
    fun highlighted(items: List<Milestone>, stage: Int, currentStage: Int): List<Milestone> = when {
        stage == currentStage ->
            (items.filter { it.status == "now" || it.status == "upcoming" } + items.filter { it.status == "next" }).take(3)
        stage < currentStage -> items
        else -> items.take(1)
    }

    data class Attention(val overdueTitles: List<String>, val unresolved: List<Flag>)

    fun attention(j: Journey): Attention = Attention(
        j.milestones.filter { it.overdue && it.status != "done" }.map { it.title },
        j.flags.filter { it.signed_off == null },
    )

    fun progressFraction(j: Journey): Float {
        val total = j.milestones.size
        val done = j.summary["done"] ?: 0
        return if (total == 0) 0f else done.toFloat() / total
    }

    fun fmtValue(v: Double): String = if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

    /** Material date-picker millis (UTC midnight) to an ISO date for the API. */
    fun isoFromMillis(millis: Long): String = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
    fun millisFromIso(iso: String): Long? = try { LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } catch (_: Exception) { null }
    fun today(): String = LocalDate.now().toString()
}
