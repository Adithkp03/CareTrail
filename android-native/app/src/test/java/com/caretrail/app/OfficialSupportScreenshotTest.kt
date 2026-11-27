package com.caretrail.app

import androidx.compose.ui.test.*
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.caretrail.app.data.OfficialSupport
import com.caretrail.app.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class OfficialSupportScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun show() { compose.setContent { CareTrailTheme { Page(title = "Your journey", back = {}) { OfficialSupportPanel(L("en", emptyMap(), AppViewModel(ApplicationProvider.getApplicationContext<Application>()))) } } } }
    @Test fun startsClosedWithoutOpeningSources() {
        show()
        compose.onNodeWithText("Government support: official guidance").assertExists()
        compose.onNodeWithText("PMMVY: maternity benefit").assertDoesNotExist()
        compose.onRoot().captureRoboImage()
    }
    @Test fun explainsAccessWithoutEligibilityPromise() {
        show()
        compose.onNodeWithText("Government support: official guidance").performClick()
        compose.onNodeWithText("PMMVY: maternity benefit").assertExists()
        compose.onRoot().captureRoboImage()
    }
    @Test fun officialLinksHaveNoPatientParameters() {
        assertEquals(3, OfficialSupport.resources.size)
        OfficialSupport.resources.forEach {
            val uri = java.net.URI(it.url)
            assertEquals("https", uri.scheme)
            assertTrue(uri.host == "www.wcd.gov.in" || uri.host == "www.nhm.gov.in")
            assertFalse(it.url.contains("journey")); assertFalse(it.url.contains("token"))
            assertTrue(it.access.contains("Ask"))
        }
    }
}
