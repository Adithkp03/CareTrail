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

    @Test fun whoLmsMedianGivesZeroZ() {
        // A boy at the WHO median birth weight (3.3464 kg) has z ~= 0.
        val z = WhoGrowth.zScore(WhoLms.weightBoys, 0.0, 3.3464)!!
        assertTrue(Math.abs(z) < 0.01)
        // 24-month girl at median head circumference.
        val zh = WhoGrowth.zScore(WhoLms.headGirls, 24.0, 47.1822)!!
        assertTrue(Math.abs(zh) < 0.01)
        // Outside the 0-24 month tabulation -> no z-score.
        assertNull(WhoGrowth.zScore(WhoLms.weightBoys, 30.0, 12.0))
        assertNull(WhoGrowth.zScore(WhoLms.weightBoys, 6.0, 0.0))
    }

    @Test fun whoLmsValueAtInvertsZScore() {
        val v = WhoGrowth.valueAt(0.3487, 3.3464, 0.14602, -2.0)
        assertTrue(v < 3.3464)
        val z = WhoGrowth.zScore(WhoLms.weightBoys, 0.0, v)!!
        assertTrue(Math.abs(z - -2.0) < 0.05)
        assertEquals(WhoGrowth.Band.BELOW, WhoGrowth.band(-2.5))
        assertEquals(WhoGrowth.Band.ON_TRACK, WhoGrowth.band(0.0))
        assertEquals(WhoGrowth.Band.ABOVE, WhoGrowth.band(2.5))
    }

    @Test fun whoInterpolationBetweenMonths() {
        // Age 1.5 months lies between tabulated rows; z must still resolve.
        val z = WhoGrowth.zScore(WhoLms.lengthBoys, 1.5, 57.5)
        assertNotNull(z)
        assertTrue(z!! in -4.0..4.0)
    }

    @Test fun vaccineScheduleStates() {
        val birth = "2026-09-01"
        val bcg = VaccineSchedule.ITEMS.first { it.key == "bcg" }
        assertEquals("2026-09-01", VaccineSchedule.dueDate(birth, bcg))
        val penta1 = VaccineSchedule.ITEMS.first { it.key == "penta1" }
        assertEquals("2026-10-13", VaccineSchedule.dueDate(birth, penta1))
        val done = VaccineLog("bcg", true, "2026-09-01")
        assertEquals(VaccineSchedule.State.DONE, VaccineSchedule.state(bcg, birth, done, java.time.LocalDate.parse("2026-09-02")))
        // Overdue when today is past the due date.
        assertEquals(VaccineSchedule.State.OVERDUE, VaccineSchedule.state(penta1, birth, null, java.time.LocalDate.parse("2026-11-01")))
        // Due soon within 14 days, upcoming beyond that.
        assertEquals(VaccineSchedule.State.DUE_SOON, VaccineSchedule.state(penta1, birth, null, java.time.LocalDate.parse("2026-10-10")))
        assertEquals(VaccineSchedule.State.UPCOMING, VaccineSchedule.state(penta1, birth, null, java.time.LocalDate.parse("2026-09-10")))
        // Open items skip completed vaccines.
        val open = VaccineSchedule.openItems(birth, mapOf("bcg" to done), java.time.LocalDate.parse("2026-10-01"))
        assertTrue(open.none { it.first.key == "bcg" })
        assertTrue(open.isNotEmpty())
    }

    @Test fun sprint2RecordsRoundTrip() {
        val birth = BirthRecord(confirmed = true, ga_weeks = 38, ga_days = 4, date = "2026-10-20", time = "14:35", gender = "girl", weight_kg = 2.9, head_cm = 34.0, length_cm = 48.0)
        assertEquals(birth, AppJson.decodeFromString<BirthRecord>(AppJson.encodeToString(BirthRecord.serializer(), birth)))
        val g = GrowthEntry(date = "2026-11-20", age_months = 1.02, weight_kg = 4.2)
        assertEquals(g, AppJson.decodeFromString<GrowthEntry>(AppJson.encodeToString(GrowthEntry.serializer(), g)))
        assertEquals(1.05, WhoGrowth.ageMonths("2026-10-19", "2026-11-20")!!, 0.01)
    }

    @Test fun haversineKnownDistance() {
        // Kochi (9.9312, 76.2673) to Thrissur (10.5276, 76.2144) is roughly 67 km.
        val d = Geo.haversineKm(9.9312, 76.2673, 10.5276, 76.2144)
        assertTrue(d in 60.0..75.0)
        assertEquals(0.0, Geo.haversineKm(9.93, 76.26, 9.93, 76.26), 0.001)
    }

    @Test fun governmentClassification() {
        assertTrue(Geo.isGovernment(mapOf("operator:type" to "public"), "Some Hospital"))
        assertTrue(Geo.isGovernment(emptyMap(), "Government Medical College, Thrissur"))
        assertTrue(Geo.isGovernment(emptyMap(), "Chellanam PHC"))
        assertFalse(Geo.isGovernment(emptyMap(), "Sunrise Speciality Hospital"))
    }

    @Test fun overpassParsesAndFilters() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":9.94,"lon":76.27,"tags":{"amenity":"hospital","name":"General Hospital","phone":"+911234"}},
            {"type":"way","id":2,"center":{"lat":9.90,"lon":76.29},"tags":{"amenity":"clinic","name":"Chellanam PHC"}},
            {"type":"node","id":3,"lat":9.95,"lon":76.28,"tags":{"healthcare":"blood_bank","name":"IMA Blood Bank"}},
            {"type":"node","id":4,"lat":9.95,"lon":76.28,"tags":{"amenity":"cafe","name":"Not a facility"}},
            {"type":"node","id":5,"lat":9.95,"lon":76.28,"tags":{"amenity":"hospital"}},
            {"type":"node","id":6,"lat":10.50,"lon":76.90,"tags":{"amenity":"hospital","name":"Far Away Hospital"}}
        ]}"""
        val out = Geo.parseOverpass(json, 9.93, 76.26, 10.0)
        // cafe and the unnamed node are dropped; the 70+ km hospital is outside 10 km.
        assertEquals(3, out.size)
        assertEquals("General Hospital", out[0].name)   // nearest first
        assertTrue(out[0].government)
        assertEquals("+911234", out[0].phone)
        assertTrue(out.any { it.kind == "blood_bank" })
        assertTrue(out.all { it.distance_km <= 10.0 })
    }

    @Test fun bpLevelBoundaries() {
        assertEquals(BpRules.Level.NORMAL, BpRules.level(120, 80))
        assertEquals(BpRules.Level.NORMAL, BpRules.level(139, 89))
        assertEquals(BpRules.Level.RAISED, BpRules.level(140, 85))
        assertEquals(BpRules.Level.RAISED, BpRules.level(130, 90))
        assertEquals(BpRules.Level.SEVERE, BpRules.level(160, 100))
        assertEquals(BpRules.Level.SEVERE, BpRules.level(150, 110))
        assertTrue(BpRules.latePreeclampsiaWarning(listOf(BpReading(date = "2026-10-27", systolic = 142, diastolic = 92))))
        assertFalse(BpRules.latePreeclampsiaWarning(listOf(BpReading(date = "2026-10-27", systolic = 118, diastolic = 76))))
    }

    @Test fun followUpStates() {
        val birth = "2026-09-15"
        val day10 = java.time.LocalDate.parse("2026-09-25")
        val day14 = java.time.LocalDate.parse("2026-09-29")
        val day50 = java.time.LocalDate.parse("2026-11-04")
        // 2-week review only applies to high-risk mothers.
        assertEquals(FollowUps.State.NOT_APPLICABLE, FollowUps.twoWeekState(birth, false, FollowUpDone(), day14))
        assertEquals(FollowUps.State.UPCOMING, FollowUps.twoWeekState(birth, true, FollowUpDone(), day10))
        assertEquals(FollowUps.State.DUE, FollowUps.twoWeekState(birth, true, FollowUpDone(), day14))
        assertEquals(FollowUps.State.DONE, FollowUps.twoWeekState(birth, true, FollowUpDone(two_week_done = "2026-09-30"), day50))
        // The 42-day checkup is mandatory for everyone.
        assertEquals(FollowUps.State.UPCOMING, FollowUps.checkup42State(birth, FollowUpDone(), day14))
        assertEquals(FollowUps.State.DUE, FollowUps.checkup42State(birth, FollowUpDone(), day50))
        assertEquals(FollowUps.State.DONE, FollowUps.checkup42State(birth, FollowUpDone(checkup42_done = "2026-10-30"), day50))
        assertEquals("2026-10-27", FollowUps.checkup42DueDate(birth))
    }

    @Test fun mentalHealthFlags() {
        assertFalse(MentalHealth.anyFlag(MoodCheckin(date = "2026-10-27")))
        assertTrue(MentalHealth.anyFlag(MoodCheckin(date = "2026-10-27", flags = listOf("guilt"))))
        assertTrue(MentalHealth.anyFlag(MoodCheckin(date = "2026-10-27", self_harm = true)))
        assertEquals(5, MentalHealth.SIGNS.size)
        assertTrue(MentalHealth.SOS_LINES.isNotEmpty())
    }

    @Test fun mindfulnessAffirmationRotates() {
        val a = Mindfulness.affirmationOfTheDay(java.time.LocalDate.parse("2026-10-27"))
        val b = Mindfulness.affirmationOfTheDay(java.time.LocalDate.parse("2026-10-28"))
        assertTrue(a.isNotEmpty())
        assertNotEquals(a, b)
        // Wraps around the list without ever indexing out of range.
        for (d in 0L..40L) assertTrue(Mindfulness.affirmationOfTheDay(java.time.LocalDate.parse("2026-10-01").plusDays(d)).isNotEmpty())
    }

    @Test fun sprint4RecordsRoundTrip() {
        val bp = BpReading(date = "2026-10-27", systolic = 128, diastolic = 84)
        assertEquals(bp, AppJson.decodeFromString<BpReading>(AppJson.encodeToString(BpReading.serializer(), bp)))
        val s = SugarReading(date = "2026-10-27", fasting = 95, post_meal = 140)
        assertEquals(s, AppJson.decodeFromString<SugarReading>(AppJson.encodeToString(SugarReading.serializer(), s)))
        val m = MoodCheckin(date = "2026-10-27", flags = listOf("crying_spells", "helpless"), self_harm = false)
        assertEquals(m, AppJson.decodeFromString<MoodCheckin>(AppJson.encodeToString(MoodCheckin.serializer(), m)))
        val hr = HighRiskRecord(gest_hypertension = true, gdm_insulin = true)
        assertEquals(hr, AppJson.decodeFromString<HighRiskRecord>(AppJson.encodeToString(HighRiskRecord.serializer(), hr)))
        assertTrue(hr.any())
        assertFalse(HighRiskRecord().any())
    }
}