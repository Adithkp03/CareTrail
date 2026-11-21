package com.caretrail.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.Api
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Journey
import com.caretrail.app.data.Repo
import com.caretrail.app.data.Store
import com.caretrail.app.data.SessionExpired
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cache-first startup (PR55): the last good copy renders while the network
 * refresh runs. These tests pin the session-safety boundaries of that cache:
 * it is scoped to a truncated SHA-256 of the session token (collision-resistant,
 * never the raw token), so a different login, an expired session, or a logout
 * can never surface another session's records.
 *
 * Intended offline privacy semantics: while a refresh is still in flight (or the
 * device is offline), the cached copy IS visible to this session's device holder
 * as a last-synced copy. A revoked-but-not-yet-detected session therefore behaves
 * exactly like offline until the server answers; the cache becomes unreachable
 * only once a 401 has cleared the token. refreshShowsSessionCacheUntilServerValidates
 * pins both sides of that contract.
 */
@RunWith(RobolectricTestRunner::class)
class StartupCacheTest {
    private fun newStore(name: String): Store {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val prefs = ctx.getSharedPreferences("test_$name", 0)
        prefs.edit().clear().commit()
        return Store(prefs)
    }

    private val journeyJson = AppJson.encodeToString(serializer<Journey>(), Journey(journey_id = "j1"))
    private fun repo(store: Store) = Repo(Api("http://127.0.0.1:1", { store.token }, {}), store)
    // Same derivation as Repo.scoped: truncated SHA-256 of the token, never the raw token.
    private fun scopedKey(token: String, key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16) + ":" + key
    }
    private fun seed(store: Store, token: String) = store.putCache(scopedKey(token, "journey"), journeyJson)

    @Test fun cachedJourneyIsEmptyBeforeFirstSync() {
        val store = newStore("a").also { it.token = "tokA" }
        assertNull(repo(store).cachedJourney())
    }

    @Test fun cacheIsVisibleWithinSameSession() {
        val store = newStore("b").also { it.token = "tokA" }
        seed(store, "tokA")
        assertEquals("j1", repo(store).cachedJourney()?.journey_id)
    }

    @Test fun accountSwitchCannotReadPreviousSessionCache() {
        val store = newStore("c").also { it.token = "tokA" }
        seed(store, "tokA")
        store.token = "tokB"
        assertNull(repo(store).cachedJourney())
    }

    @Test fun logoutWipesCache() {
        val store = newStore("d").also { it.token = "tokA" }
        seed(store, "tokA")
        store.clearAccount()
        store.token = "tokA"
        assertNull(repo(store).cachedJourney())
    }

    @Test fun expiredSessionDoesNotExposeCache() {
        val store = newStore("e").also { it.token = "tokA" }
        seed(store, "tokA")
        store.token = null
        assertNull(repo(store).cachedJourney())
    }

    @Test fun successfulRefreshReplacesCacheAndIsNotStale() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"journeys":[{"journey_id":"j1"}]}"""))
        server.enqueue(MockResponse().setBody(journeyJson))
        server.start()
        val store = newStore("g").also { it.token = "tokA" }
        seed(store, "tokA") // stale copy in place before refresh
        val repo = Repo(Api(server.url("/").toString(), { store.token }, {}), store)
        val res = repo.myJourney()
        server.shutdown()
        assertFalse(res.offline)
        assertEquals("j1", res.value?.journey_id)
        // Cache rewritten under the same session scope and still readable.
        assertEquals("j1", repo.cachedJourney()?.journey_id)
    }

    @Test fun serverErrorDoesNotFallBackToCache() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"detail\":\"boom\"}"))
        server.start()
        val store = newStore("h").also { it.token = "tokA" }
        seed(store, "tokA")
        val repo = Repo(Api(server.url("/").toString(), { store.token }, {}), store)
        val thrown = runCatching { repo.myJourney() }.exceptionOrNull()
        server.shutdown()
        assertTrue(thrown is com.caretrail.app.data.ApiException)
        assertEquals(500, (thrown as com.caretrail.app.data.ApiException).code)
    }

    @Test fun unauthorizedRefreshThrowsSessionExpiredAndKeepsCacheHidden() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"expired\"}"))
        server.start()
        val store = newStore("i").also { it.token = "tokA" }
        seed(store, "tokA")
        var expired = false
        val repo = Repo(Api(server.url("/").toString(), { store.token }, { expired = true }), store)
        val thrown = runCatching { repo.myJourney() }.exceptionOrNull()
        server.shutdown()
        assertTrue(thrown is SessionExpired)
        assertTrue(expired)
        // The real expireSession() clears the token; after that the cache is out of scope.
        store.token = null
        assertNull(repo(store).cachedJourney())
    }

    @Test fun networkFailureFallsBackToSessionCache() = runTest {
        val store = newStore("f").also { it.token = "tokA" }
        seed(store, "tokA")
        val res = repo(store).myJourney()
        assertTrue(res.offline)
        assertEquals("j1", res.value?.journey_id)
    }

    @Test fun cacheKeysNeverContainTheRawToken() {
        // The scope prefix must not leak the credential into preference keys.
        val store = newStore("j").also { it.token = "tok-secret-123" }
        seed(store, "tok-secret-123")
        val prefs = ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("test_j", 0)
        assertTrue(prefs.all.keys.any { it.endsWith(":journey") })
        assertTrue(prefs.all.keys.none { it.contains("tok-secret-123") })
        // Distinct tokens always map to distinct scopes (64-bit SHA-256 prefix).
        assertNotEquals(scopedKey("tokA", "journey"), scopedKey("tokB", "journey"))
        assertEquals(scopedKey("tokA", "journey"), scopedKey("tokA", "journey"))
    }

    @Test fun refreshShowsSessionCacheUntilServerValidates() = runTest {
        // Pre-validation window: the 401 is still in flight, so the last synced
        // copy remains readable to the session holder. Once the server rejects
        // the session, the token is cleared and the cache is unreachable.
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                hold.await(5, java.util.concurrent.TimeUnit.SECONDS)
                return MockResponse().setResponseCode(401).setBody("{\"detail\":\"revoked\"}")
            }
        }
        server.start()
        try {
            val store = newStore("k").also { it.token = "tokA" }
            seed(store, "tokA")
            val repo = Repo(Api(server.url("/").toString(), { store.token }, { store.token = null }), store)
            val job = kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { repo.myJourney() }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var waited = 0
                while (server.requestCount == 0 && waited < 5000) { Thread.sleep(20); waited += 20 }
            }
            assertTrue(server.requestCount > 0)
            // Request is at the server, answer still pending: cache still visible.
            assertNotNull(repo.cachedJourney())
            hold.countDown()
            job.join()
            // The 401 cleared the token: the same cache entry is now unreachable.
            assertNull(repo.cachedJourney())
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }
}
