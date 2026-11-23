package com.caretrail.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** One owner for downloads AND playback. All calls/callbacks run on the main thread. */
internal class ScreenAudio(
    private val scope: CoroutineScope,
    private val factory: () -> Playback,
    private val changed: (State) -> Unit = {},
    private val failed: () -> Unit = {},
) {
    enum class State { IDLE, LOADING, PLAYING }
    interface Playback {
        fun prepare(bytes: ByteArray, ready: () -> Unit, finished: () -> Unit, error: () -> Unit)
        fun start()
        fun release()
    }
    var state = State.IDLE
        private set
    private var generation = 0L
    private var job: Job? = null
    private var player: Playback? = null
    private var closed = false
    private var active = true
    val revision get() = generation
    fun isCurrent(revision: Long) = !closed && active && generation == revision

    private fun state(value: State) { state = value; changed(value) }

    fun stop() {
        ++generation // Also rejects late responses from non-cancellable network calls.
        job?.cancel(); job = null
        val old = player; player = null
        old?.release()
        state(State.IDLE)
    }

    fun listen(load: suspend () -> ByteArray) {
        when (state) {
            State.IDLE -> play(load)
            State.LOADING -> Unit // Never start a second request, even before recomposition.
            State.PLAYING -> stop()
        }
    }

    fun setActive(value: Boolean) { active = value; if (!value) stop() }

    fun play(load: suspend () -> ByteArray) {
        if (closed || !active) return
        stop()
        val request = generation
        state(State.LOADING)
        job = scope.launch {
            try {
                val bytes = load()
                if (closed || !active || generation != request) return@launch
                val current = factory()
                player = current
                fun active() = !closed && generation == request && player === current
                current.prepare(bytes,
                    ready = {
                        if (active()) {
                            try { current.start(); state(State.PLAYING) }
                            catch (_: Exception) { if (active()) { stop(); failed() } }
                        }
                    },
                    finished = { if (active()) stop() },
                    error = { if (active()) { stop(); failed() } },
                )
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (!closed && generation == request) { stop(); failed() } }
        }
    }

    fun close() { closed = true; stop() }
}

private class CachedAudioPlayer(private val context: Context) : ScreenAudio.Playback {
    private var media: MediaPlayer? = null
    private var file: File? = null
    override fun prepare(bytes: ByteArray, ready: () -> Unit, finished: () -> Unit, error: () -> Unit) {
        val audio = File.createTempFile("ct_audio", ".wav", context.cacheDir)
        file = audio
        audio.writeBytes(bytes)
        val current = MediaPlayer()
        media = current
        current.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        current.setOnPreparedListener { ready() }
        current.setOnCompletionListener { finished() }
        current.setOnErrorListener { _, _, _ -> error(); true }
        current.setDataSource(audio.path)
        current.prepareAsync()
    }
    override fun start() { checkNotNull(media).start() }
    override fun release() {
        media?.let {
            it.setOnPreparedListener(null); it.setOnCompletionListener(null); it.setOnErrorListener(null)
            runCatching { it.release() }
        }
        media = null
        file?.delete(); file = null
    }
}

@Composable
internal fun rememberScreenAudio(onError: () -> Unit): Pair<ScreenAudio, ScreenAudio.State> {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestError by rememberUpdatedState(onError)
    var state by remember { mutableStateOf(ScreenAudio.State.IDLE) }
    val audio = remember(context, scope) {
        ScreenAudio(scope, { CachedAudioPlayer(context) }, { state = it }, { latestError() })
    }
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) audio.setActive(true)
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) audio.setActive(false)
        }
        audio.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); audio.close() }
    }
    return audio to state
}

@Composable
internal fun ListenControl(audio: ScreenAudio, state: ScreenAudio.State, label: String, preparingLabel: String = "Preparing audio...", stopLabel: String = "Stop audio", load: suspend () -> ByteArray) {
    OutlineButton(when (state) {
        ScreenAudio.State.IDLE -> "🔊 $label"
        ScreenAudio.State.LOADING -> preparingLabel
        ScreenAudio.State.PLAYING -> stopLabel
    }, enabled = state != ScreenAudio.State.LOADING) {
        audio.listen(load)
    }
    if (state == ScreenAudio.State.LOADING) OutlineButton(stopLabel) { audio.stop() }
}
