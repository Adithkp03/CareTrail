package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Journey
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.HomeScreen
import com.caretrail.app.ui.JourneyGate
import com.caretrail.app.ui.Nav
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Pixels and semantics for the cache-first refresh state, using the REAL home
 * screen inside JourneyGate (not a placeholder): while the refresh is held
 * in flight by the server, the last synced copy renders with the updating
 * note, and the content still receives offline=false (network-backed actions
 * keep their normal behavior; cache-only indicators stay hidden).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupStaleScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Same derivation as Repo.scoped: truncated SHA-256 of the token.
    private fun scopedKey(token: String, key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16) + ":" + key
    }

    private fun fakeNav() = Nav(
        home = {}, timeline = {}, reminders = {}, movements = {}, care = {}, prep = {},
        doctor = {}, account = {}, accountData = {}, dataSafety = {}, intake = {}, registration = {}, rh = {},
        danger = {}, birth = {}, growth = {}, vaccines = {}, vault = {}, referral = {},
        facilities = {}, wellness = {}, recovery = {}, mindCheck = {}, bpLog = {},
        familyPlanning = {}, mindfulness = {}, chat = {}, community = {}, doctorQuestions = {},
        newJourney = {}, back = {}, milestone = {}, upload = {},
    )

    /** Server that holds every request until released: models the pre-validation window. */
    private fun hangingServer(hold: java.util.concurrent.CountDownLatch): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                hold.await(10, java.util.concurrent.TimeUnit.SECONDS)
                return MockResponse().setResponseCode(500)
            }
        }
        server.start()
        return server
    }

    private fun seededVm(server: MockWebServer): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app, server.url("/").toString().removeSuffix("/"))
        vm.store.token = "tok-pixel"
        val journeyJson = AppJson.encodeToString(serializer<Journey>(), Journey(journey_id = "j1"))
        vm.store.putCache(scopedKey("tok-pixel", "journey"), journeyJson)
        return vm
    }

    @Test
    fun staleRefreshStateShowsUpdatingNoteAboveRealHomeContent() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            composeRule.setContent { CareTrailTheme { HomeScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) {
                (vm.journey.value as? JourneyState.Ready)?.stale == true
            }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }

    @Test
    fun staleStatePassesOfflineFalseToContentAndShowsNote() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            var seenOffline: Boolean? = null
            composeRule.setContent {
                JourneyGate(vm, onEmpty = {}) { _, _, offline ->
                    seenOffline = offline
                }
            }
            composeRule.waitUntil(15_000) {
                (vm.journey.value as? JourneyState.Ready)?.stale == true
            }
            composeRule.waitForIdle()
            // Stale is NOT offline: content actions keep their online behavior.
            assertEquals(false, seenOffline)
            composeRule.onNodeWithText("Showing your last synced information - updating...")
                .assertExists()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }
}
