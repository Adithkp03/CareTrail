package com.caretrail.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StructuredBackupTest {
    private fun store(): Store {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("backup-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return Store(prefs)
    }
    @Test fun allowlistNeverReadsGenericCachesOrSession() {
        val s = store(); s.token = "synthetic-token"; s.putCache("journey", "private-cache")
        assertNull(StructuredBackup.local(s, "j1", "birth"))
        assertThrows(IllegalArgumentException::class.java) { StructuredBackup.local(s, "j1", "token") }
        assertFalse(StructuredBackup.modules.containsKey("vault"))
    }
    @Test fun restoreKeepsOtherJourneysAndExistingDifferentRecords() {
        val s = store()
        val r = BackupRecord("birth-draft", 1, data = buildJsonObject { put("weight", "2.") })
        StructuredBackup.restore(s, "j1", r)
        assertEquals("2.", s.birthDraft("j1")!!.weight)
        assertNull(s.birthDraft("j2"))
        StructuredBackup.restore(s, "j1", r)
        assertThrows(IllegalStateException::class.java) {
            StructuredBackup.restore(s, "j1", r.copy(data = buildJsonObject { put("weight", "3.") }))
        }
        assertEquals("2.", s.birthDraft("j1")!!.weight)
    }
    @Test fun deletedAndUnknownSchemasCannotRecreateRecords() {
        val s = store(); val r = BackupRecord("birth", 2, deleted = true)
        assertThrows(IllegalArgumentException::class.java) { StructuredBackup.restore(s, "j", r) }
        assertThrows(IllegalArgumentException::class.java) { StructuredBackup.restore(s, "j", r.copy(schema_version = 2, deleted = false, data = JsonObject(emptyMap()))) }
        assertNull(StructuredBackup.local(s, "j", "birth"))
    }
    @Test fun serverConflictKeepsPhoneCopyAndUsesExpectedRevision() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val s = store(); s.saveBirthDraft("j", BirthDraft(weight = "2."))
            val raw = requireNotNull(StructuredBackup.local(s, "j", "birth-draft"))
            server.enqueue(MockResponse().setResponseCode(409).setBody("{\"detail\":\"Backup changed\"}"))
            val api = Api(server.url("/").toString(), { "synthetic" })
            try { StructuredBackup.upload(api, "j", "birth-draft", 4, raw, "synthetic-operation-001"); fail("Expected conflict") }
            catch (e: ApiException) { assertEquals(409, e.code) }
            assertEquals("2.", s.birthDraft("j")!!.weight)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("PUT", request.method)
            assertEquals("/journeys/j/local-backup/birth-draft", request.path)
            val json = AppJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(4, json["expected_revision"]!!.jsonPrimitive.int)
            assertTrue(json["consent"]!!.jsonPrimitive.boolean)
        } finally { server.shutdown() }
    }
    @Test fun registrationBackupExcludesLocalImagePaths() {
        val s = store()
        s.saveRegistration("j", RegistrationData(facility = "Synthetic PHC", mcp_images = listOf("/private/image.jpg")))
        val raw = StructuredBackup.local(s, "j", "registration")!!
        assertFalse(raw.contains("mcp_images"))
        assertFalse(raw.contains("/private/image.jpg"))
        assertTrue(raw.contains("Synthetic PHC"))
        assertEquals(1, s.registration("j").mcp_images.size)
    }
}
