package com.caretrail.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.caretrail.app.data.DailyReminderPlan
import com.caretrail.app.data.DoseRecord
import com.caretrail.app.data.MedicationReminder
import com.caretrail.app.ui.TodayDoses
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoseCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayDosesCardWithMarkedDose() {
        // TodayDoses renders the device date. Keep this fixture valid on every CI day.
        val today = LocalDate.now()
        val med = MedicationReminder(
            id = "med-1",
            name = "Iron supplement",
            times = listOf("08:00", "20:00"),
            weekdays = listOf(today.dayOfWeek.value),
            start = today.toString(),
        )
        val plan = DailyReminderPlan(
            setupDone = true,
            medications = listOf(med),
            doses = listOf(
                DoseRecord("med-1", "Iron supplement", "${today}T08:00", "taken", "${today}T08:05"),
            ),
        )
        composeRule.setContent { TodayDoses(plan) {} }
        composeRule.onNodeWithText("Marked taken").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage()
    }
}
