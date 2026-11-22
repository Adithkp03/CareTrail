package com.caretrail.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.Api
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.Journey
import com.caretrail.app.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.decodeFromString
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
import java.io.File

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

    @Test fun secondTrimesterTimelineShowsDiscussionRatherThanADose() = timeline { journey ->
        val guide = journey.vaccination_guidance!!
        compose.onNodeWithText("Read about Td, boosters and Tdap").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(guide.title).assertIsDisplayed()
        compose.onNodeWithText(guide.timing).assertIsDisplayed()
        compose.onNodeWithText(guide.summary).assertIsDisplayed()
        capture("collapsed_timeline")
    }

    @Test fun expandedGuidanceShowsBoosterConditionAndTdapRelation() = timeline { journey ->
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

    private fun timeline(check: (Journey) -> Unit) {
        val payload = javaClass.getResource("/clinical-vaccination.json")!!.readText()
        val journey = AppJson.decodeFromString<Journey>(payload)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.path!!.substringBefore('?')) {
                    "/journeys" -> """{"journeys":[{"journey_id":"${journey.journey_id}"}]}"""
                    "/journey/${journey.journey_id}" -> payload
                    "/journey/${journey.journey_id}/next-up" -> """{}"""
                    else -> return MockResponse().setResponseCode(500)
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
        Api::class.java.getDeclaredField("baseUrl").apply { isAccessible = true }
            .set(vm.api, server.url("/").toString())
        try {
            compose.setContent { CareTrailTheme { TimelineScreen(vm, nav()) } }
            compose.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            compose.onNodeWithText("Your timeline").assertIsDisplayed()
            check(journey)
        } finally {
            vm.api.client.dispatcher.cancelAll()
            server.shutdown()
            app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
