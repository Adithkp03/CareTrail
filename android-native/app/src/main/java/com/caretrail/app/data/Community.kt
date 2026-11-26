package com.caretrail.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

/** Opt-in community room client. Text only, pseudonymous nicknames, no patient
 * records. Server rules are mirrored in [CommunityRules] so obvious rejections
 * never reach the network; the backend stays authoritative. */
@Serializable
data class CommunityJoinResult(val joined: Boolean, val nickname: String = "", val suspended: Boolean = false)

@Serializable
data class CommunityMe(
    val joined: Boolean,
    val nickname: String = "",
    val suspended: Boolean = false,
    val suspended_reason: String = "",
)

@Serializable
data class CommunityMsg(
    val id: String,
    val author_id: String,
    val nickname: String,
    val body: String,
    val created_at: String,
    val own: Boolean = false,
    val author_blocked: Boolean = false,
)

@Serializable
data class CommunityMsgList(val messages: List<CommunityMsg> = emptyList(), val has_more: Boolean = false, val oldest_id: String? = null)

@Serializable
data class CommunityReportResult(val reported: Boolean, val id: String = "", val duplicate: Boolean = false)

@Serializable
data class CommunityBlocked(val patient_id: String, val nickname: String)

@Serializable
data class CommunityBlockList(val blocked: List<CommunityBlocked> = emptyList())

/** Community copy in one place so safety wording is reviewable and testable.
 * The notice is honest about limits: post-moderated, not 24/7, no emergencies. */
object CommunityText {
    const val notice = "This room is not watched around the clock and is not for emergencies. For urgent health worries, use the Danger signs page or contact your doctor or hospital now."
    const val guidelinesTitle = "Before you join"
    val guidelines = listOf(
        "One shared room for mothers using CareTrail. You appear only under the nickname you choose - never share your full name, phone number, address or hospital details.",
        "Share experiences and encouragement, not medical instructions. What worked for one pregnancy can be wrong for another - your own doctor decides your care.",
        "No links, no photos, no private messages. Text only, inside this room.",
        "Nothing from your health records is shown here, and what you write here is never shared with your doctor or added to your records.",
        "Messages you post can be reported by other members and reviewed by the CareTrail team, who can remove them and pause posting for accounts that break these rules.",
        "You can delete your own messages any time. Deleting your account removes your membership and blanks your nickname and message text; an anonymous placeholder can remain in the chat.",
        "Reports other members file about your messages, and the team's action log, are kept for up to 30 days while awaiting review and up to 30 days after a report is closed, then permanently deleted.",
    )
    const val consentLine = "Joining means you accept these guidelines."
    const val reportNote = "The CareTrail team reviews every report. Reports are not seen by the member you report."
    const val blockNote = "You will stop seeing their messages and they will not know. You can unblock them later from this page."
    const val suspendedNote = "Posting is paused for your account after a moderation review. You can still read the room."
    const val emptyRoom = "No messages yet. Say hello when you are ready."
    val reasonLabels = mapOf(
        "spam" to "Spam or advertising",
        "harassment" to "Rude, threatening or harassing",
        "medical-advice" to "Telling others what medical care to take or skip",
        "private-details" to "Sharing someone's private details",
        "other" to "Something else that breaks the guidelines",
    )
}

/** Client-side mirror of the server checks, so the same rule is testable
 * without a backend and the UI can explain a rejection before sending. */
object CommunityRules {
    private val reserved = setOf("admin", "moderator", "mod", "doctor", "dr", "caretrail", "support", "system", "team")
    private val nickRe = Regex("^[A-Za-z0-9 _-]+$")

    const val maxBody = 500
    val reportReasons = listOf("spam", "harassment", "medical-advice", "private-details", "other")

    /** Returns null when the nickname is acceptable, else the reason to show. */
    fun nicknameProblem(raw: String): String? {
        val nick = raw.trim().replace(Regex("\\s+"), " ")
        if (nick.length < 2 || nick.length > 24 || !nickRe.matches(nick))
            return "Nicknames use 2-24 letters, numbers, spaces, - or _"
        if (!nick.any { it.isLetter() }) return "Nickname needs at least one letter"
        if (nick.count { it.isDigit() } >= 7) return "Do not use a phone number as your nickname"
        if (nick.lowercase().replace(" ", "") in reserved) return "That nickname is reserved"
        return null
    }

    /** Returns null when the message body is acceptable, else the reason to show. */
    fun bodyProblem(raw: String): String? {
        val body = raw.trim().replace(Regex("\\s+"), " ")
        if (body.isEmpty() || body.length > maxBody) return "Messages are 1-500 characters"
        val lowered = body.lowercase()
        if ("http" in lowered || "www." in lowered) return "Links are not allowed in the community room"
        return null
    }
}

suspend fun Api.communityJoin(nickname: String): CommunityJoinResult =
    send("POST", "/community/join", buildJsonObject { put("nickname", nickname); put("consent", true) }.toString(), serializer())

suspend fun Api.communityMe(): CommunityMe = get("/community/me", serializer())

suspend fun Api.communityMessages(beforeId: String? = null): CommunityMsgList =
    get("/community/messages" + (beforeId?.let { "?before_id=$it" } ?: ""), serializer())

suspend fun Api.communityPost(body: String): CommunityMsg =
    send("POST", "/community/messages", buildJsonObject { put("body", body) }.toString(), serializer())

suspend fun Api.communityDelete(messageId: String): JsonElement =
    send("DELETE", "/community/messages/$messageId", null, serializer())

suspend fun Api.communityReport(messageId: String, reason: String, detail: String = ""): CommunityReportResult =
    send("POST", "/community/messages/$messageId/report",
        buildJsonObject { put("reason", reason); put("detail", detail) }.toString(), serializer())

suspend fun Api.communityBlocks(): CommunityBlockList = get("/community/blocks", serializer())

suspend fun Api.communityBlock(patientId: String): JsonElement =
    send("POST", "/community/blocks", buildJsonObject { put("patient_id", patientId) }.toString(), serializer())

suspend fun Api.communityUnblock(patientId: String): JsonElement =
    send("DELETE", "/community/blocks/$patientId", null, serializer())
