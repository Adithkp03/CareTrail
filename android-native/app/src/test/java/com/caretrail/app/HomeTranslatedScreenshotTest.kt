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
import androidx.compose.ui.test.performScrollTo
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
 * Translated-pixel proof for the redesigned Home at 320dp: real Malayalam and
 * Tamil script through the runtime translation cache, plus the empty-cache
 * (offline / first-launch / translation-failure) English fallback.
 *
 * The fixture strings below are DRAFT ordinary-UI translations for layout
 * verification only - native-speaker review is still pending, and this test
 * approves rendering, not wording. Clinical strings are not translated here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeTranslatedScreenshotTest {
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

    /**
     * First vm writes token + language + translation cache into the shared
     * prefs store; the returned vm's Translator picks them up at construction.
     */
    private fun translatedVm(server: MockWebServer, lang: String, translations: Map<String, String>): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val base = server.url("/").toString().removeSuffix("/")
        val token = "tok-tr-$lang"
        val setup = AppViewModel(app, base)
        setup.store.token = token
        setup.store.lang = lang
        setup.store.saveTranslations(lang, translations)
        val journey = Journey(
            journey_id = "j1",
            patient = PatientInfo(name = "Anjali", language = lang),
            gestational_age = Gestation(days = 168, weeks = 24, plus_days = 0),
        )
        setup.store.putCache(scopedKey(token, "journey"), AppJson.encodeToString(serializer<Journey>(), journey))
        return AppViewModel(app, base)
    }

    private fun renderHome(vm: AppViewModel) {
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
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-port")
    fun homeRendersMalayalamAtSmallWidth() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val helpAssistant = "ഹെൽപ്പ് അസിസ്റ്റന്റ്"
            val askDoctor = "നിങ്ങളുടെ ഡോക്ടരോട് ചോദിക്കൂ"
            val vm = translatedVm(server, "ml", mapOf(
                "Your pregnancy journey" to "നിങ്ങളുടെ ഗർഭകാല യാത്ര",
                "See this week's care" to "ഈ ആഴ്ചത്തെ പരിചരണം കാണുക",
                "Help assistant" to helpAssistant,
                "General info & app help" to "പൊതുവിവരങ്ങളും ആപ്പ് സഹായവും",
                "Ask your doctor" to askDoctor,
                "Send a question" to "ചോദ്യം അയയ്ക്കൂ",
                "Finish your health profile" to "നിങ്ങളുടെ ആരോഗ്യ പ്രൊഫൈൽ പൂർത്തിയാക്കൂ",
                "Instructions from your doctor" to "ഡോക്ടറിൽ നിന്നുള്ള നിർദ്ദേശങ്ങൾ",
                "Home" to "ഹോം",
                "Timeline" to "ടൈംലൈൻ",
                "Reminders" to "റിമൈൻഡറുകൾ",
                "Community" to "കമ്മ്യൂണിറ്റി",
                "Profile" to "പ്രൊഫൈൽ",
            ))
            renderHome(vm)
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(helpAssistant, substring = true).assertExists()
            composeRule.onRoot().captureRoboImage()
            composeRule.onNodeWithText(askDoctor, substring = true).performScrollTo()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(helpAssistant, substring = true).assertIsDisplayed()
            composeRule.onNodeWithText(askDoctor, substring = true).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-port")
    fun homeRendersTamilAtSmallWidth() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val helpAssistant = "உதவி அசிஸ்டன்ட்"
            val askDoctor = "உங்கள் மருத்துவரிடம் கேளுங்கள்"
            val vm = translatedVm(server, "ta", mapOf(
                "Your pregnancy journey" to "உங்கள் கர்ப்பகால பயணம்",
                "See this week's care" to "இந்த வாரத்தின் பராமரிப்பைப் பார்க்கவும்",
                "Help assistant" to helpAssistant,
                "General info & app help" to "பொது தகவல் & ஆப் உதவி",
                "Ask your doctor" to askDoctor,
                "Send a question" to "கேள்வி அனுப்புங்கள்",
                "Finish your health profile" to "உங்கள் சுகாதார விவரத்தை நிறைவு செய்யுங்கள்",
                "Instructions from your doctor" to "உங்கள் மருத்துவரின் வழிமுறைகள்",
                "Home" to "முகப்பு",
                "Timeline" to "காலவரிசை",
                "Reminders" to "நினைவூட்டல்கள்",
                "Community" to "சமூகம்",
                "Profile" to "சுயவிவரம்",
            ))
            renderHome(vm)
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(helpAssistant, substring = true).assertExists()
            composeRule.onRoot().captureRoboImage()
            composeRule.onNodeWithText(askDoctor, substring = true).performScrollTo()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(helpAssistant, substring = true).assertIsDisplayed()
            composeRule.onNodeWithText(askDoctor, substring = true).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }

    /** Empty translation cache (offline, first launch, or failed fetch): the
     * screen must render the original English, never a blank or a crash. */
    @Test
    @Config(qualifiers = "w320dp-h720dp-port")
    fun homeFallsBackToEnglishWithEmptyTranslationCache() {
        val hold = java.util.concurrent.CountDownLatch(1)
        val server = hangingServer(hold)
        try {
            val vm = translatedVm(server, "ml", emptyMap())
            renderHome(vm)
            composeRule.waitUntil(15_000) { vm.journey.value is JourneyState.Ready }
            composeRule.waitForIdle()
            // Curated labels render natively even with an empty cache; dynamic
            // server strings must still fall back to English.
            composeRule.onNodeWithText("Ask your doctor").assertExists()
            composeRule.onNodeWithText("Send a question").assertExists()
            composeRule.onRoot().captureRoboImage()
        } finally {
            hold.countDown()
            server.shutdown()
        }
    }
}
