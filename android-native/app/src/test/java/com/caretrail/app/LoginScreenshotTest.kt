package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.setContent { LoginScreen(vm) {} }
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
}
