package com.caretrail.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption for records at rest, keyed by the Android Keystore (PRD data
 * security: local protection only, not end-to-end encryption). Values written
 * through putCache are stored as "enc1:" + base64(iv + ciphertext); older plaintext
 * values still read fine and are upgraded to ciphertext on their next write, or in
 * bulk by migrateCacheToEncrypted(). Encryption errors fail the write; they never fall back to plaintext.
 * Existing values are preserved if migration cannot complete.
 */
object StoreCrypto {
    private const val ALIAS = "caretrail_store"
    private const val PREFIX = "enc1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { throw IllegalStateException("Cannot unlock local records. Do not clear app data; retry on this device.", e) }
    }

    fun isEncrypted(stored: String) = stored.startsWith(PREFIX)
}

/** Small key-value store: session token, language, offline caches, movement counts. */
class Store(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("caretrail", Context.MODE_PRIVATE))

    var token: String?
        get() = prefs.getString("token", null)?.let { StoreCrypto.decrypt(it) }
        set(v) { prefs.edit().apply { if (v == null) remove("token") else putString("token", (if (encryptedRecordsEnabled()) StoreCrypto.encrypt(v) else v)) }.apply() }

    var lang: String
        get() = prefs.getString("lang", "en") ?: "en"
        set(v) { prefs.edit().putString("lang", v).apply() }

    var langChosen: Boolean
        get() = prefs.getBoolean("lang_chosen", false)
        set(v) { prefs.edit().putBoolean("lang_chosen", v).apply() }

    /** One-time acknowledgement of what the help assistant is (and is not). */
    var chatAcknowledged: Boolean
        get() = prefs.getBoolean("chat_ack", false)
        set(v) { prefs.edit().putBoolean("chat_ack", v).apply() }

    fun encryptedRecordsEnabled() = prefs.getBoolean("encrypted_records", false)

    fun getCache(key: String): String? = prefs.getString("cache:$key", null)?.let { StoreCrypto.decrypt(it) }
    fun putCache(key: String, json: String) { prefs.edit().putString("cache:$key", (if (encryptedRecordsEnabled()) StoreCrypto.encrypt(json) else json)).apply() }

    /** One-time upgrade: rewrite any plaintext cache rows as Keystore-encrypted rows. */
    fun migrateCacheToEncrypted() {
        val edits = prefs.edit()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("cache:") && v is String && !StoreCrypto.isEncrypted(v)) {
                edits.putString(k, StoreCrypto.encrypt(StoreCrypto.decrypt(v)))
            }
        }
        prefs.getString("token", null)?.let { edits.putString("token", StoreCrypto.encrypt(StoreCrypto.decrypt(it))) }
        edits.putBoolean("encrypted_records", true)
        check(edits.commit()) { "Could not save encrypted records" }
    }

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
