package com.caretrail.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Signoff(
    val doctor_name: String = "",
    val note: String = "",
    val signed_at: String = "",
    val verified_clinician: Boolean = false,
)

@Serializable
data class DocRef(val id: String = "", val filename: String = "", val status: String = "", val language: String? = null)

@Serializable
data class Obs(val id: String = "", val code: String = "", val value: Double = 0.0, val unit: String = "", val observed_on: String = "")

@Serializable
data class Milestone(
    val id: String,
    val key: String = "",
    val title: String = "",
    val type: String = "visit",
    val window_weeks: List<Double> = listOf(0.0, 0.0),
    val required_tests: List<String> = emptyList(),
    val prep_notes: String = "",
    val status: String = "next",
    val overdue: Boolean = false,
    val completed_at: String? = null,
    val completion_level: String? = null,
    val scheduled_date: String? = null,
    val signoff: Signoff? = null,
    val documents: List<DocRef> = emptyList(),
    val observations: List<Obs> = emptyList(),
) {
    /** The NT scan window is shown as 11-13+6 weeks, matching the web app. */
    val windowLabel: String get() = "${window_weeks.getOrElse(0) { 0.0 }.toInt()}-" + (if (key == "nt_scan") "13+6" else "${window_weeks.getOrElse(1) { 0.0 }.toInt()}")
}

@Serializable
data class Gestation(val days: Int = 0, val weeks: Int = 0, val plus_days: Int = 0, val as_of: String = "")

@Serializable
data class PatientInfo(val name: String = "", val language: String = "en")

@Serializable
data class Threshold(val min: Double? = null, val max: Double? = null)

@Serializable
data class Flag(
    val code: String = "",
    val label: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val observed_on: String = "",
    val observation_id: String = "",
    val threshold: Threshold? = null,
    val message: String = "",
    val severity: String = "",
    val signed_off: Signoff? = null,
)

@Serializable
data class NextAppt(val key: String = "", val title: String = "", val scheduled_date: String = "")

@Serializable
data class Journey(
    val journey_id: String,
    val template_id: String = "",
    val template_version: String = "",
    val patient: PatientInfo = PatientInfo(),
    val gestational_age: Gestation = Gestation(),
    val lmp: String? = null,
    val edd: String = "",
    val summary: Map<String, Int> = emptyMap(),
    val next_appointment: NextAppt? = null,
    val milestones: List<Milestone> = emptyList(),
    val flags: List<Flag> = emptyList(),
    val danger_signs: List<String> = emptyList(),
)

@Serializable
data class JourneyListItem(val journey_id: String)

@Serializable
data class JourneyList(val journeys: List<JourneyListItem> = emptyList())

@Serializable
data class NextUpMilestone(val id: String = "", val key: String = "", val title: String = "", val type: String = "visit", val scheduled_date: String? = null)

@Serializable
data class NextUp(
    val milestone: NextUpMilestone? = null,
    val purpose: String? = null,
    val what_to_bring: String? = null,
    val fasting: String? = null,
    val questions: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class Reminder(
    val id: String,
    val kind: String = "",
    val title: String = "",
    val message: String = "",
    val scheduled_date: String? = null,
    val link: String = "",
    val read: Boolean = false,
    val dismissed: Boolean = false,
)

@Serializable
data class Inbox(val items: List<Reminder> = emptyList(), val unread: Int = 0)

@Serializable
data class Instruction(
    val id: String,
    val note: String = "",
    val doctor_name: String = "",
    val created_at: String = "",
    val acknowledged_at: String? = null,
)

@Serializable
data class InstructionList(val items: List<Instruction> = emptyList())

@Serializable
data class Explanation(val text: String = "", val provider: String = "", val language: String = "")

@Serializable
data class Citation(val source: String = "", val topic: String = "")

@Serializable
data class AskAnswer(val answer: String = "", val urgent: Boolean = false, val note: String? = null, val citation: Citation? = null)

@Serializable
data class VoiceAnswer(val transcript: String = "", val answer: String = "", val urgent: Boolean = false, val audio_base64: String? = null)

@Serializable
data class LatestValue(val code: String = "", val value: Double = 0.0, val unit: String = "", val observed_on: String = "")

@Serializable
data class BriefNext(val title: String = "", val scheduled_date: String = "")

@Serializable
data class RecentReport(val filename: String = "", val status: String = "", val uploaded_at: String = "")

@Serializable
data class Brief(
    val patient: PatientInfo = PatientInfo(),
    val gestational_age: Gestation = Gestation(),
    val edd: String = "",
    val open_flags: List<Flag> = emptyList(),
    val signed_flags: List<Flag> = emptyList(),
    val latest_values: List<LatestValue> = emptyList(),
    val next_appointment: BriefNext? = null,
    val recent_reports: List<RecentReport> = emptyList(),
)

@Serializable
data class TokenResponse(val token: String, val patient_id: String = "")

@Serializable
data class DuplicateOf(val uploaded_at: String = "")

@Serializable
data class UploadResponse(val document_id: String, val duplicate_of: DuplicateOf? = null)

@Serializable
data class ProposedValue(
    val code: String = "",
    val label: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val reference_range: String = "",
    val observed_on: String = "",
    val confidence: Double = 1.0,
)

@Serializable
data class ExtractResponse(
    val language: String = "",
    val provider: String = "",
    val proposed: List<ProposedValue> = emptyList(),
    val message: String? = null,
)

@Serializable
data class ConfirmResponse(val observations: List<Obs> = emptyList(), val flags: List<Flag> = emptyList())

@Serializable
data class TranslateResponse(val lang: String = "", val translations: Map<String, String> = emptyMap())

@Serializable
data class Me(val patient_id: String = "", val name: String = "", val phone: String = "", val language: String = "en")

// ---- Sprint 6: help assistant + doctor questions -------------------------

@Serializable
data class ChatMsg(
    val id: String = "",
    val role: String = "user",
    val text: String = "",
    val source: String = "",
    val urgent: Boolean = false,
    val suggest_doctor: Boolean = false,
    val created_at: String = "",
)

@Serializable
data class ChatHistory(val items: List<ChatMsg> = emptyList())

@Serializable
data class ChatResult(
    val message_id: String = "",
    val answer: String = "",
    val source: String = "",
    val urgent: Boolean = false,
    val suggest_doctor: Boolean = false,
    val language: String = "en",
    val note: String? = null,
)

@Serializable
data class LinkedDoctor(val id: String = "", val name: String = "")

@Serializable
data class DoctorQuestion(
    val id: String = "",
    val journey_id: String = "",
    val clinician_id: String = "",
    val recipient_name: String = "",
    val question: String = "",
    val created_at: String = "",
    val answer: String? = null,
    val answered_at: String? = null,
    val answerer_name: String? = null,
    val answered: Boolean = false,
)

@Serializable
data class DoctorQuestionList(val items: List<DoctorQuestion> = emptyList(), val linked_doctors: List<LinkedDoctor> = emptyList())
