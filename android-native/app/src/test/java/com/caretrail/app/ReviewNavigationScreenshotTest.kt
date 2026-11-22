package com.caretrail.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.*
import com.caretrail.app.ui.CareTrailTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** Actual app graph + Api/Repo/loader, using synthetic API-produced journey fixtures.
 * No production account, endpoint/config override, real OAuth or acoustic assertion.
 * Endpoint injection is reflection confined to src/test, not a shipped app seam.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class ReviewNavigationScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstTrimesterHomeTimelineDetailAndBack() = navigateAt(56)
    @Test fun secondTrimesterHomeTimelineDetailAndBack() = navigateAt(140)
    @Test fun thirdTrimesterHomeTimelineDetailAndBack() = navigateAt(266)

    private fun tab(label: String) = compose.onNode(
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    )

    private fun capture(days: Int, screen: String) {
        val path = "build/outputs/roborazzi/ReviewNavigation_${days}_${screen}.png"
        File(path).parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(path)
    }

    private fun navigateAt(days: Int) {
        val fixtures = AppJson.decodeFromString<List<Journey>>(
            javaClass.getResource("/review-navigation.json")!!.readText(),
        )
        val source = fixtures.single { it.gestational_age.days == days }
        val lmp = LocalDate.now().minusDays(days.toLong())
        val journey = source.copy(lmp = lmp.toString(), edd = lmp.plusDays(280).toString(),
            gestational_age = source.gestational_age.copy(as_of = LocalDate.now().toString()))
        val payload = AppJson.encodeToString(journey)
        val stage = Logic.trimester(journey.gestational_age.weeks)
        val target = Logic.highlighted(journey.milestones.filter { Logic.milestoneStage(it) == stage }, stage, stage).first()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val unexpected = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!.substringBefore('?')
                val body = when {
                    path == "/journeys" -> """{"journeys":[{"journey_id":"${journey.journey_id}"}]}"""
                    path == "/journey/${journey.journey_id}" -> payload
                    path == "/journey/${journey.journey_id}/next-up" -> """{"message":"Review your timeline with your doctor."}"""
                    path == "/journey/${journey.journey_id}/instructions" -> """{"items":[]}"""
                    path == "/milestones/${target.id}" -> AppJson.encodeToString(target)
                    path == "/milestones/${target.id}/explanation" -> """{"text":"Synthetic explanation for navigation QA.","language":"en"}"""
                    else -> { unexpected += path; return MockResponse().setResponseCode(500).setBody("Unexpected test endpoint") }
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear()
            .putString("token", "synthetic-native-session").putString("lang", "en")
            .putBoolean("lang_chosen", true).commit()
        // Seed the existing opt-in setup state, without scheduling alarms or granting permissions.
        app.getSharedPreferences("daily_reminders", Context.MODE_PRIVATE).edit().clear()
            .putString("active", journey.journey_id)
            .putString(journey.journey_id, """{"setupDone":true}""").commit()
        val vm = AppViewModel(app)
        Api::class.java.getDeclaredField("baseUrl").apply { isAccessible = true }
            .set(vm.api, server.url("/").toString())
        try {
            compose.setContent { CareTrailTheme { CareTrailApp(vm) } }
            compose.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            compose.onNodeWithText("Synthetic native QA").assertIsDisplayed()
            assertEquals(days, (vm.journey.value as JourneyState.Ready).journey.gestational_age.days)
            capture(days, "home")

            tab("Timeline").performClick()
            compose.onNodeWithText("Your timeline").assertIsDisplayed()
            compose.onNodeWithText(target.title).performScrollTo().assertIsDisplayed()
            capture(days, "timeline")
            compose.onNodeWithText(target.title).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(target.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(target.title).assertIsDisplayed()
            compose.onNodeWithText("Listen").performScrollTo().assertIsDisplayed()
            capture(days, "milestone")

            compose.onNodeWithText("← CareTrail").performScrollTo().performClick()
            compose.onNodeWithText("Your timeline").assertIsDisplayed()
            tab("Home").performClick()
            compose.onNodeWithText("Synthetic native QA").assertIsDisplayed()
            assertTrue(requests.any { it.path == "/milestones/${target.id}" })
            assertTrue(requests.all { it.method == "GET" && it.getHeader("Authorization") == "Bearer synthetic-native-session" })
            assertEquals("Every requested endpoint must be explicitly modeled", emptyList<String>(), unexpected.toList())
        } finally {
            vm.api.client.dispatcher.cancelAll()
            server.shutdown()
            app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("daily_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
