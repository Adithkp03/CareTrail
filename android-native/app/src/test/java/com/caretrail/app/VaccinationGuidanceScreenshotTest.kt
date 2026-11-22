package com.caretrail.app

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.Api
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Journey
import com.caretrail.app.ui.*
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class VaccinationGuidanceScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun nav() = Nav(
        home = {}, timeline = {}, reminders = {}, movements = {}, care = {}, prep = {},
        doctor = {}, account = {}, dataSafety = {}, intake = {}, registration = {}, rh = {},
        danger = {}, birth = {}, growth = {}, vaccines = {}, vault = {}, referral = {},
        facilities = {}, wellness = {}, recovery = {}, mindCheck = {}, bpLog = {},
        familyPlanning = {}, mindfulness = {}, chat = {}, doctorQuestions = {},
        newJourney = {}, back = {}, milestone = {}, upload = {},
    )

    private fun capture(name: String) {
        val path = "build/outputs/roborazzi/VaccinationGuidance_$name.png"
        File(path).parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(path)
    }

    @Test fun secondTrimesterTimelineShowsDiscussionRatherThanADose() = screen("collapsed") { journey ->
        val guide = journey.vaccination_guidance!!
        compose.onNodeWithText("Read about Td, boosters and Tdap").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(guide.title).assertIsDisplayed()
        compose.onNodeWithText(guide.timing).assertIsDisplayed()
        compose.onNodeWithText(guide.summary).assertIsDisplayed()
        capture("collapsed_timeline")
    }

    @Test fun expandedGuidanceShowsBoosterConditionAndTdapRelation() = screen("expanded") { journey ->
        val guide = journey.vaccination_guidance!!
        compose.onNodeWithText("Read about Td, boosters and Tdap").performScrollTo().performClick()
        compose.onNodeWithText(guide.booster).performScrollTo().assertIsDisplayed()
        capture("course_and_booster")
        compose.onNodeWithText(guide.records).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(guide.tdap).assertIsDisplayed()
        compose.onNodeWithText("Mark as done").assertDoesNotExist()
        compose.onNodeWithText("Schedule").assertDoesNotExist()
        capture("tdap_and_unchanged_records")
    }

    @Test fun approvedExamNoteAndHospitalWarningAreVisible() = screen("exam", examScreen = true) { journey ->
        val exam = journey.milestones.single { it.key == "review_38w" }
        compose.onNodeWithText(exam.title).assertIsDisplayed()
        compose.onNodeWithText(exam.prep_notes).performScrollTo().assertIsDisplayed()
        capture("approved_exam_note")
    }

    private fun screen(scenario: String, examScreen: Boolean = false, check: (Journey) -> Unit) {
        val payload = javaClass.getResource("/clinical-vaccination.json")!!.readText()
        val journey = AppJson.decodeFromString<Journey>(payload)
        val exam = journey.milestones.single { it.key == "review_38w" }
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val unexpected = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!.substringBefore('?')
                val body = when (path) {
                    "/journeys" -> """{"journeys":[{"journey_id":"${journey.journey_id}"}]}"""
                    "/journey/${journey.journey_id}" -> payload
                    "/journey/${journey.journey_id}/next-up" -> """{}"""
                    "/milestones/${exam.id}" -> AppJson.encodeToString(exam)
                    "/milestones/${exam.id}/explanation" -> """{"text":"Synthetic clinician-guidance QA.","language":"en"}"""
                    else -> {
                        unexpected += path
                        return MockResponse().setResponseCode(500)
                    }
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear()
            .putString("token", "synthetic-clinical-session").putString("lang", "en")
            .putBoolean("lang_chosen", true).commit()
        val vm = AppViewModel(app)
        val endpoint = server.url("/").toString()
        val endpointField = Api::class.java.getDeclaredField("baseUrl").apply { isAccessible = true }
        endpointField.set(vm.api, endpoint)
        assertEquals(endpoint, endpointField.get(vm.api))
        assertTrue(vm.loggedIn.value)
        try {
            compose.setContent {
                CareTrailTheme {
                    if (examScreen) MilestoneScreen(vm, exam.id, nav()) else TimelineScreen(vm, nav())
                }
            }
            compose.waitUntil(15_000) {
                // Compose's frame clock does not drain Robolectric's paused main queue.
                // idle() runs due work only, without jumping forward to future messages.
                shadowOf(Looper.getMainLooper()).idle()
                if (examScreen) {
                    compose.onAllNodesWithText(exam.prep_notes).fetchSemanticsNodes().size == 1
                } else {
                    val state = vm.journey.value
                    state is JourneyState.Ready || state is JourneyState.Failed ||
                        state is JourneyState.Empty || !vm.loggedIn.value
                }
            }
            if (!examScreen) {
                assertTrue("Timeline must load through the actual Api/Repo", vm.journey.value is JourneyState.Ready)
                compose.onNodeWithText("Your timeline").assertIsDisplayed()
            }
            check(journey)
            assertTrue("The synthetic screen must issue real fixture GET requests", requests.isNotEmpty())
            assertTrue(requests.all { it.method == "GET" && it.getHeader("Authorization") == "Bearer synthetic-clinical-session" })
            assertEquals("Every requested endpoint must be explicitly modeled", emptyList<String>(), unexpected.toList())
        } catch (error: Throwable) {
            println("CLINICAL_NATIVE_DIAGNOSTIC scenario=$scenario; state=${vm.journey.value}; signedIn=${vm.loggedIn.value}; endpoint=$endpoint; requests=${requests.map { it.path }}; unexpected=$unexpected")
            runCatching { println(compose.onRoot().printToString()) }
                .onFailure { println("CLINICAL_DIAGNOSTIC_SEMANTICS_FAILED ${it.javaClass.simpleName}") }
            runCatching { capture("${scenario}_failed") }
                .onFailure { println("CLINICAL_DIAGNOSTIC_CAPTURE_FAILED ${it.javaClass.simpleName}") }
            throw error
        } finally {
            vm.api.client.dispatcher.cancelAll()
            server.shutdown()
            app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
