package com.caretrail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.Inbox
import com.caretrail.app.data.Instruction
import com.caretrail.app.data.Logic
import kotlinx.coroutines.launch

@Composable
fun DoctorInstructions(vm: AppViewModel, journeyId: String, l: L) {
    var items by remember { mutableStateOf<List<Instruction>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(journeyId) {
        try { items = vm.api.instructions(journeyId).items; loaded = true }
        catch (e: Exception) { error = "Doctor instructions could not be loaded. Connect and try again." }
    }
    val unread = items.count { it.acknowledged_at == null }
    CTCard {
        SectionTitle(l.tr("Instructions from your doctor") + if (unread > 0) " · $unread ${l.tr("unread")}" else "")
        Muted(l.tr("These notes were written by a doctor with access to your journey, not by AI. Acknowledging means you have read the note, not that the care step is complete."))
        if (!loaded && error.isEmpty()) Muted(l.tr("Loading doctor instructions..."))
        if (loaded && items.isEmpty()) Muted(l.tr("No doctor instructions recorded yet."))
        if (error.isNotEmpty()) Text(l.tr(error), color = CT.Amber900, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        items.forEach { i ->
            Spacer(gap(10))
            Column(
                Modifier.fillMaxWidth().background(if (i.acknowledged_at != null) Color_slate else CT.Amber50, RoundedCornerShape(16.dp)).padding(12.dp)
            ) {
                Text(i.doctor_name, style = MaterialTheme.typography.titleSmall)
                Muted(i.created_at.take(16).replace('T', ' '))
                Spacer(gap(6))
                Text(i.note, style = MaterialTheme.typography.bodyMedium)
                Spacer(gap(8))
                if (i.acknowledged_at != null) {
                    Text("${l.tr("You acknowledged this note")} · ${i.acknowledged_at.take(16).replace('T', ' ')}", color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                } else {
                    PrimaryButton(l.tr("Acknowledge"), enabled = saving.isEmpty()) {
                        saving = i.id; error = ""
                        scope.launch {
                            try { vm.api.acknowledge(i.id); items = vm.api.instructions(journeyId).items }
                            catch (e: Exception) { error = "Could not save your acknowledgment. Try again." }
                            saving = ""
                        }
                    }
                }
            }
        }
    }
}

private val Color_slate = androidx.compose.ui.graphics.Color(0xFFF8FAFC)

@Composable
fun RemindersScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, offline ->
        var inbox by remember { mutableStateOf<Inbox?>(null) }
        var error by remember { mutableStateOf("") }
        suspend fun refresh() {
            try { inbox = vm.api.reminders(j.journey_id); error = ""; Notifier.maybeNotify(ctx, inbox!!) }
            catch (e: Exception) { error = "Unable to refresh reminders. Connect to the internet and try again; this is not a current safety check." }
        }
        LaunchedEffect(j.journey_id, offline) { if (!offline) refresh() }
        val att = Logic.attention(j)
        val attention = att.overdueTitles.map { "${l.t("overdueMilestone")}: ${l.tr(it)}" } +
            att.unresolved.map { "${l.t("reviewPending")}: ${l.tr(it.label)} (${Logic.fmtValue(it.value)} ${it.unit})" }
        Page(title = l.tr("Care reminders"), back = nav.home) {
            Spacer(gap(8))
            CTCard {
                SectionTitle(l.t("attentionNeeded") + if (attention.isNotEmpty()) " · ${attention.size}" else "")
                Spacer(gap(6))
                if (attention.isEmpty()) Muted(l.t("noOpenItems"))
                attention.forEach { Text("⚠ $it", color = CT.Amber900, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Spacer(gap(12))
            if (offline) Note(l.tr("Reminders are unavailable offline. Connect to refresh them; cached information is not a current safety check."))
            else CTCard {
                val ib = inbox
                SectionTitle(l.tr("Care reminders") + if ((ib?.unread ?: 0) > 0) " · ${ib!!.unread}" else "")
                Muted(l.tr("Updated while the app is open. Not emergency monitoring. For warning signs, contact your doctor immediately."))
                Spacer(gap(8))
                NotifyToggle(l)
                if (error.isNotEmpty()) Text(l.tr(error), color = CT.Red700, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (ib == null && error.isEmpty()) Muted(l.tr("Loading reminders..."))
                if (ib != null && ib.items.none { !it.dismissed }) Muted(l.tr("No active care reminders."))
                ib?.items?.filter { !it.dismissed }?.forEach { r ->
                    Spacer(gap(10))
                    Column(Modifier.fillMaxWidth().background(if (r.read) androidx.compose.ui.graphics.Color.White else CT.Cream, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text(l.tr(r.title), color = CT.Brand, style = MaterialTheme.typography.titleSmall)
                        r.scheduled_date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(l.tr(r.message), style = MaterialTheme.typography.bodySmall)
                        Spacer(gap(6))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!r.read) Text(l.tr("Mark read"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.androidClick { scope.launch { try { vm.api.patchReminder(j.journey_id, r.id, false); refresh() } catch (_: Exception) { error = "Could not save your change. Refresh and try again." } } })
                            Text(l.tr("Dismiss"), color = CT.Muted, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.androidClick { scope.launch { try { vm.api.patchReminder(j.journey_id, r.id, true); refresh() } catch (_: Exception) { error = "Could not save your change. Refresh and try again." } } })
                        }
                    }
                }
                Spacer(gap(8))
                Muted(l.tr("Dismissing a reminder does not complete a visit or clear a result. No SMS, email or closed-app push notifications."))
            }
        }
    }
}

fun Modifier.androidClick(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick).padding(vertical = 4.dp)

@Composable
fun MovementsScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        Page(title = l.tr("Baby movements"), back = nav.home) {
            Spacer(gap(8))
            if (j.gestational_age.weeks < 14) {
                CTCard { Text(l.tr("This personal movement tracker is available from the second trimester. It is not a medical test or target count.")) }
            } else {
                val day = Logic.today()
                var count by remember(j.journey_id, day) { mutableIntStateOf(vm.store.movements(j.journey_id, day)) }
                fun update(delta: Int) { count = maxOf(0, count + delta); vm.store.setMovements(j.journey_id, day, count) }
                CTCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareIcon("movement", 20.dp); Text(l.t("movementTracker"), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(gap(8))
                    Text(l.t("movementInstruction"), style = MaterialTheme.typography.bodySmall, color = CT.Muted)
                    Spacer(gap(12))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(onClick = { update(1) }, shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CT.Brand)) { Text("+ 1", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White) }
                        Text("$count · ${l.t("movementCount")}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(l.t("movementUndo"), color = if (count > 0) CT.Brand else CT.Muted, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.androidClick { if (count > 0) update(-1) })
                    Spacer(gap(8))
                    Note("⚠️ ${l.t("movementWarning")}")
                    Spacer(gap(6))
                    Muted(l.t("movementStorage"))
                }
            }
        }
    }
}
