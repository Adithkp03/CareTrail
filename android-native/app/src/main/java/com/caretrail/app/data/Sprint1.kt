package com.caretrail.app.data

import kotlinx.serialization.Serializable

/**
 * Sprint 1 (MVP) of the Health-a-thon clinical PRD: structured onboarding intake,
 * first-visit registration checklist, Rh isoimmunization decision engine and the
 * 28-week danger-sign checklist.
 *
 * Everything is stored locally on the phone (SharedPreferences JSON, per journey)
 * so it works fully offline. Every record carries `updated_at` and `pending_sync`
 * so a backend sync endpoint can pick it up later without a schema change.
 */

fun nowIso(): String = java.time.Instant.now().toString()

@Serializable
data class HealthIntake(
    val conditions: List<String> = emptyList(),   // hypertension, diabetes, thyroid
    val surgeries: List<String> = emptyList(),    // previous_cesarean, myomectomy
    val other_history: String = "",
    val allergies: String = "",
    val completed: Boolean = false,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class RegistrationData(
    val hiv_mother: Boolean = false,
    val hiv_partner: Boolean = false,
    val vdrl_mother: Boolean = false,
    val vdrl_partner: Boolean = false,
    val hplc_scheduled: Boolean = false,
    val hplc_done: Boolean = false,
    val registered: Boolean = false,
    val facility: String = "",
    val asha_name: String = "",
    val asha_phone: String = "",
    val vhn_name: String = "",
    val vhn_phone: String = "",
    val mcp_number: String = "",
    val mcp_images: List<String> = emptyList(),   // local file paths in the app locker
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class IctRecord(
    val date: String = "",
    val weeks: Int = 0,          // gestational age when the test was logged
    val result: String = "",     // negative / positive
    val note: String = "",
)

@Serializable
data class RhProfile(
    val maternal_group: String = "",   // A+, A-, B+, B-, AB+, AB-, O+, O- or ""
    val partner_group: String = "",
    val ict_log: List<IctRecord> = emptyList(),
    val anti_d_given: Boolean = false,
    val anti_d_date: String = "",
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class DangerLog(
    val date: String = "",
    val weeks: Int = 0,
    val symptoms: List<String> = emptyList(),
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

/** Rh isoimmunization decision engine (PRD section 1). Pure logic, unit-tested. */
object RhEngine {
    val GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    fun isNegative(group: String): Boolean = group.trim().endsWith("-")
    fun isPositive(group: String): Boolean = group.trim().endsWith("+")

    enum class Status { RECORD_MATERNAL, NO_RISK, PROMPT_PARTNER, PARTNER_NEGATIVE, TRACK_ICT }

    fun status(p: RhProfile): Status = when {
        !isNegative(p.maternal_group) && !isPositive(p.maternal_group) -> Status.RECORD_MATERNAL
        isPositive(p.maternal_group) -> Status.NO_RISK
        !isNegative(p.partner_group) && !isPositive(p.partner_group) -> Status.PROMPT_PARTNER
        isNegative(p.partner_group) -> Status.PARTNER_NEGATIVE
        else -> Status.TRACK_ICT
    }

    data class Action(val key: String, val due: Boolean, val done: Boolean)

    /**
     * ICT tracking plan for a confirmed Rh-negative mother with an Rh-positive partner:
     * an initial ICT, a repeat ICT at 28 weeks, and Anti-D prophylaxis at 28 weeks.
     */
    fun actions(p: RhProfile, weeks: Int): List<Action> {
        if (status(p) != Status.TRACK_ICT) return emptyList()
        val initialDone = p.ict_log.isNotEmpty()
        val repeatDone = p.ict_log.any { it.weeks >= 28 }
        return listOf(
            Action("ict_initial", due = true, done = initialDone),
            Action("ict_28w", due = weeks >= 28, done = repeatDone),
            Action("anti_d_28w", due = weeks >= 28, done = p.anti_d_given),
        )
    }

    /** Open actions that are due now - used for the home attention hint. */
    fun openDueActions(p: RhProfile, weeks: Int): Int = actions(p, weeks).count { it.due && !it.done }
}

/** Preeclampsia / acute obstetric danger signs logged from 28 weeks (PRD section 2.3). */
object DangerSigns {
    const val START_WEEK = 28

    val PREECLAMPSIA = listOf(
        "severe_headache" to "Severe headache that does not go away",
        "blurry_vision" to "Blurry or altered vision",
        "excessive_vomiting" to "Excessive nausea or vomiting",
        "epigastric_ruq_pain" to "Pain in the upper right belly or below the ribs",
        "sudden_swelling" to "Sudden swelling of the face, hands or legs",
        "reduced_urine" to "Passing much less urine than usual",
    )

    fun symptomLabel(key: String): String = PREECLAMPSIA.firstOrNull { it.first == key }?.second ?: key

    /** Any selected red flag means high-priority triage: contact the doctor now. */
    fun urgent(selected: List<String>): Boolean = selected.isNotEmpty()
}

// ---- Local persistence (offline-first, sync-ready) ---------------------------

private fun Store.getS1(key: String): String? = getCache("s1:$key")
private fun Store.putS1(key: String, json: String) = putCache("s1:$key", json)

fun Store.healthIntake(journeyId: String): HealthIntake =
    getS1("intake:$journeyId")?.let { try { AppJson.decodeFromString<HealthIntake>(it) } catch (_: Exception) { null } } ?: HealthIntake()

fun Store.saveHealthIntake(journeyId: String, v: HealthIntake) =
    putS1("intake:$journeyId", AppJson.encodeToString(HealthIntake.serializer(), v.copy(updated_at = nowIso())))

fun Store.registration(journeyId: String): RegistrationData =
    getS1("registration:$journeyId")?.let { try { AppJson.decodeFromString<RegistrationData>(it) } catch (_: Exception) { null } } ?: RegistrationData()

fun Store.saveRegistration(journeyId: String, v: RegistrationData) =
    putS1("registration:$journeyId", AppJson.encodeToString(RegistrationData.serializer(), v.copy(updated_at = nowIso())))

fun Store.rhProfile(journeyId: String): RhProfile =
    getS1("rh:$journeyId")?.let { try { AppJson.decodeFromString<RhProfile>(it) } catch (_: Exception) { null } } ?: RhProfile()

fun Store.saveRhProfile(journeyId: String, v: RhProfile) =
    putS1("rh:$journeyId", AppJson.encodeToString(RhProfile.serializer(), v.copy(updated_at = nowIso())))

fun Store.dangerLogs(journeyId: String): List<DangerLog> =
    getS1("danger:$journeyId")?.let { try { AppJson.decodeFromString<List<DangerLog>>(it) } catch (_: Exception) { null } } ?: emptyList()

fun Store.saveDangerLogs(journeyId: String, logs: List<DangerLog>) =
    putS1("danger:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(DangerLog.serializer()), logs))
