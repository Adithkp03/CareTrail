package com.caretrail.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.JourneyLoading
import com.caretrail.app.ui.L
import com.caretrail.app.ui.MilestoneRow
import com.caretrail.app.data.Milestone
import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        val labels = L("en", emptyMap(), vm)
        composeRule.setContent { CareTrailTheme { JourneyLoading(labels, true, "Loading...") {} } }
        composeRule.onNodeWithText("Still loading your care timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
    @Test @Config(qualifiers = "w360dp-h820dp-port")
    fun nextMilestoneClearlyLabelsAnAppointmentWindow() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        val labels = L("en", emptyMap(), vm)
        composeRule.setContent {
            CareTrailTheme { MilestoneRow(Milestone(id = "sample", title = "Second-trimester visit", status = "next", window_weeks = listOf(18.0, 24.0)), labels) {} }
        }
        composeRule.onNodeWithText("${labels.t("window")}: 18-24 ${labels.t("weeks")}").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
}
