package com.caretrail.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.caretrail.app.AppViewModel
import com.caretrail.app.DailyReminderScheduler
import com.caretrail.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Composable
fun DailyRemindersScreen(vm: AppViewModel, onboarding: Boolean, done: () -> Unit) {
    val ctx = LocalContext.current
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = done) { j, _, _ ->
        var plan by remember(j.journey_id) { mutableStateOf(DailyReminderScheduler.plan(ctx, j.journey_id)) }
        var editing by remember { mutableStateOf<MedicationReminder?>(null) }
        var error by remember { mutableStateOf("") }
        LaunchedEffect(j.journey_id) {
            DailyReminderScheduler.activate(ctx, j.journey_id)
            if (onboarding && plan.setupDone) done()
        }
        fun save(next: DailyReminderPlan) {
            try { DailyReminderScheduler.save(ctx, j.journey_id, next); plan = next; error = "" }
            catch (_: Exception) { error = "Could not save reminders. Please try again." }
        }
        if (editing != null) {
            MedicationEditor(editing!!, error, cancel = { editing = null; error = "" }) { medication ->
                val next = plan.copy(medications = plan.medications.filterNot { it.id == medication.id } + medication)
                save(next)
                if (error.isEmpty()) editing = null
            }
        } else Page(title = l.tr(if (onboarding) "Set up your daily reminders" else "Daily reminders"), back = if (onboarding) null else done) {
            Muted(l.tr("Add the medications you currently take. You can skip this and add them later."))
            Spacer(gap(12))
            plan.medications.forEach { m ->
                CTCard {
                    SectionTitle(m.name)
                    Muted("${m.dose} ${m.form} · ${m.times.joinToString()} · ${if (m.enabled) "On" else "Paused"}")
                    TextButton(onClick = { editing = m }) { Text(l.tr("Edit schedule")) }
                    Row {
                        TextButton(onClick = { save(plan.copy(medications = plan.medications.map { if (it.id == m.id) it.copy(enabled = !it.enabled) else it })) }) { Text(l.tr(if (m.enabled) "Pause" else "Resume")) }
                        TextButton(onClick = { save(plan.copy(medications = plan.medications.filterNot { it.id == m.id })) }) { Text(l.tr("Delete")) }
                    }
                }
                Spacer(gap(8))
            }
            PrimaryButton(l.tr(if (plan.medications.isEmpty()) "Add medication" else "Add another medication")) {
                editing = MedicationReminder(id = UUID.randomUUID().toString(), name = "")
            }
            Spacer(gap(12))
            TodayDoses(plan) { rec ->
                save(plan.copy(doses = (plan.doses.filterNot { it.medicationId == rec.medicationId && it.scheduledAt == rec.scheduledAt } + rec).takeLast(500)))
            }
            Spacer(gap(12))
            HydrationSettings(plan.hydration) { save(plan.copy(hydration = it)) }
            Spacer(gap(12))
            DailyNotificationPermission()
            if (error.isNotEmpty()) Note(error)
            Spacer(gap(12))
            PrimaryButton(l.tr(if (plan.medications.isEmpty() && !plan.hydration.enabled) "Skip for now" else "Continue")) {
                save(plan.copy(setupDone = true))
                if (error.isEmpty()) done()
            }
        }
    }
}

@Composable
private fun HydrationSettings(h: HydrationReminder, save: (HydrationReminder) -> Unit) {
    CTCard {
        SectionTitle("Hydration reminders")
        Row {
            Text("Gentle nudges for water", modifier = Modifier.weight(1f))
            Switch(checked = h.enabled, onCheckedChange = { save(h.copy(enabled = it)) })
        }
        if (h.enabled) {
            var start by remember(h.start) { mutableStateOf(h.start) }
            var end by remember(h.end) { mutableStateOf(h.end) }
            val valid = LocalTime.parse(end) > LocalTime.parse(start)
            TimeChoice("First reminder", start) { start = it }
            TimeChoice("Stop reminders", end) { end = it }
            if (!valid) Note("Choose a stop time later than the first reminder.")
            Text("Remind me every ${h.intervalMinutes} minutes")
            listOf(30, 60, 90, 120, 180).forEach { minutes ->
                TextButton(onClick = { save(h.copy(intervalMinutes = minutes)) }) { Text(if (minutes == h.intervalMinutes) "$minutes minutes ✓" else "$minutes minutes") }
            }
            if (start != h.start || end != h.end) PrimaryButton("Save hydration times", enabled = valid) { save(h.copy(start = start, end = end)) }
            Muted("Reminders pause outside this time window. Follow any fluid limits your care team has given you.")
        }
    }
}

@Composable
fun DailyNotificationPermission() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Notifier.granted(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Muted("Android may delay reminders to save battery. Tap a notification to review your schedule.")
    if (!granted) TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }) {
        Text("Notification settings")
    }
}

@Composable
private fun MedicationEditor(initial: MedicationReminder, error: String, cancel: () -> Unit, save: (MedicationReminder) -> Unit) {
    var step by rememberSaveable(initial.id) { mutableIntStateOf(0) }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var form by rememberSaveable(initial.id) { mutableStateOf(initial.form) }
    var dose by rememberSaveable(initial.id) { mutableStateOf(initial.dose) }
    var times by rememberSaveable(initial.id) { mutableStateOf(initial.times) }
    var start by rememberSaveable(initial.id) { mutableStateOf(initial.start) }
    var end by rememberSaveable(initial.id) { mutableStateOf(initial.end) }
    var days by rememberSaveable(initial.id) { mutableStateOf(initial.weekdays) }
    var instructions by rememberSaveable(initial.id) { mutableStateOf(initial.instructions) }
    var refill by rememberSaveable(initial.id) { mutableStateOf(initial.refillDate) }
    val validDates = runCatching { end.isBlank() || LocalDate.parse(end) >= LocalDate.parse(start) }.getOrDefault(false)
    Page(title = listOf("Your medication", "Medication schedule", "Instructions and refill")[step], back = { if (step == 0) cancel() else step-- }) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        Spacer(gap(12))
        Muted("Step ${step + 1} of 3")
        when (step) {
            0 -> {
                CTField(name, { name = it.take(80) }, "Medication name")
                Text("Medication form")
                listOf("Tablet", "Capsule", "Liquid", "Other").forEach { option ->
                    Row { RadioButton(form == option, { form = option }); TextButton(onClick = { form = option }) { Text(option) } }
                }
            }
            1 -> {
                DateChoice("Start date", start) { start = it }
                DateChoice("End date (optional)", end) { end = it }
                if (end.isNotEmpty()) TextButton(onClick = { end = "" }) { Text("No end date") }
                Text("Repeat on")
                Row {
                    (1..7).forEach { day ->
                        FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(listOf("M", "T", "W", "T", "F", "S", "S")[day - 1]) }, modifier = Modifier.weight(1f))
                    }
                }
                CTField(dose, { dose = it.take(30) }, "Dose per reminder (e.g. 1 tablet or 5 mL)", keyboard = KeyboardType.Text)
                times.forEachIndexed { index, time ->
                    Row {
                        TimeChoice("Dose ${index + 1}", time) { value -> times = times.mapIndexed { i, old -> if (i == index) value else old } }
                        if (times.size > 1) TextButton(onClick = { times = times.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
                    }
                }
                if (times.size < 8) TextButton(onClick = { times = times + LocalTime.parse(times.last()).plusHours(4).toString() }) { Text("Add daily time") }
                if (!validDates) Note("End date must be on or after the start date.")
                if (times.distinct().size != times.size) Note("Choose a different time for each dose.")
            }
            2 -> {
                CTField(instructions, { instructions = it.take(500) }, "Instructions from your prescription (optional)", singleLine = false)
                DateChoice("Refill reminder at 09:00 (optional)", refill) { refill = it }
                if (refill.isNotBlank()) TextButton(onClick = { refill = "" }) { Text("Remove refill reminder") }
                Muted("Use the dose and instructions given by your clinician.")
            }
        }
        Spacer(gap(12))
        if (error.isNotEmpty()) Note(error)
        PrimaryButton(if (step == 2) "Save medication reminder" else "Continue",
            enabled = name.isNotBlank() && (step != 1 || (dose.isNotBlank() && validDates && days.isNotEmpty() && times.distinct().size == times.size))) {
            if (step < 2) step++ else save(initial.copy(name = name.trim(), form = form, dose = dose.trim(), times = times.sorted(), start = start, end = end, weekdays = days.sorted(), instructions = instructions.trim(), refillDate = refill))
        }
    }
}

@Composable
private fun TodayDoses(plan: DailyReminderPlan, mark: (DoseRecord) -> Unit) {
    val today = plan.medications.flatMap { m ->
        ReminderTimes.dosesOn(m, LocalDate.now()).map { t ->
            Triple(m, t, plan.doses.lastOrNull { it.medicationId == m.id && it.scheduledAt == t.toString() })
        }
    }.sortedBy { it.second }
    if (today.isEmpty()) return
    CTCard {
        SectionTitle("Today's doses")
        today.forEach { (m, t, rec) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.name} · ${t.toLocalTime()}")
                    if (rec != null) Muted(if (rec.status == "taken") "Marked taken" else "Marked skipped")
                }
                if (rec == null) {
                    TextButton(onClick = { mark(DoseRecord(m.id, m.name, t.toString(), "taken", LocalDateTime.now().toString())) }) { Text("Taken") }
                    TextButton(onClick = { mark(DoseRecord(m.id, m.name, t.toString(), "skipped", LocalDateTime.now().toString())) }) { Text("Skip") }
                }
            }
        }
        Muted("This is your own record, kept on this phone (the most recent 500 marks). For any dose concerns, follow your care team's guidance.")
    }
}

@Composable
fun DateChoice(label: String, value: String, change: (String) -> Unit) {
    val ctx = LocalContext.current
    TextButton(onClick = {
        val date = value.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
        DatePickerDialog(ctx, { _, year, month, day -> change(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }) { Text("$label · ${value.ifBlank { "Not set" }}") }
}

@Composable
fun TimeChoice(label: String, value: String, change: (String) -> Unit) {
    val ctx = LocalContext.current
    TextButton(onClick = {
        val time = LocalTime.parse(value)
        TimePickerDialog(ctx, { _, hour, minute -> change(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)) }, time.hour, time.minute, true).show()
    }) { Text("$label · $value") }
}

