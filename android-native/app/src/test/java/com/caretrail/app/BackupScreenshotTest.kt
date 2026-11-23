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
class BackupScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun show(screen: String, postpartum: Boolean = false) {
        val app = RuntimeEnvironment.getApplication() as Application
        app.getSharedPreferences("caretrail", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = AppViewModel(app)
        vm.store.token = "synthetic-backup-token"
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
        val nav = Nav(home = noop, timeline = noop, reminders = noop, movements = noop, care = noop, prep = noop, doctor = noop, account = noop, dataSafety = noop, intake = noop, registration = noop, rh = noop, danger = noop, birth = noop, growth = noop, vaccines = noop, vault = noop, referral = noop, facilities = noop, wellness = noop, recovery = noop, mindCheck = noop, bpLog = noop, familyPlanning = noop, mindfulness = noop, chat = noop, doctorQuestions = noop, newJourney = noop, back = noop, milestone = {}, upload = {})
        compose.setContent { CareTrailTheme { when (screen) {
            "birth" -> BirthEntryScreen(vm, nav)
            "backup" -> Page(title = "Optional cloud backup", back = {}) { StructuredBackupPanel(vm) }
            "safety" -> DataSafetyScreen(vm, nav)
            else -> HomeScreen(vm, nav)
        } } }
        if (screen == "birth") {
            compose.onNodeWithText("Birth weight (kg)").assertExists()
            assertFalse(vm.store.birthRecord(journey.journey_id).confirmed)
        }
    }
    @Test fun optionalBackupStartsClosed() {
        show("backup")
        compose.onNodeWithText("Optional cloud backup and restore").assertExists()
        compose.onNodeWithText("Preview selected local and server copies").assertDoesNotExist()
        compose.onRoot().captureRoboImage()
    }
    @Test fun backupOpensWithNoModulesSelected() {
        show("backup")
        compose.onNodeWithText("Optional cloud backup and restore").performClick()
        compose.onAllNodes(isToggleable()).assertCountEquals(13)
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().forEachIndexed { index, _ ->
            compose.onAllNodes(isToggleable())[index].assertIsOff()
        }
        compose.onRoot().captureRoboImage()
    }
    @Test fun sensitiveChoicesStayOffAndPreviewIsExplicit() {
        show("backup")
        compose.onNodeWithText("Optional cloud backup and restore").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Preview selected local and server copies"))
        compose.onNodeWithText("Mood entries (optional)").assertIsDisplayed()
        compose.onNodeWithText("Preview selected local and server copies").assertIsNotEnabled()
        compose.onRoot().captureRoboImage()
    }
}
