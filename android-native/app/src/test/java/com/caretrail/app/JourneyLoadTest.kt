package com.caretrail.app

import com.caretrail.app.data.Journey
import com.caretrail.app.data.NextUp
import com.caretrail.app.data.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyLoadTest {
    @Test fun freshInstallShowsSlowNoticeThenStopsWaiting() = runTest {
        var cancelled = false
        val loader = JourneyLoader(backgroundScope, { "a" }, {
            try { awaitCancellation() } finally { cancelled = true }
        }, { null })
        loader.refresh(); runCurrent()
        assertEquals(JourneyState.Loading(), loader.state.value)
        advanceTimeBy(8_001); runCurrent()
        assertEquals(JourneyState.Loading(true), loader.state.value)
        advanceTimeBy(37_000); runCurrent()
        assertTrue(loader.state.value is JourneyState.Failed)
        assertTrue(cancelled)
    }

    @Test fun overlappingGateLoadsShareOneRequest() = runTest {
        var calls = 0
        val loader = JourneyLoader(backgroundScope, { "a" }, { calls++; delay(1_000); Repo.Loaded(Journey(journey_id = "j1"), false) }, { null })
        loader.refresh(); runCurrent(); loader.refresh(); loader.refresh(); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1_001); runCurrent()
        assertEquals("j1", (loader.state.value as JourneyState.Ready).journey.journey_id)
    }

    @Test fun retryCancelsThePreviousLoadInsteadOfOverlappingIt() = runTest {
        var calls = 0; var cancelled = 0
        val loader = JourneyLoader(backgroundScope, { "a" }, {
            calls++
            if (calls == 1) try { awaitCancellation() } finally { cancelled++ }
            Repo.Loaded(Journey(journey_id = "retry"), false)
        }, { null })
        loader.refresh(); runCurrent(); loader.refresh(force = true); runCurrent()
        assertEquals(2, calls); assertEquals(1, cancelled)
        assertEquals("retry", (loader.state.value as JourneyState.Ready).journey.journey_id)
    }

    @Test fun slowNextUpDoesNotHideLoadedHome() = runTest {
        val loader = JourneyLoader(backgroundScope, { "a" }, { Repo.Loaded(Journey(journey_id = "j1"), false) }, { awaitCancellation() })
        loader.refresh(); runCurrent()
        assertTrue(loader.state.value is JourneyState.Ready)
        advanceTimeBy(15_001); runCurrent()
        assertTrue(loader.state.value is JourneyState.Ready)
        assertNull((loader.state.value as JourneyState.Ready).nextUp)
    }

    @Test fun accountSwitchCancelsLoadAndNeverPublishesOldPatient() = runTest {
        var token = "a"
        val loader = JourneyLoader(backgroundScope, { token }, { delay(1_000); Repo.Loaded(Journey(journey_id = "old"), false) }, { null })
        loader.refresh(); runCurrent(); loader.reset(); token = "b"
        advanceTimeBy(2_000); runCurrent()
        assertEquals(JourneyState.Loading(), loader.state.value)
    }

    @Test fun changedTokenWithoutResetStillCannotPublishOldPatient() = runTest {
        var token = "a"
        val loader = JourneyLoader(backgroundScope, { token }, { delay(1_000); Repo.Loaded(Journey(journey_id = "old"), false) }, { null })
        loader.refresh(); runCurrent(); token = "b"
        advanceTimeBy(2_000); runCurrent()
        assertEquals(JourneyState.Loading(), loader.state.value)
    }

    @Test fun emptyFirstLoadRoutesToOnboarding() = runTest {
        val loader = JourneyLoader(backgroundScope, { "a" }, { Repo.Loaded(null, false) }, { error("next-up must not run") })
        loader.refresh(); runCurrent()
        assertEquals(JourneyState.Empty, loader.state.value)
    }

    @Test fun errorAfterRetryCanBeRecovered() = runTest {
        var fail = true
        val loader = JourneyLoader(backgroundScope, { "a" }, {
            if (fail) error("network down")
            Repo.Loaded(Journey(journey_id = "ok"), false)
        }, { null })
        loader.refresh(); runCurrent(); assertTrue(loader.state.value is JourneyState.Failed)
        fail = false; loader.refresh(force = true); runCurrent()
        assertTrue(loader.state.value is JourneyState.Ready)
    }
}
