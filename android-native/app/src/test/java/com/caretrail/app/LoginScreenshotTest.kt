package com.caretrail.app

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.caretrail.app.ui.LoginScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Renders the real LoginScreen. The Google sign-in section is visible only when
// SupabaseAuth.configured is true, so the CI screenshot job passes a dummy anon
// key via -PcaretrailSupabaseAnonKey (build-time flag only, no network calls).
// Widths matter: 360dp is a typical phone, 320dp is the smallest real device
// class - the default Robolectric viewport is far narrower in effective dp and
// produced a misleading mid-word "Welcome" break, so both real widths are
// verified explicitly instead of trusting one render.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun showLogin() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent { com.caretrail.app.ui.CareTrailTheme { LoginScreen(vm) {} } }
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun loginScreenWithGoogleButton() {
        showLogin()
        composeRule.onRoot().captureRoboImage()
    }

    // Smallest real phone width: header must wrap at word boundaries, and the
    // top of the login form must be visible without scrolling.
    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun loginScreenSmallPhoneTop() {
        showLogin()
        composeRule.onRoot().captureRoboImage()
    }

    // Same small phone: the Google controls sit below the fold, so this proves
    // they are actually reachable by scrolling, not clipped away.
    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun loginScreenSmallPhoneScrollsToGoogle() {
        showLogin()
        composeRule.onNodeWithText("Continue with Google", useUnmergedTree = true).assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Continue with Google"))
        composeRule.onNodeWithText("Continue with Google", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun signupOffersGoogleAbovePhoneForm() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent { com.caretrail.app.ui.CareTrailTheme { com.caretrail.app.ui.SignupScreen(vm) {} } }
        composeRule.onNodeWithText("Continue with Google", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun signupGoogleVisibleOnSmallPhone() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent { com.caretrail.app.ui.CareTrailTheme { com.caretrail.app.ui.SignupScreen(vm) {} } }
        composeRule.onNodeWithText("Continue with Google", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    private fun showAccountPanel(error: String = "") {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent {
            com.caretrail.app.ui.CareTrailTheme {
                com.caretrail.app.ui.GoogleAccountPanel(com.caretrail.app.ui.rememberL(vm), false, error,
                    onLanguage = {}, onCreate = {}, onLink = { _, _ -> }, onBack = {})
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun googleCreationRequiresConsentAndSeparatesLinking() {
        showAccountPanel()
        composeRule.onNodeWithText("Create account with Google").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Phone number").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage()
        composeRule.onNodeWithText("I understand CareTrail", substring = true).performClick()
        composeRule.onNodeWithText("Create account with Google").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun googleCreationSmallPhoneScrollsToLink() {
        showAccountPanel()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Link my existing account"))
        composeRule.onNodeWithText("Link my existing account").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun googleLinkFormRequiresPasswordProof() {
        showAccountPanel()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Link my existing account"))
        composeRule.onNodeWithText("Link my existing account").performClick()
        composeRule.onNodeWithText("Link my existing account").assertIsNotEnabled()
        composeRule.onNodeWithText("Phone number").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w360dp-h820dp-port")
    fun successSnackbarUsesInAppHost() {
        composeRule.setContent {
            com.caretrail.app.ui.CareTrailTheme {
                val host = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
                androidx.compose.runtime.LaunchedEffect(Unit) { host.showSnackbar("Google sign-in successful", duration = androidx.compose.material3.SnackbarDuration.Indefinite) }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    GoogleAuthNotice(host, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        composeRule.onNodeWithText("Google sign-in successful").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
}
