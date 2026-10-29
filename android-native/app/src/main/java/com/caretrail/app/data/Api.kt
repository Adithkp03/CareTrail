package com.caretrail.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)
class SessionExpired : Exception("session expired")

val AppJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

/** REST client for the existing CareTrail FastAPI backend (same endpoints as the web app). */
class Api(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {},
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonType = "application/json".toMediaType()

    private fun build(path: String, auth: Boolean): Request.Builder {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
        if (auth) tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private fun detailOf(body: String, fallback: String): String {
        return try {
            val el = AppJson.parseToJsonElement(body)
            val d = (el as? JsonObject)?.get("detail")
            when (d) {
                is JsonPrimitive -> d.content
                is JsonArray -> d.joinToString("; ") { (it as? JsonObject)?.get("msg")?.let { m -> (m as? JsonPrimitive)?.content } ?: it.toString() }
                null -> fallback
                else -> d.toString()
            }
        } catch (_: Exception) { fallback }
    }

    suspend fun raw(req: Request.Builder, auth: Boolean = true): ByteArray = withContext(Dispatchers.IO) {
        try {
            client.newCall(req.build()).execute().use { res ->
                val bytes = res.body?.bytes() ?: ByteArray(0)
                if (res.code == 401 && auth) { onUnauthorized(); throw SessionExpired() }
                if (!res.isSuccessful) throw ApiException(res.code, detailOf(String(bytes), res.message.ifBlank { "HTTP ${res.code}" }))
                bytes
            }
        } catch (e: IOException) {
            throw ApiException(0, "Network error: ${e.message ?: "offline"}")
        }
    }

    suspend fun <T> get(path: String, ser: KSerializer<T>, auth: Boolean = true): T =
        AppJson.decodeFromString(ser, String(raw(build(path, auth).get(), auth)))

    suspend fun <T> send(method: String, path: String, bodyJson: String?, ser: KSerializer<T>, auth: Boolean = true): T {
        val body: RequestBody? = if (method == "GET") null else (bodyJson ?: "{}").toRequestBody(jsonType)
        val bytes = raw(build(path, auth).method(method, body), auth)
        return AppJson.decodeFromString(ser, String(bytes))
    }

    suspend fun getText(path: String): String = String(raw(build(path, true).get()))

    suspend fun getBytes(path: String): ByteArray = raw(build(path, true).get())

    // ---- typed endpoints -------------------------------------------------

    suspend fun login(phone: String, password: String): TokenResponse =
        send("POST", "/auth/login", obj { put("phone", phone); put("password", password) }, serializer(), auth = false)

    suspend fun signup(name: String, phone: String, password: String, language: String, consent: Boolean): TokenResponse =
        send("POST", "/auth/signup", obj { put("name", name); put("phone", phone); put("password", password); put("language", language); put("consent", consent) }, serializer(), auth = false)

    suspend fun seedDemo() { send("POST", "/demo/seed", null, serializer<JsonElement>(), auth = false) }

    suspend fun me(): Me = get("/auth/me", serializer())
    suspend fun journeys(): JourneyList = get("/journeys", serializer())
    suspend fun journey(id: String): Journey = get("/journey/$id", serializer())
    suspend fun nextUp(id: String): NextUp = get("/journey/$id/next-up", serializer())
    suspend fun brief(id: String): Brief = get("/journey/$id/brief", serializer())
    suspend fun reminders(id: String): Inbox = get("/journey/$id/reminders", serializer())
    suspend fun instructions(id: String): InstructionList = get("/journey/$id/instructions", serializer())
    suspend fun milestone(id: String): Milestone = get("/milestones/$id", serializer())
    suspend fun explanation(id: String, lang: String): Explanation = get("/milestones/$id/explanation?lang=$lang", serializer())
    suspend fun explanationAudio(id: String, lang: String): ByteArray = getBytes("/milestones/$id/explanation/audio?lang=$lang")

    suspend fun createJourney(lmp: String?, edd: String?): JsonElement =
        send("POST", "/journeys", obj { if (lmp != null) put("lmp", lmp) else put("edd", edd) }, serializer())

    suspend fun completeMilestone(id: String): JsonElement = send("POST", "/milestones/$id/complete", "{}", serializer())
    suspend fun scheduleMilestone(id: String, date: String): JsonElement =
        send("POST", "/milestones/$id/schedule", obj { put("scheduled_date", date) }, serializer())

    suspend fun patchReminder(journeyId: String, reminderId: String, dismissed: Boolean): JsonElement =
        send("PATCH", "/journey/$journeyId/reminders", obj { put("reminder_id", reminderId); put("read", true); put("dismissed", dismissed) }, serializer())

    suspend fun acknowledge(instructionId: String): JsonElement = send("POST", "/instructions/$instructionId/acknowledge", null, serializer())

    suspend fun ask(question: String, lang: String): AskAnswer =
        send("POST", "/ask", obj { put("question", question); put("lang", lang) }, serializer())

    suspend fun askVoice(audio: ByteArray, filename: String, mime: String, lang: String): VoiceAnswer {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, audio.toRequestBody(mime.toMediaType()))
            .addFormDataPart("lang", lang).build()
        return AppJson.decodeFromString(serializer(), String(raw(build("/ask/voice", true).post(form))))
    }

    suspend fun translate(texts: List<String>, lang: String): TranslateResponse {
        val body = buildJsonObject {
            put("lang", lang)
            put("texts", JsonArray(texts.map { JsonPrimitive(it) }))
        }.toString()
        return send("POST", "/i18n/translate", body, serializer())
    }

    suspend fun upload(journeyId: String, filename: String, mime: String, bytes: ByteArray, milestoneId: String?): UploadResponse {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
        if (!milestoneId.isNullOrBlank()) b.addFormDataPart("milestone_id", milestoneId)
        return AppJson.decodeFromString(serializer(), String(raw(build("/journeys/$journeyId/documents/upload", true).post(b.build()))))
    }

    suspend fun extract(documentId: String): ExtractResponse = send("POST", "/documents/$documentId/extract", null, serializer())

    suspend fun confirm(documentId: String, milestoneId: String?, values: List<ProposedValue>): ConfirmResponse {
        val body = buildJsonObject {
            if (milestoneId.isNullOrBlank()) put("milestone_id", JsonPrimitive(null as String?)) else put("milestone_id", milestoneId)
            put("values", JsonArray(values.map {
                buildJsonObject { put("code", it.code); put("value", it.value); put("unit", it.unit); put("observed_on", it.observed_on) }
            }))
        }.toString()
        return send("POST", "/documents/$documentId/confirm", body, serializer())
    }

    suspend fun grantClinician(journeyId: String, email: String): JsonElement =
        send("POST", "/journeys/$journeyId/clinicians", obj { put("clinician_email", email) }, serializer())

    suspend fun exportData(): String = getText("/me/export")

    suspend fun deleteAccount(): JsonElement = send("DELETE", "/me", obj { put("confirm", "DELETE") }, serializer())

    // ---- Sprint 6: help assistant + doctor questions ----------------------

    suspend fun assistantChat(message: String, lang: String): ChatResult =
        send("POST", "/assistant/chat", obj { put("message", message); put("lang", lang) }, serializer())

    suspend fun assistantHistory(): ChatHistory = get("/assistant/history", serializer())

    suspend fun clearAssistantHistory(): JsonElement = send("DELETE", "/assistant/history", null, serializer())

    suspend fun askDoctor(journeyId: String, clinicianId: String, question: String): DoctorQuestion =
        send("POST", "/journeys/$journeyId/doctor-questions", obj { put("clinician_id", clinicianId); put("question", question) }, serializer())

    suspend fun doctorQuestions(journeyId: String): DoctorQuestionList = get("/journeys/$journeyId/doctor-questions", serializer())

    private fun obj(f: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String = buildJsonObject(f).toString()
}
