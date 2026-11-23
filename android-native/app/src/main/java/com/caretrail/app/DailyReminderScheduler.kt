package com.caretrail.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.caretrail.app.data.*
import com.caretrail.app.ui.Notifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DailyReminderScheduler {
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("daily_reminders", Context.MODE_PRIVATE)
    fun plan(ctx: Context, owner: String): DailyReminderPlan = prefs(ctx).getString(owner, null)?.let {
        json.decodeFromString<DailyReminderPlan>(StoreCrypto.decrypt(it))
    } ?: DailyReminderPlan()

    fun save(ctx: Context, owner: String, plan: DailyReminderPlan) {
        check(prefs(ctx).edit().putString(owner, StoreCrypto.encrypt(json.encodeToString(plan))).commit())
        activate(ctx, owner)
    }

    fun activate(ctx: Context, owner: String) {
        prefs(ctx).edit().putString("active", owner).apply()
        prefs(ctx).getString("last_patient", null)?.let { prefs(ctx).edit().putString("owner_patient", it).apply() }
        schedule(ctx)
    }

    // Explicit sign-in by a different patient stops the previous patient's reminders on this
    // device. Session expiry is not a sign-in, so alarms survive it; logout() deactivates too.
    fun onExplicitSignIn(ctx: Context, patientId: String) {
        if (patientId.isBlank()) return
        val ownerPatient = prefs(ctx).getString("owner_patient", null)
        if (ownerPatient != null && ownerPatient != patientId) deactivate(ctx)
        prefs(ctx).edit().putString("last_patient", patientId).apply()
    }

    fun setupDone(ctx: Context): Boolean {
        val owner = prefs(ctx).getString("active", null) ?: return false
        return plan(ctx, owner).setupDone
    }

    fun deactivate(ctx: Context) {
        prefs(ctx).edit().remove("active").remove("owner_patient").apply()
        alarms(ctx).cancel(pending(ctx, "", 0))
        alarms(ctx).cancel(hydrationPending(ctx, "", 0))
        NotificationManagerCompat.from(ctx).cancel(2001)
        NotificationManagerCompat.from(ctx).cancel(2002)
    }

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
    private fun pending(ctx: Context, owner: String, due: Long) = PendingIntent.getBroadcast(
        ctx, 2001, Intent(ctx, DailyReminderReceiver::class.java).setAction("com.caretrail.DAILY")
            .putExtra("owner", owner).putExtra("due", due),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context, only: String? = null) {
        val owner = prefs(ctx).getString("active", null) ?: return
        if (only != "hydration") alarms(ctx).cancel(pending(ctx, owner, 0))
        if (only != "medication") alarms(ctx).cancel(hydrationPending(ctx, owner, 0))
        val plan = plan(ctx, owner)
        val now = LocalDateTime.now()
        if (only != "medication") ReminderTimes.nextHydration(plan.hydration, now)?.let { next ->
            val due = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, hydrationPending(ctx, owner, due))
        }
        if (only == "hydration") return
        val candidates = plan.medications.flatMap { m ->
            listOfNotNull(ReminderTimes.next(m, now), m.refillDate.takeIf { m.enabled && it.isNotBlank() }
                ?.let { LocalDate.parse(it).atTime(9, 0) }?.takeIf { it > now })
        }
        val next = candidates.minOrNull() ?: return
        val due = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending(ctx, owner, due))
    }

    fun receive(ctx: Context, intent: Intent) {
        val owner = prefs(ctx).getString("active", null) ?: return
        if (intent.action == "com.caretrail.HYDRATION" && intent.getStringExtra("owner") == owner) {
            val h = plan(ctx, owner).hydration
            val time = java.time.LocalTime.now()
            val age = System.currentTimeMillis() - intent.getLongExtra("due", 0)
            if (h.enabled && age in 0..3_600_000 && time >= java.time.LocalTime.parse(h.start) && time < java.time.LocalTime.parse(h.end) && Notifier.granted(ctx)) {
                val manager = ctx.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("hydration", "Hydration reminders", NotificationManager.IMPORTANCE_DEFAULT))
                val open = PendingIntent.getActivity(ctx, 2002, Intent(ctx, MainActivity::class.java).putExtra("daily_reminders", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(ctx, "hydration")
                    .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("A gentle hydration reminder")
                    .setContentText("Take a moment for some water, following your care team's guidance.")
                    .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                try { NotificationManagerCompat.from(ctx).notify(2002, notification) } catch (_: SecurityException) { }
            }
        }
        if (intent.action == "com.caretrail.DAILY" && intent.getStringExtra("owner") == owner) {
            val due = intent.getLongExtra("due", 0)
            // Do not deliver stale reminders after a long device shutdown or clock jump.
            if (System.currentTimeMillis() - due in 0..3_600_000 && Notifier.granted(ctx)) {
                val manager = ctx.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("medications", "Medication reminders", NotificationManager.IMPORTANCE_HIGH))
                val open = PendingIntent.getActivity(ctx, 2001,
                    Intent(ctx, MainActivity::class.java).putExtra("daily_reminders", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val scheduledAt = java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val details = MedicationNotificationText.forDue(plan(ctx, owner), scheduledAt)
                val notification = medicationNotification(ctx, details, open)
                try { NotificationManagerCompat.from(ctx).notify(2001, notification) } catch (_: SecurityException) { }
            }
        }
        schedule(ctx, when (intent.action) {
            "com.caretrail.HYDRATION" -> "hydration"
            "com.caretrail.DAILY" -> "medication"
            else -> null
        })
    }

    internal fun medicationNotification(ctx: Context, details: String, open: PendingIntent): android.app.Notification {
        val hidden = NotificationCompat.Builder(ctx, "medications")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Medication reminder")
            .setContentText("Unlock your phone to view your reminder.")
            .setContentIntent(open).setAutoCancel(true).build()
        return NotificationCompat.Builder(ctx, "medications")
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Medication reminder")
            .setContentText(details).setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPublicVersion(hidden).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open).setAutoCancel(true).build()
    }

    private fun hydrationPending(ctx: Context, owner: String, due: Long) = PendingIntent.getBroadcast(
        ctx, 2002, Intent(ctx, DailyReminderReceiver::class.java).setAction("com.caretrail.HYDRATION")
            .putExtra("owner", owner).putExtra("due", due), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyReminderScheduler.receive(context, intent)
    }
}
