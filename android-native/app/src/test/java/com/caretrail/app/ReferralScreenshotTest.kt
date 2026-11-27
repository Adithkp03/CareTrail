package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Gestation
import com.caretrail.app.data.Journey
import com.caretrail.app.data.PatientInfo
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Nav
import com.caretrail.app.ui.ReferralScreen
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
 * Referral transfer screen (patient-carried summary export/import) rendered for
 * demo/video review at 360dp with the synthetic journey only. The received-file
 * preview state needs the system document picker, which Robolectric cannot
 * drive - that state is covered by the live/emulator capture, not here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferralScreenshotTest {
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
    fun referralInitialExplainsScopeAndOffersPrepare() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            composeRule.setContent { CareTrailTheme { ReferralScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Referral transfer").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun referralPreparedShowsReviewConsentAndExportGate() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = seededVm(server)
            composeRule.setContent { CareTrailTheme { ReferralScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Prepare my referral").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Review before export").assertIsDisplayed()
            composeRule.onNodeWithText("I have reviewed and want to export").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Export acknowledged").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }
}
