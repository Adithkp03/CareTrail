package com.caretrail.app

import com.caretrail.app.data.AppJson
import com.caretrail.app.data.SupabaseAuth
import com.caretrail.app.data.SupabaseAuthException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleAuthTest {

    @Test fun googleIdTokenGrantSendsNonce() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"access_token":"sb-access-123"}"""))
        server.start()
        val auth = SupabaseAuth(server.url("/").toString().trimEnd('/'), "anon-test-key")
        val token = auth.signInWithGoogleIdToken("google-id-token-abc", "nonce-xyz")
        assertEquals("sb-access-123", token)
        val sent = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=id_token", sent.path)
        assertEquals("anon-test-key", sent.getHeader("apikey"))
        val body = AppJson.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals("google", body["provider"]!!.jsonPrimitive.content)
        assertEquals("google-id-token-abc", body["id_token"]!!.jsonPrimitive.content)
        // Supabase nonce checks are enabled on the project; the raw nonce must be
        // sent so the hashed nonce inside the Google ID token can be verified.
        assertEquals("nonce-xyz", body["nonce"]!!.jsonPrimitive.content)
        server.shutdown()
    }

    @Test fun googleIdTokenGrantFailureThrows() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad nonce"}"""))
        server.start()
        val auth = SupabaseAuth(server.url("/").toString().trimEnd('/'), "anon-test-key")
        try {
            auth.signInWithGoogleIdToken("google-id-token-abc", "nonce-xyz")
            fail("expected SupabaseAuthException")
        } catch (e: SupabaseAuthException) {
            assertTrue(e.message!!.contains("400"))
        }
        server.shutdown()
    }

    @Test fun googleSignInHiddenWithoutAnonKey() {
        assertTrue(!SupabaseAuth("https://example.supabase.co", "").configured)
        assertTrue(SupabaseAuth("https://example.supabase.co", "anon-test-key").configured)
    }
}
