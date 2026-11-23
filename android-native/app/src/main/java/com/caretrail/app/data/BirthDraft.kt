package com.caretrail.app.data

import kotlinx.serialization.Serializable

/** Raw text stays raw, including incomplete numbers. A draft never confirms a birth. */
@Serializable
data class BirthDraft(
    val date: String = "", val time: String = "", val gender: String = "",
    val weeks: String = "", val days: String = "", val weight: String = "",
    val head: String = "", val length: String = "",
) {
    companion object {
        fun from(record: BirthRecord) = BirthDraft(record.date, record.time, record.gender,
            record.ga_weeks.toString().takeIf { record.ga_weeks > 0 } ?: "",
            record.ga_days.toString().takeIf { record.ga_weeks > 0 } ?: "",
            record.weight_kg.toString().takeIf { record.weight_kg > 0 } ?: "",
            record.head_cm.toString().takeIf { record.head_cm > 0 } ?: "",
            record.length_cm.toString().takeIf { record.length_cm > 0 } ?: "")
    }
}

fun Store.birthDraft(journeyId: String): BirthDraft? = getCache("birth-draft:$journeyId")?.let {
    // Corruption must be visible, not silently replaced with an empty draft.
    AppJson.decodeFromString(BirthDraft.serializer(), it)
}
fun Store.saveBirthDraft(journeyId: String, value: BirthDraft) = putCachesDurable(
    mapOf("birth-draft:$journeyId" to AppJson.encodeToString(BirthDraft.serializer(), value)))
fun Store.discardBirthDraft(journeyId: String) = putCachesDurable(emptyMap(), setOf("birth-draft:$journeyId"))

/** Confirmed record + optional initial growth row + removal of its draft are one edit. */
fun Store.confirmBirthDraft(journeyId: String, record: BirthRecord) {
    val values = mutableMapOf("s2:birth:$journeyId" to AppJson.encodeToString(BirthRecord.serializer(),
        record.copy(confirmed = true, updated_at = nowIso(), pending_sync = true)))
    val entries = growthEntries(journeyId)
    if ((record.weight_kg > 0 || record.length_cm > 0 || record.head_cm > 0) && entries.none { it.age_months == 0.0 }) {
        values["s2:growth:$journeyId"] = AppJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(GrowthEntry.serializer()),
            entries + GrowthEntry(date = record.date, age_months = 0.0, weight_kg = record.weight_kg,
                length_cm = record.length_cm, head_cm = record.head_cm))
    }
    putCachesDurable(values, setOf("birth-draft:$journeyId"))
}
