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

    @Test fun rhEngineDecisionTree() {
        // Nothing recorded -> ask for the maternal group first.
        assertEquals(RhEngine.Status.RECORD_MATERNAL, RhEngine.status(RhProfile()))
        // Rh-positive mother -> no isoimmunization path.
        assertEquals(RhEngine.Status.NO_RISK, RhEngine.status(RhProfile(maternal_group = "A+")))
        // Rh-negative mother, partner unknown -> prompt partner blood group.
        assertEquals(RhEngine.Status.PROMPT_PARTNER, RhEngine.status(RhProfile(maternal_group = "O-")))
        // Partner Rh-negative -> no risk.
        assertEquals(RhEngine.Status.PARTNER_NEGATIVE, RhEngine.status(RhProfile(maternal_group = "O-", partner_group = "B-")))
        // Partner Rh-positive -> ICT tracking.
        assertEquals(RhEngine.Status.TRACK_ICT, RhEngine.status(RhProfile(maternal_group = "O-", partner_group = "B+")))
    }

    @Test fun rhEngine28WeekPlan() {
        val p = RhProfile(maternal_group = "O-", partner_group = "B+")
        // Before 28 weeks only the initial ICT is due.
        var a = RhEngine.actions(p, 22)
        assertEquals(3, a.size)
        assertTrue(a[0].due && !a[0].done)
        assertTrue(!a[1].due && !a[2].due)
        assertEquals(1, RhEngine.openDueActions(p, 22))
        // Initial ICT logged -> it is done; at 28 weeks repeat ICT + Anti-D come due.
        val withIct = p.copy(ict_log = listOf(IctRecord(date = "2026-09-01", weeks = 12, result = "negative")))
        a = RhEngine.actions(withIct, 28)
        assertTrue(a[0].done)
        assertTrue(a[1].due && !a[1].done)
        assertTrue(a[2].due && !a[2].done)
        assertEquals(2, RhEngine.openDueActions(withIct, 28))
        // Repeat ICT at 28+ weeks and Anti-D given -> nothing left.
        val full = withIct.copy(
            ict_log = withIct.ict_log + IctRecord(date = "2026-12-01", weeks = 28, result = "negative"),
            anti_d_given = true, anti_d_date = "2026-12-02",
        )
        assertEquals(0, RhEngine.openDueActions(full, 29))
    }

    @Test fun dangerSignTriage() {
        assertFalse(DangerSigns.urgent(emptyList()))
        assertTrue(DangerSigns.urgent(listOf("severe_headache")))
        assertEquals(28, DangerSigns.START_WEEK)
        DangerSigns.PREECLAMPSIA.forEach { (k, label) -> assertEquals(label, DangerSigns.symptomLabel(k)) }
    }

    @Test fun sprint1RecordsRoundTrip() {
        val intake = HealthIntake(conditions = listOf("hypertension"), surgeries = listOf("previous_cesarean"), allergies = "penicillin", completed = true)
        val back = AppJson.decodeFromString<HealthIntake>(AppJson.encodeToString(HealthIntake.serializer(), intake))
        assertEquals(intake, back)
        val reg = RegistrationData(hiv_mother = true, mcp_number = "MCP-123", mcp_images = listOf("/data/x/1.jpg"))
        assertEquals(reg, AppJson.decodeFromString<RegistrationData>(AppJson.encodeToString(RegistrationData.serializer(), reg)))
        val log = DangerLog(date = "2026-10-27", weeks = 30, symptoms = listOf("blurry_vision"))
        assertEquals(log, AppJson.decodeFromString<DangerLog>(AppJson.encodeToString(DangerLog.serializer(), log)))
    }
}
