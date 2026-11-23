package com.caretrail.app

import com.caretrail.app.ui.ScreenAudio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScreenAudioTest {
    private class FakePlayer : ScreenAudio.Playback {
        var starts = 0; var releases = 0
        lateinit var ready: () -> Unit
        lateinit var finish: () -> Unit
        lateinit var fail: () -> Unit
        override fun prepare(bytes: ByteArray, ready: () -> Unit, finished: () -> Unit, error: () -> Unit) {
            this.ready = ready; finish = finished; fail = error
        }
        override fun start() { starts++ }
        override fun release() { releases++ }
    }
    @Test fun repeatedListenDuringFetchStartsOnlyOneRequestAndCanStop() = runTest {
        var requests = 0
        val result = CompletableDeferred<ByteArray>()
        val audio = ScreenAudio(this, { FakePlayer() })
        val load: suspend () -> ByteArray = { requests++; result.await() }
        audio.listen(load); audio.listen(load); runCurrent()
        audio.listen(load); runCurrent()
        assertEquals(1, requests)
        assertEquals(ScreenAudio.State.LOADING, audio.state)
        audio.stop(); result.complete(byteArrayOf(1)); runCurrent()
        assertEquals(ScreenAudio.State.IDLE, audio.state)
    }
    @Test fun latestRequestOwnsTheOnlyPlayer() = runTest {
        val players = mutableListOf<FakePlayer>()
        val audio = ScreenAudio(this, { FakePlayer().also { players += it } })
        audio.play { byteArrayOf(1) }; runCurrent()
        players[0].ready()
        audio.play { byteArrayOf(2) }; runCurrent()
        assertEquals(1, players[0].releases)
        players[0].ready(); players[0].finish(); players[0].fail()
        assertEquals(1, players[0].starts)
        players[1].ready()
        assertEquals(ScreenAudio.State.PLAYING, audio.state)
        players[1].finish()
        assertEquals(1, players[1].releases)
        assertEquals(ScreenAudio.State.IDLE, audio.state)
    }
    @Test fun cancelledLateDownloadNeverCreatesPlayer() = runTest {
        val result = CompletableDeferred<ByteArray>()
        var creations = 0
        val audio = ScreenAudio(this, { creations++; FakePlayer() })
        audio.play { withContext(NonCancellable) { result.await() } }; runCurrent()
        assertEquals(ScreenAudio.State.LOADING, audio.state)
        audio.stop()
        result.complete(byteArrayOf(1)); runCurrent()
        assertEquals(0, creations)
        assertEquals(ScreenAudio.State.IDLE, audio.state)
    }
    @Test fun closeDuringPreparationRejectsLateReadyAndFurtherPlay() = runTest {
        val player = FakePlayer()
        val audio = ScreenAudio(this, { player })
        audio.play { byteArrayOf(1) }; runCurrent()
        audio.close(); player.ready()
        audio.play { fail("closed owner must not fetch"); byteArrayOf() }; runCurrent()
        assertEquals(0, player.starts)
        assertEquals(1, player.releases)
    }
    @Test fun backgroundStopsAndCannotStartUntilResumed() = runTest {
        val player = FakePlayer()
        val audio = ScreenAudio(this, { player })
        audio.play { byteArrayOf(1) }; runCurrent(); player.ready()
        audio.setActive(false)
        audio.play { fail("background must not fetch"); byteArrayOf() }; runCurrent()
        assertEquals(1, player.releases)
        audio.setActive(true)
        assertEquals(ScreenAudio.State.IDLE, audio.state) // no automatic restart
    }
    @Test fun downloadAndPrepareFailuresResetForRetry() = runTest {
        var errors = 0
        val player = FakePlayer()
        val audio = ScreenAudio(this, { player }, failed = { errors++ })
        audio.play { error("offline") }; runCurrent()
        assertEquals(ScreenAudio.State.IDLE, audio.state)
        audio.play { byteArrayOf(1) }; runCurrent(); player.fail()
        assertEquals(ScreenAudio.State.IDLE, audio.state)
        assertEquals(2, errors)
        assertEquals(1, player.releases)
    }
}
