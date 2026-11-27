package com.caretrail.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.caretrail.app.data.*
import com.caretrail.app.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class RecordSafetyScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun show(screen: String, postpartum: Boolean = false) {
        val app = RuntimeEnvironment.getApplication() as Application
        app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = AppViewModel(app)
        val source = AppJson.decodeFromString<List<Journey>>(javaClass.getResource("/record-safety.json")!!.readText()).first()
        val journey = source.copy(journey_id = "synthetic-record-safety")
        if (postpartum) vm.store.saveBirthRecord(journey.journey_id, BirthRecord(confirmed = true, date = LocalDate.now().minusDays(60).toString(), gender = "girl"))
        else vm.store.saveBirthDraft(journey.journey_id, BirthDraft(date = LocalDate.now().toString(), weight = "2.", gender = "girl"))
        // No HTTP: direct screen contract with a Ready flow and deliberately offline Repo refresh.
        @Suppress("UNCHECKED_CAST")
        val flow = runCatching {
            AppViewModel::class.java.getDeclaredField("_journey").apply { isAccessible = true }
                .get(vm) as kotlinx.coroutines.flow.MutableStateFlow<JourneyState>
        }.getOrElse {
            val loader = AppViewModel::class.java.getDeclaredField("journeyLoader")
                .apply { isAccessible = true }.get(vm)
            loader.javaClass.getDeclaredField("mutable").apply { isAccessible = true }
                .get(loader) as kotlinx.coroutines.flow.MutableStateFlow<JourneyState>
        }
        val ready = JourneyState.Ready(journey, null, true)
        flow.value = ready
        vm.store.putCache("journey", AppJson.encodeToString(Journey.serializer(), journey))
        val endpoint = Api::class.java.getDeclaredField("baseUrl").apply { isAccessible = true }
        endpoint.set(vm.api, "http://127.0.0.1:1/")
        val noop: () -> Unit = {}
        val nav = Nav(home = noop, timeline = noop, reminders = noop, movements = noop, care = noop, prep = noop, doctor = noop, account = noop, accountData = noop, dataSafety = noop, intake = noop, registration = noop, rh = noop, danger = noop, birth = noop, growth = noop, vaccines = noop, vault = noop, referral = noop, facilities = noop, wellness = noop, recovery = noop, mindCheck = noop, bpLog = noop, familyPlanning = noop, mindfulness = noop, chat = noop, community = noop, doctorQuestions = noop, newJourney = noop, back = noop, milestone = {}, upload = {})
        compose.setContent { CareTrailTheme { when (screen) {
            "birth" -> BirthEntryScreen(vm, nav)
            "safety" -> DataSafetyScreen(vm, nav)
            else -> HomeScreen(vm, nav)
        } } }
        if (screen == "birth") {
            compose.onNodeWithText("Birth weight (kg)").assertExists()
            assertFalse(vm.store.birthRecord(journey.journey_id).confirmed)
        }
    }
    @Test fun restoredDraft() { show("birth"); compose.onRoot().captureRoboImage() }
    @Test fun localStorageWarning() { show("safety"); compose.onRoot().captureRoboImage() }
    @Test fun postpartumMotherBabyLinks() {
        show("home", true)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mother: recovery and wellbeing"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Baby: vaccination record"))
        compose.onNodeWithText("Baby: vaccination record").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }
    @Test fun logoutCancelPreservesDraft() {
        show("home")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Log out"))
        compose.onNodeWithText("Log out").performClick()
        compose.onNodeWithText("Before you log out").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Stay signed in").performClick()
        compose.onNodeWithText("Before you log out").assertDoesNotExist()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("caretrail", Context.MODE_PRIVATE)
        assertNotNull(Store(prefs).birthDraft("synthetic-record-safety"))
    }
}
