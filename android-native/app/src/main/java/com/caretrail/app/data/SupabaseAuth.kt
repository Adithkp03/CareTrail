package com.caretrail.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class SupabaseAuthException(message: String) : Exception(message)

/**
 * Minimal Supabase Auth REST bridge for the native app: trades a Google ID token
 * (from Credential Manager) for a Supabase access token. The access token is then
 * exchanged for a CareTrail session through the backend, exactly like the web app.
 * Only the public anon key is used; no service keys exist on the client.
 */
class SupabaseAuth(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /** Google sign-in stays hidden until a build supplies the public anon key. */
    val configured: Boolean get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun signInWithGoogleIdToken(idToken: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("provider", "google")
            put("id_token", idToken)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(supabaseUrl.trimEnd('/') + "/auth/v1/token?grant_type=id_token")
            .header("apikey", anonKey)
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SupabaseAuthException("Identity provider sign-in failed (HTTP ${res.code})")
            val token = try {
                AppJson.parseToJsonElement(text).jsonObject["access_token"]?.jsonPrimitive?.content
            } catch (e: Exception) { null }
            token ?: throw SupabaseAuthException("Identity provider returned no access token")
        }
    }
}
