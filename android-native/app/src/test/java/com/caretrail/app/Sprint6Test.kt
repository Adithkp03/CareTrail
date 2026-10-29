package com.caretrail.app

import com.caretrail.app.data.Api
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.ChatHistory
import com.caretrail.app.data.ChatMsg
import com.caretrail.app.data.ChatResult
import com.caretrail.app.data.DoctorQuestionList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint6Test {

    @Test fun chatHistoryFixtureParses() {
        val json = """{"items":[
            {"id":"a","role":"user","text":"When is my next appointment?","created_at":"2026-10-29T04:00:00+00:00"},
            {"id":"b","role":"assistant","text":"Your next booked appointment is Anomaly scan on 2026-11-05.","source":"app:appointments","urgent":false,"suggest_doctor":false,"created_at":"2026-10-29T04:00:01+00:00"}
        ]}"""
        val h = AppJson.decodeFromString(ChatHistory.serializer(), json)
        assertEquals(2, h.items.size)
        assertEquals("assistant", h.items[1].role)
        assertFalse(h.items[1].urgent)
        assertFalse(h.items[1].suggest_doctor)
    }

    @Test fun chatResultDefaultsAreSafe() {
        val r = AppJson.decodeFromString(ChatResult.serializer(), """{"message_id":"m1","answer":"ask your doctor","source":"defer-advice"}""")
        assertEquals("defer-advice", r.source)
        assertFalse(r.urgent)
        assertEquals("en", r.language)
    }

    @Test fun doctorQuestionListParses() {
        val json = """{"items":[{"id":"q1","journey_id":"j1","question":"Can we move my scan?","created_at":"2026-10-29T04:00:00+00:00","answer":null,"answered_at":null,"answerer_name":null,"answered":false}],
            "linked_doctors":[{"id":"c1","name":"Dr Test"}]}"""
        val list = AppJson.decodeFromString(DoctorQuestionList.serializer(), json)
        assertEquals(1, list.items.size)
        assertFalse(list.items[0].answered)
        assertEquals("Dr Test", list.linked_doctors[0].name)
        assertEquals("c1", list.linked_doctors[0].id)
    }

    @Test fun assistantChatPostsAndParses() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"message_id":"m1","answer":"Your next booked appointment is Anomaly scan on 2026-11-05.","source":"app:appointments","urgent":false,"suggest_doctor":false,"language":"en"}"""))
        server.start()
        val api = Api(server.url("/").toString().trimEnd('/'), { "tok" })
        val r = api.assistantChat("When is my next appointment?", "en")
        assertEquals("app:appointments", r.source)
        val sent = server.takeRequest()
        assertEquals("Bearer tok", sent.getHeader("Authorization"))
        val body = AppJson.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals("When is my next appointment?", body["message"]!!.jsonPrimitive.content)
        assertEquals("en", body["lang"]!!.jsonPrimitive.content)
        server.shutdown()
    }

    @Test fun askDoctorPostsQuestion() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"id":"q1","journey_id":"j1","clinician_id":"c1","recipient_name":"Dr Test","question":"Is Friday ok?","created_at":"2026-10-29T04:00:00+00:00","answer":null,"answered_at":null,"answerer_name":null,"answered":false}"""))
        server.start()
        val api = Api(server.url("/").toString().trimEnd('/'), { "tok" })
        val q = api.askDoctor("j1", "c1", "Is Friday ok?")
        assertEquals("q1", q.id)
        assertFalse(q.answered)
        val sent = server.takeRequest()
        assertEquals("/journeys/j1/doctor-questions", sent.path)
        val body = AppJson.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals("c1", body["clinician_id"]!!.jsonPrimitive.content)
        server.shutdown()
    }
}
