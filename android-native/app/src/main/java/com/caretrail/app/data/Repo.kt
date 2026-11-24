package com.caretrail.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/** Loads data from the API and keeps the last good copy on the phone for offline viewing. */
class Repo(val api: Api, val store: Store) {
    data class Loaded<T>(val value: T, val offline: Boolean)

    // Cache is scoped via a truncated SHA-256 of the session token (collision-resistant, raw token never stored in keys): a different login or renewed token can never read another session's records.
    private fun scoped(key: String): String = java.security.MessageDigest.getInstance("SHA-256").digest((store.token ?: "").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16) + ":" + key

    private inline fun <reified T> cached(key: String): T? =
        store.getCache(scoped(key))?.let { try { AppJson.decodeFromString(serializer<T>(), it) } catch (_: Exception) { null } }

    private inline fun <reified T> remember(key: String, v: T) {
        store.putCache(scoped(key), AppJson.encodeToString(serializer<T>(), v))
    }

    fun cachedJourney(): Journey? = cached("journey")

    fun cachedNextUp(): NextUp? = cached("nextup")

    suspend fun myJourney(): Loaded<Journey?> {
        return try {
            val list = api.journeys()
            if (list.journeys.isEmpty()) return Loaded(null, false)
            val j = api.journey(list.journeys[0].journey_id)
            remember("journey", j)
            Loaded(j, false)
        } catch (e: SessionExpired) {
            throw e
        } catch (e: ApiException) {
            // A real HTTP answer (4xx/5xx) is surfaced; only network failure falls back to cache.
            val c = cached<Journey>("journey")
            if (c != null && e.code == 0) Loaded(c, true) else throw e
        }
    }

    suspend fun nextUp(journeyId: String): NextUp? = try {
        api.nextUp(journeyId).also { remember("nextup", it) }
    } catch (e: SessionExpired) { throw e } catch (e: CancellationException) { throw e } catch (_: Exception) { cached<NextUp>("nextup") }

    suspend fun milestone(id: String): Loaded<Milestone> = try {
        Loaded(api.milestone(id).also { remember("ms:$id", it) }, false)
    } catch (e: SessionExpired) { throw e } catch (e: Exception) {
        cached<Milestone>("ms:$id")?.let { Loaded(it, true) } ?: throw e
    }

    suspend fun explanation(id: String, lang: String): Explanation? = try {
        api.explanation(id, lang).also { remember("ex:$id:$lang", it) }
    } catch (e: SessionExpired) { throw e } catch (_: Exception) { cached<Explanation>("ex:$id:$lang") }
}
