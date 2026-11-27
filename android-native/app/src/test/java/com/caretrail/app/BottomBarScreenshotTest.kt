package com.caretrail.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Nav
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Bottom quick-access bar: Community replaced the Movements tab (user request
 * 2026-11-27). Movement tracking is NOT stranded: it stays reachable from the
 * Home quick-access grid (the "Movements" card in JourneyScreens). Profile is
 * the fifth tab (user request 2026-11-27): patient info, doctor details, app
 * details and logout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomBarScreenshotTest {
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

    @Test
    fun bottomBarShowsCommunityInsteadOfMovements() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent { CareTrailTheme { BottomBar(vm, "community", fakeNav()) } }
        composeRule.onNodeWithText("Community").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Reminders").assertIsDisplayed()
        composeRule.onNodeWithText("Movements").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage()
    }
}
