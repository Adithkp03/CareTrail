package com.caretrail.app

import android.content.Context
import com.caretrail.app.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Synthetic local-only tests. Reopening Store is not a physical power-cut test. */
@RunWith(RobolectricTestRunner::class)
class RecordSafetyTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("record-safety-test", Context.MODE_PRIVATE)
    @Before fun reset() { prefs.edit().clear().commit() }
    @Test fun unfinishedRawTextSurvivesStoreReopenWithoutConfirmingBirth() {
        val value = BirthDraft(date = "2026-11-23", weight = "2.", weeks = "3", gender = "girl")
        Store(prefs).saveBirthDraft("synthetic-a", value)
        assertEquals(value, Store(prefs).birthDraft("synthetic-a"))
        assertFalse(Store(prefs).birthRecord("synthetic-a").confirmed)
        assertTrue(Store(prefs).growthEntries("synthetic-a").isEmpty())
    }
    @Test fun confirmationUsesExistingRecordKeysAndClearsOnlyItsDraft() {
        val store = Store(prefs)
        store.saveBirthDraft("a", BirthDraft(weight = "2."))
        store.saveBirthDraft("b", BirthDraft(weight = "3."))
        store.confirmBirthDraft("a", BirthRecord(date = "2026-11-23", gender = "girl", weight_kg = 2.8))
        val reopened = Store(prefs)
        assertTrue(reopened.birthRecord("a").confirmed)
        assertEquals(2.8, reopened.birthRecord("a").weight_kg, 0.0)
        assertEquals(1, reopened.growthEntries("a").size)
        assertNull(reopened.birthDraft("a"))
        assertNotNull(reopened.birthDraft("b"))
        assertNotNull(prefs.getString("cache:s2:birth:a", null))
    }
    @Test fun reconfirmationDoesNotDuplicateOrOverwriteRecordedGrowth() {
        val store = Store(prefs)
        val initial = GrowthEntry(date = "2026-11-20", age_months = 0.0, weight_kg = 2.5)
        store.saveGrowthEntries("a", listOf(initial))
        store.confirmBirthDraft("a", BirthRecord(date = "2026-11-20", weight_kg = 2.6))
        assertEquals(listOf(initial), store.growthEntries("a"))
    }
    @Test fun draftsAreScopedToJourneyAndLogoutStillClearsPrivateCaches() {
        val store = Store(prefs)
        store.lang = "ml"
        store.saveBirthDraft("a", BirthDraft(time = "12:"))
        store.putCache("s4:mood:a", "synthetic-private")
        store.setMovements("a", "2026-11-23", 2)
        assertNull(store.birthDraft("b"))
        store.clearAccount()
        val reopened = Store(prefs)
        assertNull(reopened.birthDraft("a"))
        assertNull(reopened.getCache("s4:mood:a"))
        assertEquals(0, reopened.movements("a", "2026-11-23"))
        assertEquals("ml", reopened.lang)
    }
    @Test fun corruptDraftFailsVisiblyWithoutBeingDeleted() {
        prefs.edit().putString("cache:birth-draft:a", "broken-json").commit()
        assertTrue(runCatching { Store(prefs).birthDraft("a") }.isFailure)
        assertEquals("broken-json", prefs.getString("cache:birth-draft:a", null))
    }
    @Test fun failedDurableCommitIsNotReportedAsSuccess() {
        val delegate = prefs
        val failing = object : android.content.SharedPreferences by delegate {
            override fun edit(): android.content.SharedPreferences.Editor {
                val editor = delegate.edit()
                return object : android.content.SharedPreferences.Editor by editor {
                    override fun commit() = false
                }
            }
        }
        assertTrue(runCatching { Store(failing).saveBirthDraft("a", BirthDraft(time = "12:")) }.isFailure)
        assertNull(Store(prefs).birthDraft("a"))
    }
    @Test fun discardDoesNotRemoveConfirmedRecord() {
        val store = Store(prefs)
        store.saveBirthRecord("a", BirthRecord(confirmed = true, date = "2026-11-23"))
        store.saveBirthDraft("a", BirthDraft(time = "12:"))
        store.discardBirthDraft("a")
        assertNull(store.birthDraft("a"))
        assertTrue(store.birthRecord("a").confirmed)
    }
}
