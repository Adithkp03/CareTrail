package com.caretrail.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Gestation
import com.caretrail.app.data.Journey
import com.caretrail.app.data.PatientInfo
import com.caretrail.app.ui.CT
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.HomeScreen
import com.caretrail.app.ui.Nav
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bottom quick-access bar: Community replaced the Movements tab (user request
 * 2026-11-27). Movement tracking is NOT stranded: it stays reachable from the
 * Home quick-access grid (the "Movements" card in JourneyScreens). Profile is
 * the fifth tab (user request 2026-11-27): patient info, doctor details, app
 * details and logout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomBarScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fakeNav() = Nav(
        home = {}, timeline = {}, reminders = {}, movements = {}, care = {}, prep = {},
        doctor = {}, account = {}, accountData = {}, dataSafety = {}, intake = {}, registration = {}, rh = {},
        danger = {}, birth = {}, growth = {}, vaccines = {}, vault = {}, referral = {},
        facilities = {}, wellness = {}, recovery = {}, mindCheck = {}, bpLog = {},
        familyPlanning = {}, mindfulness = {}, chat = {}, community = {}, doctorQuestions = {},
        newJourney = {}, back = {}, milestone = {}, upload = {},
    )

    private fun scopedKey(token: String, key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16) + ":" + key
    }

    private fun seededVm(server: MockWebServer): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app, server.url("/").toString().removeSuffix("/"))
        vm.store.token = "tok-pixel"
        val journey = Journey(
            journey_id = "j1",
            patient = PatientInfo(name = "Anjali", language = "en"),
            gestational_age = Gestation(days = 168, weeks = 24, plus_days = 0),
        )
        vm.store.putCache(scopedKey("tok-pixel", "journey"), AppJson.encodeToString(serializer<Journey>(), journey))
        return vm
    }

    /** Holds every request until released: the screen renders from the cached journey. */
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

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun homeScreenShowsIntegratedBottomBarWithAllLabels() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            composeRule.setContent {
                CareTrailTheme {
                    Box(Modifier.fillMaxSize().background(CT.Bg)) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) { HomeScreen(vm, fakeNav()) }
                            BottomBar(vm, "home", fakeNav())
                        }
                    }
                }
            }
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Hi, Anjali").assertIsDisplayed()
            composeRule.onNodeWithText("Community").assertIsDisplayed()
            composeRule.onNodeWithText("Profile").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }

    @Test
    fun bottomBarShowsCommunityInsteadOfMovements() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent { CareTrailTheme { BottomBar(vm, "community", fakeNav()) } }
        composeRule.onNodeWithText("Community").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Reminders").assertIsDisplayed()
        composeRule.onNodeWithText("Movements").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage()
    }
}
