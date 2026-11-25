package com.caretrail.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.caretrail.app.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-content pixel coverage for the two merged journey sections, rendered
 * from current main source. Tall viewport so collapsed and expanded states
 * show every card and review label, not a cropped scroll window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2400dp-port")
class ContentSectionsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun showBoth() {
        compose.setContent {
            CareTrailTheme {
                Column {
                    OfficialSupportPanel()
                    ParentingListeningPanel()
                }
            }
        }
    }

    @Test fun bothSectionsStartCollapsed() {
        showBoth()
        compose.onNodeWithText("Government support: official guidance").assertExists()
        compose.onNodeWithText("Parenting listening: UNICEF India").assertExists()
        compose.onNodeWithText("PMMVY: maternity benefit").assertDoesNotExist()
        compose.onNodeWithText("Parenthood and co-parenting").assertDoesNotExist()
        compose.onRoot().captureRoboImage()
    }

    @Test fun supportExpandedShowsAllSchemesAndReviewDate() {
        showBoth()
        compose.onNodeWithText("Government support: official guidance").performClick()
        compose.onNodeWithText("India: English guidance in this first version, not an eligibility decision.", substring = true).assertExists()
        compose.onNodeWithText("PMMVY: maternity benefit").assertExists()
        compose.onNodeWithText("JSY: institutional-delivery support").assertExists()
        compose.onNodeWithText("JSSK: public-facility maternity and infant care").assertExists()
        compose.onNodeWithText("Sources checked 23 November 2026", substring = true).assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test fun listeningExpandedShowsLabelsAndSourceNote() {
        showBoth()
        compose.onNodeWithText("Parenting listening: UNICEF India").performClick()
        compose.onNodeWithText("Parenthood and co-parenting").assertExists()
        compose.onNodeWithText("not a maternity treatment guide", substring = true).assertExists()
        compose.onNodeWithText("12 minutes 26 seconds. English captions checked.", substring = true).assertExists()
        compose.onNodeWithText("no purchase or subscription", substring = true).assertExists()
        compose.onNodeWithText("not medical advice", substring = true).assertExists()
        compose.onRoot().captureRoboImage()
    }
}
