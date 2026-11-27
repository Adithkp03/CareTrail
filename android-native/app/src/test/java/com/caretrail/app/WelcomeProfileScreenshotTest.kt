package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Gestation
import com.caretrail.app.data.Journey
import com.caretrail.app.data.PatientInfo
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Nav
import com.caretrail.app.ui.ProfileScreen
import com.caretrail.app.ui.WelcomeScreen
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Redesign pixels: the new photo welcome screen and the new Profile tab.
 * Profile renders the real screen against a cached journey with the server
 * held, the same seeded pattern as StartupStaleScreenshotTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeProfileScreenshotTest {
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

    /** Server that answers doctor-questions with a linked doctor and holds every other request until released. */
    private fun hangingServer(hold: java.util.concurrent.CountDownLatch): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().contains("/doctor-questions")) {
                    return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("{\"items\": [], \"linked_doctors\": [{\"id\": \"c1\", \"name\": \"Dr. Jyotsna Sharma\"}]}")
                }
                hold.await(10, java.util.concurrent.TimeUnit.SECONDS)
                return MockResponse().setResponseCode(500)
            }
        }
        server.start()
        return server
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun welcomeScreenShowsPhotosLoginSignupAndLanguage() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent { CareTrailTheme { WelcomeScreen(vm, onLogin = {}, onSignup = {}) } }
        composeRule.onNodeWithText("CareTrail").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun profileScreenShowsPatientDoctorAppAndLogout() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            composeRule.setContent { CareTrailTheme { ProfileScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasText("Dr. Jyotsna Sharma")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Anjali").assertIsDisplayed()
            composeRule.onNodeWithText("Dr. Jyotsna Sharma").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }
}
