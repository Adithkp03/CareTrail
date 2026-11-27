package com.caretrail.app

import android.app.Application
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.caretrail.app.ui.AssistantScreen
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Nav
import java.io.File
import java.io.FileOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * On-device (emulator) proof that the chat composer stays usable with the soft
 * keyboard OPEN - the static Roborazzi renders cannot show the IME. Mirrors the
 * app manifest's windowSoftInputMode="adjustResize", taps the composer, waits
 * for the window to shrink (keyboard shown), then asserts the composer stays
 * fully inside the visible root and captures a screenshot for CI artifact
 * review.
 */
class ChatKeyboardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun fakeNav() = Nav(
        home = {}, timeline = {}, reminders = {}, movements = {}, care = {}, prep = {},
        doctor = {}, account = {}, accountData = {}, dataSafety = {}, intake = {}, registration = {}, rh = {},
        danger = {}, birth = {}, growth = {}, vaccines = {}, vault = {}, referral = {},
        facilities = {}, wellness = {}, recovery = {}, mindCheck = {}, bpLog = {},
        familyPlanning = {}, mindfulness = {}, chat = {}, community = {}, doctorQuestions = {},
        newJourney = {}, back = {}, milestone = {}, upload = {},
    )

    @Test
    fun composerStaysVisibleAboveKeyboard() {
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
            vm.store.token = "tok-keyboard"
            vm.store.chatAcknowledged = true

            composeRule.setContent { CareTrailTheme { AssistantScreen(vm, fakeNav()) } }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("Ask anything…").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // Match the app manifest's adjustResize for this bare test activity.
            composeRule.activity.runOnUiThread {
                composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            val rootBefore = composeRule.onRoot().getUnclippedBoundsInRoot()
            composeRule.onNodeWithText("Ask anything…").performClick()

            // Keyboard shown => window (and compose root) shrinks under adjustResize.
            composeRule.waitUntil(10_000) {
                composeRule.onRoot().getUnclippedBoundsInRoot().height < rootBefore.height - 80.dp
            }
            composeRule.waitForIdle()

            val rootAfter = composeRule.onRoot().getUnclippedBoundsInRoot()
            val composer = composeRule.onNodeWithText("Ask anything…").getUnclippedBoundsInRoot()
            assertTrue("composer pushed off top: $composer vs $rootAfter", composer.top >= rootAfter.top - 1.dp)
            assertTrue("composer hidden under keyboard: $composer vs $rootAfter", composer.bottom <= rootAfter.bottom + 1.dp)
            composeRule.onNodeWithText("Ask anything…").assertIsDisplayed()

            val bmp = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val out = File(ctx.getExternalFilesDir(null), "chat-keyboard-open.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            server.shutdown()
        }
    }
}
