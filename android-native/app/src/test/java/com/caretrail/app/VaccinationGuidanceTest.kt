package com.caretrail.app

import com.caretrail.app.data.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VaccinationGuidanceTest {
    private fun payload() = javaClass.getResource("/clinical-vaccination.json")!!.readText()

    @Test fun priorPayloadWithoutGuidanceStillParses() {
        val fields = AppJson.parseToJsonElement(payload()).jsonObject.toMutableMap()
        fields.remove("vaccination_guidance")
        val old = AppJson.decodeFromString<Journey>(JsonObject(fields).toString())
        assertNull(old.vaccination_guidance)
        assertEquals(14, old.milestones.size)
        assertEquals(14, Logic.zones(old).values.sumOf { it.size })
    }

    @Test fun approvedExamNoteAndExistingHospitalWarningParseExactly() {
        val journey = AppJson.decodeFromString<Journey>(payload())
        assertEquals(
            "At the 38–39-week review, your doctor may recommend an internal examination if needed. They will explain the reason and ask for your consent. Know when to go to the hospital: regular pains, leaking, bleeding.",
            journey.milestones.single { it.key == "review_38w" }.prep_notes
        )
    }

    @Test fun discussionMetadataCannotChangeStoredHistoryOrStatusZones() {
        val original = AppJson.decodeFromString<Journey>(payload())
        val historical = original.copy(milestones = original.milestones.map {
            when (it.key) {
                "tdap_vaccine" -> it.copy(title = "Historical vaccination label", completed_at = "2026-10-01", status = "done", completion_level = "self_reported")
                "review_38w" -> it.copy(scheduled_date = "2027-03-01", status = "upcoming")
                else -> it
            }
        })
        val oldShape = historical.copy(vaccination_guidance = null)
        val restored = AppJson.decodeFromString<Journey>(AppJson.encodeToString(historical))
        assertEquals(historical, restored)
        assertEquals(oldShape.milestones, restored.milestones)
        assertEquals(Logic.zones(oldShape), Logic.zones(restored))
        assertEquals(oldShape.summary, restored.summary)
        assertEquals("clinician_discussion", restored.vaccination_guidance!!.kind)
        assertTrue(restored.vaccination_guidance!!.booster.contains("two Td/TT doses"))
        assertEquals("Historical vaccination label", restored.milestones.single { it.key == "tdap_vaccine" }.title)
        assertEquals(14, restored.milestones.size)
    }
}
