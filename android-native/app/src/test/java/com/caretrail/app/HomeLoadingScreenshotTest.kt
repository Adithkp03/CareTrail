package com.caretrail.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.JourneyLoading
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLoadingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()
    @Test @Config(qualifiers = "w360dp-h820dp-port")
    fun firstLoadExplainsSlowRequestAndOffersRetry() {
        composeRule.setContent { CareTrailTheme { JourneyLoading(true, "Loading...") {} } }
        composeRule.onNodeWithText("Still loading your care timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
}
