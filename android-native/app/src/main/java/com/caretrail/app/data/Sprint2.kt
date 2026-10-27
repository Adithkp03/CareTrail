package com.caretrail.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sprint 2 of the Health-a-thon clinical PRD: postpartum transition + birth intake,
 * WHO growth standards engine, pediatric immunization scheduler and the report vault.
 * Same offline-first, sync-ready shape as Sprint 1 (updated_at + pending_sync).
 */

@Serializable
data class BirthRecord(
    val confirmed: Boolean = false,
    val ga_weeks: Int = 0,
    val ga_days: Int = 0,
    val date: String = "",          // date of delivery, ISO
    val time: String = "",          // exact time of birth, HH:MM
    val gender: String = "",        // boy / girl
    val weight_kg: Double = 0.0,
    val head_cm: Double = 0.0,
    val length_cm: Double = 0.0,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class GrowthEntry(
    val date: String = "",          // ISO date of measurement
    val age_months: Double = 0.0,   // derived from the birth date at save time
    val weight_kg: Double = 0.0,
    val length_cm: Double = 0.0,
    val head_cm: Double = 0.0,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class VaccineLog(
    val key: String = "",
    val done: Boolean = false,
    val date: String = "",
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class VaultEntry(
    val filename: String = "",
    val uploaded_at: String = "",
    val source: String = "",        // milestone title or "vault"
    val updated_at: String = "",
)

/** WHO LMS math: z-scores, percentile curves and band classification. Pure, unit-tested. */
object WhoGrowth {
    private fun lmsAt(table: List<WhoLms.P>, ageMonths: Double): Triple<Double, Double, Double>? {
        if (table.isEmpty() || ageMonths < table.first().ageMonths || ageMonths > table.last().ageMonths) return null
        var prev = table.first()
        for (p in table) {
            if (p.ageMonths.toDouble() == ageMonths) return Triple(p.l.toDouble(), p.m.toDouble(), p.s.toDouble())
            if (p.ageMonths > ageMonths) {
                val span = (p.ageMonths - prev.ageMonths).toDouble()
                val t = if (span == 0.0) 0.0 else (ageMonths - prev.ageMonths) / span
                fun lerp(a: Float, b: Float) = a + (b - a) * t
                return Triple(lerp(prev.l, p.l), lerp(prev.m, p.m), lerp(prev.s, p.s))
            }
            prev = p
        }
        val last = table.last()
        return Triple(last.l.toDouble(), last.m.toDouble(), last.s.toDouble())
    }

    /** Measurement at a z-score: X = M(1 + LSZ)^(1/L), or M * exp(SZ) when L = 0. */
    fun valueAt(l: Double, m: Double, s: Double, z: Double): Double =
        if (l != 0.0) m * Math.pow(1 + l * s * z, 1 / l) else m * Math.exp(s * z)

    /** Z-score of a measurement at an age, or null when outside the tabulated 0-24 months. */
    fun zScore(table: List<WhoLms.P>, ageMonths: Double, x: Double): Double? {
        if (x <= 0.0) return null
        val (l, m, s) = lmsAt(table, ageMonths) ?: return null
        return if (l != 0.0) (Math.pow(x / m, l) - 1) / (l * s) else Math.log(x / m) / s
    }

    /** Percentile curve values for the chart, at every tabulated age. */
    fun curves(table: List<WhoLms.P>, zs: List<Double> = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)): List<Pair<Float, List<Double>>> =
        table.map { p -> p.ageMonths to zs.map { z -> valueAt(p.l.toDouble(), p.m.toDouble(), p.s.toDouble(), z) } }

    enum class Band { BELOW, ON_TRACK, ABOVE }
    fun band(z: Double?): Band = when {
        z == null -> Band.ON_TRACK
        z < -2.0 -> Band.BELOW
        z > 2.0 -> Band.ABOVE
        else -> Band.ON_TRACK
    }

    fun tableFor(indicator: String, gender: String): List<WhoLms.P> = when (indicator) {
        "weight" -> if (gender == "girl") WhoLms.weightGirls else WhoLms.weightBoys
        "length" -> if (gender == "girl") WhoLms.lengthGirls else WhoLms.lengthBoys
        else -> if (gender == "girl") WhoLms.headGirls else WhoLms.headBoys
    }

    fun ageMonths(birthDate: String, onDate: String): Double? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(birthDate), LocalDate.parse(onDate)) / 30.4375
    } catch (_: Exception) { null }
}

/** Pediatric immunization schedule from the birth date (India Universal Immunization Programme). */
object VaccineSchedule {
    class Item(val key: String, val label: String, val dueDays: Int, val windowNote: String)

    val ITEMS = listOf(
        Item("bcg", "BCG", 0, "At birth"),
        Item("opv0", "OPV-0", 0, "At birth"),
        Item("hepb0", "Hepatitis B birth dose", 0, "Within 24 hours of birth"),
        Item("penta1", "Pentavalent 1 (DPT + HepB + Hib)", 42, "6 weeks"),
        Item("opv1", "OPV 1", 42, "6 weeks"),
        Item("rota1", "Rotavirus 1", 42, "6 weeks"),
        Item("pcv1", "PCV 1", 42, "6 weeks"),
        Item("fipv1", "fIPV 1", 42, "6 weeks"),
        Item("penta2", "Pentavalent 2", 70, "10 weeks"),
        Item("opv2", "OPV 2", 70, "10 weeks"),
        Item("rota2", "Rotavirus 2", 70, "10 weeks"),
        Item("penta3", "Pentavalent 3", 98, "14 weeks"),
        Item("opv3", "OPV 3", 98, "14 weeks"),
        Item("rota3", "Rotavirus 3", 98, "14 weeks"),
        Item("fipv2", "fIPV 2", 98, "14 weeks"),
        Item("mr1", "MR 1 (Measles-Rubella)", 274, "9-12 months"),
        Item("je1", "JE 1 (in JE districts)", 274, "9-12 months"),
        Item("pcv_boost", "PCV booster", 274, "9-12 months"),
        Item("vita1", "Vitamin A first dose", 274, "9-12 months"),
        Item("dptb1", "DPT booster 1", 487, "16-24 months"),
        Item("opvb", "OPV booster", 487, "16-24 months"),
        Item("mr2", "MR 2", 487, "16-24 months"),
        Item("je2", "JE 2 (in JE districts)", 487, "16-24 months"),
    )

    fun dueDate(birthDate: String, item: Item): String? = try {
        LocalDate.parse(birthDate).plusDays(item.dueDays.toLong()).toString()
    } catch (_: Exception) { null }

    enum class State { DONE, OVERDUE, DUE_SOON, UPCOMING }

    /** due within the next 14 days, overdue after the due day passes. */
    fun state(item: Item, birthDate: String, log: VaccineLog?, today: LocalDate = LocalDate.now()): State {
        if (log?.done == true) return State.DONE
        val due = dueDate(birthDate, item) ?: return State.UPCOMING
        val d = LocalDate.parse(due)
        return when {
            today.isAfter(d) -> State.OVERDUE
            ChronoUnit.DAYS.between(today, d) <= 14 -> State.DUE_SOON
            else -> State.UPCOMING
        }
    }

    /** Next items that need action, earliest due first. */
    fun openItems(birthDate: String, logs: Map<String, VaccineLog>, today: LocalDate = LocalDate.now()): List<Pair<Item, State>> =
        ITEMS.map { it to state(it, birthDate, logs[it.key], today) }
            .filter { it.second != State.DONE }
            .sortedBy { dueDate(birthDate, it.first) ?: "9999" }
}

// ---- Local persistence ---------------------------------------------------------

private fun Store.getS2(key: String): String? = getCache("s2:$key")
private fun Store.putS2(key: String, json: String) = putCache("s2:$key", json)

fun Store.birthRecord(journeyId: String): BirthRecord =
    getS2("birth:$journeyId")?.let { try { AppJson.decodeFromString<BirthRecord>(it) } catch (_: Exception) { null } } ?: BirthRecord()

fun Store.saveBirthRecord(journeyId: String, v: BirthRecord) =
    putS2("birth:$journeyId", AppJson.encodeToString(BirthRecord.serializer(), v.copy(updated_at = nowIso())))

fun Store.growthEntries(journeyId: String): List<GrowthEntry> =
    getS2("growth:$journeyId")?.let { try { AppJson.decodeFromString<List<GrowthEntry>>(it) } catch (_: Exception) { null } } ?: emptyList()

fun Store.saveGrowthEntries(journeyId: String, v: List<GrowthEntry>) =
    putS2("growth:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(GrowthEntry.serializer()), v))

fun Store.vaccineLogs(journeyId: String): Map<String, VaccineLog> =
    getS2("vaccines:$journeyId")?.let { try { AppJson.decodeFromString<Map<String, VaccineLog>>(it) } catch (_: Exception) { null } } ?: emptyMap()

fun Store.saveVaccineLogs(journeyId: String, v: Map<String, VaccineLog>) =
    putS2("vaccines:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), VaccineLog.serializer()), v))

fun Store.vaultEntries(journeyId: String): List<VaultEntry> =
    getS2("vault:$journeyId")?.let { try { AppJson.decodeFromString<List<VaultEntry>>(it) } catch (_: Exception) { null } } ?: emptyList()

fun Store.saveVaultEntries(journeyId: String, v: List<VaultEntry>) =
    putS2("vault:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(VaultEntry.serializer()), v))
