package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.ui.AssistantScreen
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Nav
import com.github.takahirom.roborazzi.captureRoboImage
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
 * Redesign pixels: the restyled help assistant empty state - friendly header,
 * suggestion chips and the rounded input bar from the user's chat sample.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenshotTest {
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

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun assistantEmptyStateShowsGreetingChipsAndInputBar() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/assistant/history")) {
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"items\": []}")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val vm = AppViewModel(app, server.url("/").toString().removeSuffix("/"))
            vm.store.token = "tok-pixel"
            vm.store.chatAcknowledged = true
            composeRule.setContent { CareTrailTheme { AssistantScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("Hello, how can I help you?").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Hello, how can I help you?").assertIsDisplayed()
            composeRule.onNodeWithText("When is my next appointment?").assertIsDisplayed()
            composeRule.onNodeWithText("Ask anything…").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            server.shutdown()
        }
    }
}
