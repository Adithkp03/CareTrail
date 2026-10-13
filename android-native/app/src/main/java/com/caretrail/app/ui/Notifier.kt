package com.caretrail.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.caretrail.app.data.Inbox

/** Local (not push) notifications for new reminders while the app refreshes. Generic text only, no health details. */
object Notifier {
    private const val CHANNEL = "care_reminders"
    private const val PREF = "notify_prefs"

    fun enabled(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    fun granted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun maybeNotify(ctx: Context, inbox: Inbox) {
        if (!enabled(ctx) || !granted(ctx)) return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()) ?: emptySet()
        val unseen = inbox.items.filter { !it.read && !it.dismissed && it.id !in seen }
        if (unseen.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Care reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("CareTrail reminder")
            .setContentText("Open CareTrail to review your care reminders.")
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(1001, n) } catch (_: SecurityException) { return }
        prefs.edit().putStringSet("seen", seen + unseen.map { it.id }).apply()
    }
}

@Composable
fun NotifyToggle(l: L) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Notifier.enabled(ctx) && Notifier.granted(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> on = ok; Notifier.setEnabled(ctx, ok) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "Notifications on" else "Notifications off", style = MaterialTheme.typography.labelLarge)
        Switch(checked = on, onCheckedChange = { want ->
            if (!want) { on = false; Notifier.setEnabled(ctx, false) }
            else if (Notifier.granted(ctx)) { on = true; Notifier.setEnabled(ctx, true) }
            else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, colors = SwitchDefaults.colors(checkedTrackColor = CT.Brand))
    }
}
