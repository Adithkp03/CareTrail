package com.caretrail.app.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Sprint 5 of the Health-a-thon clinical PRD: inter-facility referral transfer
 * (relocating across districts/states), ABHA capture for ABDM alignment, with explicit patient-controlled exports.
 *
 * The referral package is a checksum-protected (SHA-256) JSON document the mother
 * carries to the receiving facility: share as a file for a receiving clinician to review. No server round-trip is required, so it works fully offline.
 */

@Serializable
data class ReferralPackage(
    val kind: String = "caretrail-referral",
    val version: Int = 1,
    val created_at: String = "",
    val patient_name: String = "",
    val abha_number: String = "",
    val intake: HealthIntake? = null,
    val registration: RegistrationData? = null,
    val rh: RhProfile? = null,
    val danger_logs: List<DangerLog> = emptyList(),
    val birth: BirthRecord? = null,
    val growth: List<GrowthEntry> = emptyList(),
    val vaccines: Map<String, VaccineLog> = emptyMap(),
    val bp: List<BpReading> = emptyList(),
    val sugar: List<SugarReading> = emptyList(),
    val high_risk: HighRiskRecord? = null,
    val checksum: String = "",
)

object Referral {
    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun encode(p: ReferralPackage): String =
        AppJson.encodeToString(ReferralPackage.serializer(), p)

    fun build(
        patientName: String,
        intake: HealthIntake,
        registration: RegistrationData,
        rh: RhProfile,
        dangerLogs: List<DangerLog>,
        birth: BirthRecord,
        growth: List<GrowthEntry>,
        vaccines: Map<String, VaccineLog>,
        bp: List<BpReading>,
        sugar: List<SugarReading>,
        highRisk: HighRiskRecord,
    ): ReferralPackage {
        val base = ReferralPackage(
            created_at = nowIso(),
            patient_name = patientName,
            abha_number = intake.abha_number,
            intake = intake.takeIf { it.completed },
            registration = registration.copy(mcp_images = emptyList()).takeIf { it.updated_at.isNotEmpty() },
            rh = rh.takeIf { it.updated_at.isNotEmpty() },
            danger_logs = dangerLogs,
            birth = birth.takeIf { it.confirmed },
            growth = growth,
            vaccines = vaccines,
            bp = bp,
            sugar = sugar,
            high_risk = highRisk.takeIf { it.any() },
        )
        return base.copy(checksum = sha256(encode(base)))
    }

    /** Detect accidental changes. This is not a signature and does not prove authorship. */
    fun verify(p: ReferralPackage): Boolean = sha256(encode(p.copy(checksum = ""))) == p.checksum

    fun parse(json: String): ReferralPackage? =
        runCatching { AppJson.decodeFromString<ReferralPackage>(json) }.getOrNull()
            ?.takeIf { it.kind == "caretrail-referral" && it.version == 1 && verify(it) }

    /** Compact human/clinician-readable referral summary. */
    fun summaryText(p: ReferralPackage): String {
        val sb = StringBuilder("CARETRAIL REFERRAL SUMMARY\n")
        sb.append("Patient: ").append(p.patient_name).append('\n')
        if (p.abha_number.isNotEmpty()) sb.append("ABHA: ").append(p.abha_number).append('\n')
        p.rh?.let { if (it.maternal_group.isNotEmpty()) sb.append("Blood group: ").append(it.maternal_group).append(if (it.anti_d_given) " (Anti-D given ${it.anti_d_date})" else "").append('\n') }
        p.registration?.let { if (it.mcp_number.isNotEmpty()) sb.append("MCP no: ").append(it.mcp_number).append('\n') }
        p.intake?.let {
            if (it.conditions.isNotEmpty()) sb.append("Conditions: ").append(it.conditions.joinToString(", ")).append('\n')
            if (it.allergies.isNotEmpty()) sb.append("Allergies: ").append(it.allergies).append('\n')
        }
        p.high_risk?.let { if (it.any()) sb.append("HIGH RISK: ").append(listOfNotNull(
            if (it.gest_hypertension) "gestational hypertension" else null,
            if (it.preeclampsia) "preeclampsia" else null,
            if (it.gdm_insulin) "GDM on insulin" else null,
        ).joinToString(", ")).append('\n') }
        p.birth?.let { sb.append("Delivered: ").append(it.date).append(" at ").append(it.ga_weeks).append("+").append(it.ga_days).append(" weeks, ").append(it.gender).append(", ").append(it.weight_kg).append(" kg\n") }
        if (p.danger_logs.isNotEmpty()) sb.append("Danger-sign episodes: ").append(p.danger_logs.size).append('\n')
        if (p.bp.isNotEmpty()) sb.append("BP readings: ").append(p.bp.size).append(", last ").append(p.bp.last().let { "${it.systolic}/${it.diastolic} on ${it.date}" }).append('\n')
        sb.append("Records: growth ").append(p.growth.size).append(", vaccines ").append(p.vaccines.values.count { it.done }).append(" done").append('\n')
        sb.append("Integrity: ").append(p.checksum.take(12))
        return sb.toString()
    }
}

