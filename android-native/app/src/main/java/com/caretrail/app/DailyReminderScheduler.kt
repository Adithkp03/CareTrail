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
        schedule(ctx)
    }

    fun deactivate(ctx: Context) {
        prefs(ctx).edit().remove("active").apply()
        alarms(ctx).cancel(pending(ctx, "", 0))
        NotificationManagerCompat.from(ctx).cancel(2001)
    }

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
    private fun pending(ctx: Context, owner: String, due: Long) = PendingIntent.getBroadcast(
        ctx, 2001, Intent(ctx, DailyReminderReceiver::class.java).setAction("com.caretrail.DAILY")
            .putExtra("owner", owner).putExtra("due", due),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context) {
        val owner = prefs(ctx).getString("active", null) ?: return
        alarms(ctx).cancel(pending(ctx, owner, 0))
        if (Store(ctx).token == null) return
        val plan = plan(ctx, owner)
        val now = LocalDateTime.now()
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
        if (Store(ctx).token == null) { deactivate(ctx); return }
        if (intent.action == "com.caretrail.DAILY" && intent.getStringExtra("owner") == owner) {
            val due = intent.getLongExtra("due", 0)
            // Do not deliver stale reminders after a long device shutdown or clock jump.
            if (System.currentTimeMillis() - due in 0..3_600_000 && Notifier.granted(ctx)) {
                val manager = ctx.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("medications", "Medication reminders", NotificationManager.IMPORTANCE_HIGH))
                val open = PendingIntent.getActivity(ctx, 2001,
                    Intent(ctx, MainActivity::class.java).putExtra("daily_reminders", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(ctx, "medications")
                    .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Medication reminder")
                    .setContentText("Open CareTrail to check your medication or refill schedule.")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()
                try { NotificationManagerCompat.from(ctx).notify(2001, notification) } catch (_: SecurityException) { }
            }
        }
        schedule(ctx)
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyReminderScheduler.receive(context, intent)
    }
}
