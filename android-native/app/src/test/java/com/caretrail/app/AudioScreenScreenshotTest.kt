package com.caretrail.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.caretrail.app.ui.ListenControl
import com.caretrail.app.ui.ScreenAudio
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real production Listen control, fake transport/player. No claim of audible-device testing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp-port")
class AudioScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preparingDisablesDuplicateTapsAndStopCancels() {
        var calls = 0
        val response = CompletableDeferred<ByteArray>()
        compose.setContent {
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf(ScreenAudio.State.IDLE) }
            val audio = remember { ScreenAudio(scope, { error("pending request must not create player") }, { state = it }) }
            Column(Modifier.padding(16.dp)) {
                ListenControl(audio, state, "Listen") { calls++; response.await() }
            }
            DisposableEffect(audio) { onDispose { audio.close() } }
        }
        compose.onNodeWithText("🔊 Listen").performClick()
        compose.onNodeWithText("Preparing audio...").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Stop audio").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Stop audio").performClick()
        compose.onNodeWithText("🔊 Listen").assertIsEnabled()
    }

    @Test fun playingHasVisibleStopAndReleasesOnRemoval() {
        var releases = 0
        var visible by mutableStateOf(true)
        compose.setContent {
            if (visible) {
                val scope = rememberCoroutineScope()
                var state by remember { mutableStateOf(ScreenAudio.State.IDLE) }
                val audio = remember { ScreenAudio(scope, { object : ScreenAudio.Playback {
                    override fun prepare(bytes: ByteArray, ready: () -> Unit, finished: () -> Unit, error: () -> Unit) { ready() }
                    override fun start() {}
                    override fun release() { releases++ }
                } }, { state = it }) }
                Column(Modifier.padding(16.dp)) { ListenControl(audio, state, "Listen") { byteArrayOf(1) } }
                DisposableEffect(audio) { onDispose { audio.close() } }
            }
        }
        compose.onNodeWithText("🔊 Listen").performClick()
        compose.onNodeWithText("Stop audio").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, releases) }
    }
}
