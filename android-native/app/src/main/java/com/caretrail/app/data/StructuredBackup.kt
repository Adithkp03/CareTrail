package com.caretrail.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable data class BackupRecord(val module: String, val revision: Int, val schema_version: Int = 1,
    val deleted: Boolean = false, val data: JsonElement? = null, val updated_at: String = "", val operation_id: String = "")
@Serializable data class BackupListing(val journey_id: String, val records: List<BackupRecord>)
@Serializable data class BackupWrite(val expected_revision: Int, val operation_id: String,
    val schema_version: Int = 1, val consent: Boolean, val deleted: Boolean = false, val data: JsonElement? = null)

/** Explicit module allowlist. No tokens, report files, translations or generic cache export. */
object StructuredBackup {
    val modules = linkedMapOf("health" to "Health profile", "registration" to "First-visit checklist",
        "rh" to "Rh details", "danger" to "Danger-sign log", "birth" to "Birth record", "growth" to "Growth log",
        "vaccines" to "Vaccination record", "bp" to "BP log", "sugar" to "Sugar log",
        "high-risk" to "High-risk details", "follow-ups" to "Follow-up record", "mood" to "Mood entries (optional)",
        "birth-draft" to "Unfinished birth form (optional)")
    private val prefixes = mapOf("health" to "s1:intake", "registration" to "s1:registration", "rh" to "s1:rh",
        "danger" to "s1:danger", "birth" to "s2:birth", "growth" to "s2:growth", "vaccines" to "s2:vaccines",
        "bp" to "s4:bp", "sugar" to "s4:sugar", "high-risk" to "s4:highrisk", "follow-ups" to "s4:followups",
        "mood" to "s4:mood", "birth-draft" to "birth-draft")
    private fun key(jid: String, module: String) = "${requireNotNull(prefixes[module])}:$jid"
    fun local(store: Store, jid: String, module: String): String? = store.getCache(key(jid, module))?.let { raw ->
        if (module == "registration") {
            val value = AppJson.parseToJsonElement(raw).jsonObject
            JsonObject(value.filterKeys { it != "mcp_images" }).toString()
        } else raw
    }
    private fun valid(module: String, raw: String) {
        when (module) {
            "health" -> AppJson.decodeFromString<HealthIntake>(raw)
            "registration" -> AppJson.decodeFromString<RegistrationData>(raw)
            "rh" -> AppJson.decodeFromString<RhProfile>(raw)
            "danger" -> AppJson.decodeFromString<List<DangerLog>>(raw)
            "birth" -> AppJson.decodeFromString<BirthRecord>(raw)
            "growth" -> AppJson.decodeFromString<List<GrowthEntry>>(raw)
            "vaccines" -> AppJson.decodeFromString<Map<String, VaccineLog>>(raw)
            "bp" -> AppJson.decodeFromString<List<BpReading>>(raw)
            "sugar" -> AppJson.decodeFromString<List<SugarReading>>(raw)
            "high-risk" -> AppJson.decodeFromString<HighRiskRecord>(raw)
            "follow-ups" -> AppJson.decodeFromString<FollowUpDone>(raw)
            "mood" -> AppJson.decodeFromString<List<MoodCheckin>>(raw)
            "birth-draft" -> AppJson.decodeFromString<BirthDraft>(raw)
            else -> error("Unsupported module")
        }
    }
    suspend fun preview(api: Api, jid: String) = api.get("/journeys/$jid/local-backup", BackupListing.serializer()).also {
        check(it.journey_id == jid) { "Backup belongs to another journey" }
    }
    suspend fun upload(api: Api, jid: String, module: String, revision: Int, raw: String, operation: String): BackupRecord {
        valid(module, raw)
        val request = BackupWrite(revision, operation, consent = true, data = AppJson.parseToJsonElement(raw))
        return api.send("PUT", "/journeys/$jid/local-backup/$module", AppJson.encodeToString(BackupWrite.serializer(), request), BackupRecord.serializer())
    }
    suspend fun remove(api: Api, jid: String, record: BackupRecord): BackupRecord {
        require(record.module in modules)
        return api.send("PUT", "/journeys/$jid/local-backup/${record.module}",
            AppJson.encodeToString(BackupWrite.serializer(), BackupWrite(record.revision, UUID.randomUUID().toString(), consent = true, deleted = true)), BackupRecord.serializer())
    }
    /** Never replaces a differing local copy. Delete locally or reconcile it in its form first. */
    fun restore(store: Store, jid: String, record: BackupRecord) {
        require(record.schema_version == 1 && !record.deleted && record.module in modules)
        val raw = requireNotNull(record.data).toString()
        valid(record.module, raw)
        val old = store.getCache(key(jid, record.module))
        check(old == null || AppJson.parseToJsonElement(old) == record.data) { "Different local record exists. It was kept; reconcile it before restore." }
        if (old == null) store.putCachesDurable(mapOf(key(jid, record.module) to raw))
    }
}
