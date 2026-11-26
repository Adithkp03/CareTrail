package com.caretrail.app

import com.caretrail.app.data.AppJson
import com.caretrail.app.data.CommunityMsg
import com.caretrail.app.data.CommunityMsgList
import com.caretrail.app.data.CommunityRules
import com.caretrail.app.data.CommunityText
import org.junit.Assert.*
import org.junit.Test

/** Client rules mirror the backend; safety copy is pinned so it cannot be
 * weakened by accident. */
class CommunityTest {

    @Test fun nicknamesRejectPhonesReservedAndBlank() {
        assertNull(CommunityRules.nicknameProblem("Anu M"))
        assertNull(CommunityRules.nicknameProblem("meera_2"))
        assertNotNull(CommunityRules.nicknameProblem("9876543210"))      // phone-like
        assertNotNull(CommunityRules.nicknameProblem("123 4567"))        // 7 digits
        assertNotNull(CommunityRules.nicknameProblem("Moderator"))
        assertNotNull(CommunityRules.nicknameProblem("doctor"))
        assertNotNull(CommunityRules.nicknameProblem("x"))               // too short
        assertNotNull(CommunityRules.nicknameProblem("a".repeat(25)))    // too long
        assertNotNull(CommunityRules.nicknameProblem("nick!name"))       // bad characters
        assertNotNull(CommunityRules.nicknameProblem("123"))             // no letter
    }

    @Test fun bodiesRejectLinksAndOverrun() {
        assertNull(CommunityRules.bodyProblem("We had our scan this week"))
        assertNotNull(CommunityRules.bodyProblem("check https://spam.example"))
        assertNotNull(CommunityRules.bodyProblem("visit www.example.com"))
        assertNotNull(CommunityRules.bodyProblem("HTTP link"))
        assertNotNull(CommunityRules.bodyProblem("   "))
        assertNotNull(CommunityRules.bodyProblem("x".repeat(501)))
    }

    @Test fun noticeIsHonestAboutModerationLimits() {
        assertTrue(CommunityText.notice.contains("not watched around the clock"))
        assertTrue(CommunityText.notice.contains("not for emergencies"))
        assertTrue(CommunityText.notice.contains("Danger signs"))
    }

    @Test fun guidelinesCoverNicknamePrivacyNoRecordsAndReview() {
        val all = CommunityText.guidelines.joinToString("\n")
        assertTrue(all.contains("nickname"))
        assertTrue(all.contains("never share your full name, phone number"))
        assertTrue(all.contains("Nothing from your health records is shown"))
        assertTrue(all.contains("reviewed by the CareTrail team"))
        assertTrue(all.contains("delete your own messages"))
        assertEquals(5, CommunityRules.reportReasons.size)
        assertEquals(CommunityRules.reportReasons.toSet(), CommunityText.reasonLabels.keys)
    }

    @Test fun serverPayloadParsesWithBlockedMasking() {
        val raw = """{"messages":[{"id":"m1","author_id":"p1","nickname":"Blocked member","body":"","created_at":"2026-11-26T10:00:00","own":false,"author_blocked":true}],"has_more":false,"oldest_id":"m1"}"""
        val list = AppJson.decodeFromString(CommunityMsgList.serializer(), raw)
        assertEquals(1, list.messages.size)
        assertTrue(list.messages[0].author_blocked)
        assertEquals("", list.messages[0].body)
        assertEquals("m1", list.oldest_id)
    }

    @Test fun messageViewKeepsAuthorIdForBlockActions() {
        val msg = CommunityMsg(id = "m2", author_id = "p9", nickname = "Meera", body = "hi", created_at = "2026-11-26T10:05:00")
        assertEquals("p9", msg.author_id) // block/report target the author, never the nickname
    }
}
