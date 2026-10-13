package com.caretrail.app

import com.caretrail.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun res(n: String) = javaClass.classLoader!!.getResource(n)!!.readText()

    @Test fun journeyFixtureParses() {
        val j = AppJson.decodeFromString<Journey>(res("journey.json"))
        assertTrue(j.milestones.isNotEmpty())
        val f = Logic.progressFraction(j)
        assertTrue(f in 0f..1f)
        assertTrue(Logic.zones(j).values.sumOf { it.size } > 0)
    }

    @Test fun nextUpFixtureParses() {
        AppJson.decodeFromString<NextUp>(res("nextup.json"))
    }

    @Test fun trimesterBoundaries() {
        assertEquals(0, Logic.trimester(13)); assertEquals(1, Logic.trimester(14))
        assertEquals(1, Logic.trimester(27)); assertEquals(2, Logic.trimester(28))
    }

    @Test fun i18nEveryLabelHasAllLanguages() {
        assertTrue(I18nData.labels.size >= 80)
        // Every label has English (the runtime translator fills the rest, as on the web).
        I18nData.labels.forEach { (k, v) -> assertTrue("label $k", v.containsKey("en")) }
    }

    @Test fun dateRoundTrip() {
        val ms = Logic.millisFromIso("2026-10-13")!!
        assertEquals("2026-10-13", Logic.isoFromMillis(ms))
        assertNull(Logic.millisFromIso("nope"))
    }

    @Test fun inboxAndMilestoneParse() {
        AppJson.decodeFromString<Inbox>(res("rem.json"))
        val m = AppJson.decodeFromString<Milestone>(res("ms.json"))
        assertEquals("first_consultation", m.key)
        assertEquals("6-10", m.windowLabel)
    }
}
