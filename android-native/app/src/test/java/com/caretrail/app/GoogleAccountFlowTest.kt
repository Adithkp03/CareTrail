package com.caretrail.app

import com.caretrail.app.data.ApiException
import com.caretrail.app.data.TokenResponse
import com.caretrail.app.ui.GoogleAccountFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GoogleAccountFlowTest {
    private val session = TokenResponse("caretrail-session", "patient-correct")

    @Test fun knownGoogleAccountSignsInWithCorrectPatient() = runTest {
        var actual: TokenResponse? = null
        val flow = GoogleAccountFlow({ token, consent, lang, create ->
            assertEquals("supabase-session", token); assertFalse(consent); assertFalse(create); assertEquals("ml", lang)
            session
        }, { _, _, _ -> error("must not link by email") }, { actual = it })
        flow.begin("supabase-session", "ml")
        assertEquals(session, actual); assertFalse(flow.needsAccount)
    }

    @Test fun unknownAccountWaitsWithoutSuccessOrAutomaticCreation() = runTest {
        var successes = 0
        var calls = 0
        val flow = GoogleAccountFlow({ _, _, _, create ->
            calls++; assertFalse(create); throw ApiException(404, "no_caretrail_account")
        }, { _, _, _ -> error("must not link") }, { successes++ })
        flow.begin("supabase-session", "en")
        assertTrue(flow.needsAccount); assertEquals(1, calls); assertEquals(0, successes)
        try { flow.create(false, "en"); fail("consent required") } catch (_: IllegalArgumentException) {}
        assertEquals(1, calls); assertEquals(0, successes)
    }

    @Test fun explicitConsentCreatesThenReportsSuccess() = runTest {
        var successes = 0
        var calls = 0
        val flow = GoogleAccountFlow({ _, consent, lang, create ->
            calls++
            if (!create) throw ApiException(404, "no_caretrail_account")
            assertTrue(consent); assertEquals("hi", lang); session
        }, { _, _, _ -> error("must not link") }, { assertEquals(session, it); successes++ })
        flow.begin("supabase-session", "en"); flow.create(true, "hi")
        assertEquals(2, calls); assertEquals(1, successes); assertFalse(flow.needsAccount)
    }

    @Test fun linkRequiresPasswordAndUsesReturnedPatientId() = runTest {
        var actual: TokenResponse? = null
        var links = 0
        val flow = GoogleAccountFlow({ _, _, _, _ -> throw ApiException(404, "no_caretrail_account") },
            { token, phone, password ->
                links++; assertEquals("supabase-session", token); assertEquals("9000000002", phone)
                assertEquals("password-proof", password); session
            }, { actual = it })
        flow.begin("supabase-session", "en")
        try { flow.link("9000000002", ""); fail("password proof required") } catch (_: IllegalArgumentException) {}
        assertEquals(0, links); assertNull(actual)
        flow.link(" 9000000002 ", "password-proof")
        assertEquals(session, actual); assertFalse(flow.needsAccount)
    }

    @Test fun networkFailureDoesNotReportSuccessOrOfferCreate() = runTest {
        var successes = 0
        val flow = GoogleAccountFlow({ _, _, _, _ -> throw ApiException(0, "network") },
            { _, _, _ -> session }, { successes++ })
        try { flow.begin("supabase-session", "en"); fail("must fail") } catch (_: ApiException) {}
        assertEquals(0, successes); assertFalse(flow.needsAccount)
    }

    @Test fun unrelated404DoesNotOfferCreate() = runTest {
        val flow = GoogleAccountFlow({ _, _, _, _ -> throw ApiException(404, "Not found") },
            { _, _, _ -> session }, { fail("no session") })
        try { flow.begin("supabase-session", "en"); fail("must fail") } catch (_: ApiException) {}
        assertFalse(flow.needsAccount)
    }

    @Test fun conflictAndWrongPasswordDoNotSignIn() = runTest {
        for (code in listOf(401, 409)) {
            var successes = 0
            val flow = GoogleAccountFlow({ _, _, _, _ -> throw ApiException(404, "no_caretrail_account") },
                { _, _, _ -> throw ApiException(code, "rejected") }, { successes++ })
            flow.begin("supabase-session", "en")
            try { flow.link("9000000002", "wrong-password"); fail("must fail") } catch (_: ApiException) {}
            assertEquals(0, successes); assertTrue(flow.needsAccount)
        }
    }

    @Test fun malformedSessionCannotTriggerSignIn() = runTest {
        for (response in listOf(TokenResponse("", "patient"), TokenResponse("token", ""))) {
            val flow = GoogleAccountFlow({ _, _, _, _ -> response }, { _, _, _ -> session }, { fail("invalid session") })
            try { flow.begin("supabase-session", "en"); fail("must reject incomplete response") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun backClearsPendingCredential() = runTest {
        val flow = GoogleAccountFlow({ _, _, _, _ -> throw ApiException(404, "no_caretrail_account") },
            { _, _, _ -> session }, { fail("cleared") })
        flow.begin("supabase-session", "en"); flow.clear(); assertFalse(flow.needsAccount)
        try { flow.create(true, "en"); fail("cleared") } catch (_: IllegalStateException) {}
    }
}
