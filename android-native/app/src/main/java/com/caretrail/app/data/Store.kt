package com.caretrail.app.data

import android.content.Context
import android.content.SharedPreferences

/** Small key-value store: session token, language, offline caches, movement counts. */
class Store(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("caretrail", Context.MODE_PRIVATE))

    var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().apply { if (v == null) remove("token") else putString("token", v) }.apply() }

    var lang: String
        get() = prefs.getString("lang", "en") ?: "en"
        set(v) { prefs.edit().putString("lang", v).apply() }

    var langChosen: Boolean
        get() = prefs.getBoolean("lang_chosen", false)
        set(v) { prefs.edit().putBoolean("lang_chosen", v).apply() }

    fun getCache(key: String): String? = prefs.getString("cache:$key", null)
    fun putCache(key: String, json: String) { prefs.edit().putString("cache:$key", json).apply() }

    fun movements(journeyId: String, day: String): Int = prefs.getInt("mv:$journeyId:$day", 0)
    fun setMovements(journeyId: String, day: String, n: Int) { prefs.edit().putInt("mv:$journeyId:$day", n).apply() }

    fun translations(lang: String): Map<String, String> {
        val raw = prefs.getString("tr:$lang", null) ?: return emptyMap()
        return try { Json.decodeFromString(raw) } catch (_: Exception) { emptyMap() }
    }
    fun saveTranslations(lang: String, map: Map<String, String>) {
        prefs.edit().putString("tr:$lang", Json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), map)).apply()
    }

    fun clearAccount() {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("cache:") || it.startsWith("mv:") }.forEach { remove(it) }
            remove("token")
        }.apply()
    }

    private object Json {
        val impl = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        inline fun <reified T> decodeFromString(s: String): T = impl.decodeFromString(s)
        fun <T> encodeToString(ser: kotlinx.serialization.KSerializer<T>, v: T): String = impl.encodeToString(ser, v)
    }
}
