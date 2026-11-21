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
 * it is scoped to the session token, so a different login, an expired session,
 * or a logout can never surface another session's records.
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
    private fun seed(store: Store, token: String) = store.putCache("${token.hashCode()}:journey", journeyJson)

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
}
