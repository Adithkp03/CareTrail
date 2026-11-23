package com.caretrail.app

import com.caretrail.app.data.Api
import com.caretrail.app.data.SessionExpired
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiCancellationTest {
    @Test fun timeoutCancelsTheUnderlyingSocket() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start()
        val api = Api(server.url("/").toString(), { "a" })
        try {
            try { withTimeout(250) { api.journeys() }; fail("expected timeout") }
            catch (_: TimeoutCancellationException) { }
            withTimeout(2_000) { while (api.client.dispatcher.runningCallsCount() != 0) delay(10) }
            assertEquals(0, api.client.dispatcher.runningCallsCount())
        } finally { server.shutdown() }
    }

    @Test fun old401CannotExpireNewSession() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}").setBodyDelay(200, TimeUnit.MILLISECONDS)); server.start()
        var token = "old"; var expired = false
        val api = Api(server.url("/").toString(), { token }, { expired = true })
        try {
            val call = async { runCatching { api.journeys() }.exceptionOrNull() }
            withTimeout(2_000) { while (server.requestCount == 0) delay(10) }
            token = "new"
            assertTrue(call.await() is SessionExpired)
            assertFalse(expired)
        } finally { server.shutdown() }
    }
}
