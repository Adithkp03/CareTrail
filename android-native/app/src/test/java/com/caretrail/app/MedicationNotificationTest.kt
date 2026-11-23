package com.caretrail.app

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.caretrail.app.data.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.captureRoboImage
import android.app.Activity
import org.robolectric.Robolectric
import android.view.View
import android.widget.FrameLayout

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h820dp-port")
class MedicationNotificationTest {
    private val due = LocalDateTime.parse("2026-11-23T09:00")
    private val med = MedicationReminder("one", "Synthetic medicine", start = "2026-11-23", times = listOf("09:00"))
    @Test fun namesOnlyScheduledEnabledMedicines() {
        val other = med.copy(id = "two", name = "Later medicine", times = listOf("20:00"))
        val paused = med.copy(id = "three", name = "Paused medicine", enabled = false)
        val text = MedicationNotificationText.forDue(DailyReminderPlan(medications = listOf(med, other, paused)), due)
        assertTrue(text.contains("Synthetic medicine"))
        assertFalse(text.contains("Later medicine"))
        assertFalse(text.contains("Paused medicine"))
    }
    @Test fun refillAndSimultaneousMedicinesAreIdentified() {
        val refill = med.copy(id = "two", name = "Refill medicine", times = listOf("20:00"), refillDate = "2026-11-23")
        val text = MedicationNotificationText.forDue(DailyReminderPlan(medications = listOf(med, refill)), due)
        assertTrue(text.contains("Synthetic medicine (scheduled reminder)"))
        assertTrue(text.contains("Refill medicine (refill reminder)"))
    }
    @Test fun privateNotificationHasGenericPublicVersion() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val open = PendingIntent.getActivity(app, 888, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val text = MedicationNotificationText.forDue(DailyReminderPlan(medications = listOf(med)), due)
        val n = DailyReminderScheduler.medicationNotification(app, text, open)
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(med.name))
        assertNotNull(n.publicVersion)
        assertFalse(n.publicVersion.extras.toString().contains(med.name))
        assertEquals("Unlock your phone to view your reminder.", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
    @Test fun renderPrivateAndPublicNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val open = PendingIntent.getActivity(app, 889, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = DailyReminderScheduler.medicationNotification(app, "Synthetic medicine (scheduled reminder). Open CareTrail to review.", open)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        listOf("private" to n, "public" to n.publicVersion).forEach { (name, notification) ->
            val remote = Notification.Builder.recoverBuilder(app, notification).createContentView()
            val parent = FrameLayout(activity)
            val view = remote.apply(activity, parent)
            parent.addView(view)
            parent.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST))
            parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
            parent.captureRoboImage("build/outputs/roborazzi/medication_notification_$name.png")
        }
    }
}
