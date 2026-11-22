package com.caretrail.app

import com.caretrail.app.data.Api
import com.caretrail.app.ui.GoogleAccountFlow
import com.caretrail.app.ui.ScreenAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Integration of the reviewed audio/Google owners with the shared cancellable Api. */
class CombinedLifecycleTest {
    private class Player : ScreenAudio.Playback {
        var starts = 0
        var content = ""
        override fun prepare(bytes: ByteArray, ready: () -> Unit, finished: () -> Unit, error: () -> Unit) { content = String(bytes); ready() }
        override fun start() { starts++ }
        override fun release() {}
    }

    @Test fun stopCancelsTheSocketAndCannotStartLatePlayback() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start()
        val api = Api(server.url("/").toString(), { "session" })
        val player = Player()
        val audio = ScreenAudio(this, { player })
        try {
            audio.listen { api.getBytes("/audio") }
            withTimeout(2_000) { while (server.requestCount < 1) delay(10) }
            audio.stop()
            withTimeout(2_000) { while (api.client.dispatcher.runningCallsCount() != 0) delay(10) }
            assertEquals(ScreenAudio.State.IDLE, audio.state)
            assertEquals(0, player.starts)
        } finally { audio.close(); server.shutdown() }
    }

    @Test fun restartPlaysOnlyTheNewResponse() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("old").setBodyDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("new")); server.start()
        val api = Api(server.url("/").toString(), { "session" })
        val player = Player()
        val audio = ScreenAudio(this, { player })
        try {
            audio.listen { api.getBytes("/audio") }
            withTimeout(2_000) { while (server.requestCount < 1) delay(10) }
            audio.stop()
            audio.listen { api.getBytes("/audio") }
            withTimeout(2_000) { while (audio.state != ScreenAudio.State.PLAYING) delay(10) }
            delay(350)
            assertEquals(1, player.starts)
            assertEquals("new", player.content)
        } finally { audio.close(); server.shutdown() }
    }

    @Test fun googleApiProbeDoesNotAnnounceSuccessUntilConsentAndCareTrailSession() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"no_caretrail_account"}"""))
        server.enqueue(MockResponse().setBody("""{"token":"care-session","patient_id":"patient-new"}""")); server.start()
        val api = Api(server.url("/").toString(), { null })
        var success = 0; var patientId = ""
        val flow = GoogleAccountFlow(api::supabaseExchange, api::linkSupabase) { success++; patientId = it.patient_id }
        try {
            flow.begin("synthetic-supabase-token", "en")
            assertTrue(flow.needsAccount); assertEquals(0, success)
            flow.create(true, "en")
            assertEquals(1, success); assertEquals("patient-new", patientId)
            val probe = server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()
            val create = server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()
            assertTrue(probe.contains("\"create\":false"))
            assertTrue(create.contains("\"consent\":true"))
        } finally { server.shutdown() }
    }
}
