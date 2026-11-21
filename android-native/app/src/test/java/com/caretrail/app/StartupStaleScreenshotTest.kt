package com.caretrail.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.L
import com.caretrail.app.ui.StaleRefreshNote
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Pixels for the cache-first refresh state: the updating note above cached content. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupStaleScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun staleRefreshStateShowsUpdatingNoteAboveContent() {
        composeRule.setContent {
            val vm = AppViewModel(ApplicationProvider.getApplicationContext())
            val l = L("en", emptyMap(), vm)
            CareTrailTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    StaleRefreshNote(l, Modifier.padding(bottom = 8.dp))
                    Box(Modifier.weight(1f)) { Text("Cached timeline content") }
                }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
