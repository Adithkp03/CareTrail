package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Taller-than-default test device so the full login layout (phone form + Google
// section) fits in the captured viewport instead of being cut off below the fold.
@Config(qualifiers = "w360dp-h820dp-port")
class LoginScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenWithGoogleButton() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication() as Application)
        composeRule.setContent { LoginScreen(vm) {} }
        composeRule.onRoot().captureRoboImage()
    }
}
