package com.caretrail.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.BirthRecord
import com.caretrail.app.data.GrowthEntry
import com.caretrail.app.data.Logic
import com.caretrail.app.data.VaccineLog
import com.caretrail.app.data.VaccineSchedule
import com.caretrail.app.data.VaultEntry
import com.caretrail.app.data.WhoGrowth
import com.caretrail.app.data.birthRecord
import com.caretrail.app.data.dangerLogs
import com.caretrail.app.data.growthEntries
import com.caretrail.app.data.healthIntake
import com.caretrail.app.data.nowIso
import com.caretrail.app.data.registration
import com.caretrail.app.data.rhProfile
import com.caretrail.app.data.saveBirthRecord
import com.caretrail.app.data.saveGrowthEntries
import com.caretrail.app.data.saveVaccineLogs
import com.caretrail.app.data.vaccineLogs
import com.caretrail.app.data.vaultEntries
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun PickChip2(label: String, selected: Boolean, onPick: () -> Unit) {
    Text(
        label, color = if (selected) Color.White else CT.Ink, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (selected) CT.Brand else CT.BrandSoft, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Pending-sync count across the Sprint 1/2 local stores, for the vault's sync status line. */
private fun pendingSyncCount(vm: AppViewModel, journeyId: String): Int {
    var n = 0
    if (vm.store.healthIntake(journeyId).pending_sync && vm.store.healthIntake(journeyId).completed) n++
    val reg = vm.store.registration(journeyId); if (reg.pending_sync && reg.updated_at.isNotEmpty()) n++
    val rh = vm.store.rhProfile(journeyId); if (rh.pending_sync && rh.updated_at.isNotEmpty()) n++
    n += vm.store.dangerLogs(journeyId).count { it.pending_sync }
    n += vm.store.growthEntries(journeyId).count { it.pending_sync }
    n += vm.store.vaccineLogs(journeyId).values.count { it.pending_sync }
    val b = vm.store.birthRecord(journeyId); if (b.pending_sync && b.confirmed) n++
    return n
}

// ---- 1. Birth intake + postpartum transition ----------------------------------

@Composable
fun BirthEntryScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var data by remember(j.journey_id) { mutableStateOf(vm.store.birthRecord(j.journey_id)) }
        var gaW by remember(j.journey_id) { mutableStateOf(if (data.ga_weeks > 0) data.ga_weeks.toString() else "") }
        var gaD by remember(j.journey_id) { mutableStateOf(data.ga_days.toString().takeIf { data.ga_weeks > 0 } ?: "") }
        var wt by remember(j.journey_id) { mutableStateOf(if (data.weight_kg > 0) data.weight_kg.toString() else "") }
        var head by remember(j.journey_id) { mutableStateOf(if (data.head_cm > 0) data.head_cm.toString() else "") }
        var len by remember(j.journey_id) { mutableStateOf(if (data.length_cm > 0) data.length_cm.toString() else "") }
        var saved by remember { mutableStateOf(false) }
        fun valid() = data.date.isNotEmpty() && data.gender.isNotEmpty()
        Page(title = l.tr(if (data.confirmed) "Birth details" else "Record the birth"), back = nav.home) {
            Spacer(gap(8))
            if (data.confirmed) Note(l.tr("Birth recorded - CareTrail is in postpartum mode. You can correct these details any time."), tone = Tone.Green)
            else Muted(l.tr("Recording the birth switches CareTrail from pregnancy to postpartum mode: baby growth charts and the vaccination schedule turn on."))
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("About the delivery"))
                Spacer(gap(8))
                DateField(data.date, { data = data.copy(date = it); saved = false }, l.tr("Date of delivery"))
                Spacer(gap(8))
                CTField(data.time, { data = data.copy(time = it); saved = false }, l.tr("Time of birth (e.g. 14:35)"))
                Spacer(gap(12))
                Text(l.tr("Gestational age at delivery"), style = MaterialTheme.typography.labelMedium, color = CT.Muted)
                Spacer(gap(6))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CTField(gaW, { gaW = it.filter { c -> c.isDigit() }; saved = false }, l.tr("Weeks"), keyboard = KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { CTField(gaD, { gaD = it.filter { c -> c.isDigit() }; saved = false }, l.tr("+ Days"), keyboard = KeyboardType.Number) }
                }
                Spacer(gap(12))
                Text(l.tr("Baby"), style = MaterialTheme.typography.labelMedium, color = CT.Muted)
                Spacer(gap(6))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickChip2(l.tr("Boy"), data.gender == "boy") { data = data.copy(gender = "boy"); saved = false }
                    PickChip2(l.tr("Girl"), data.gender == "girl") { data = data.copy(gender = "girl"); saved = false }
                }
                Spacer(gap(12))
                CTField(wt, { wt = it; saved = false }, l.tr("Birth weight (kg)"), keyboard = KeyboardType.Decimal)
                Spacer(gap(8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CTField(head, { head = it; saved = false }, l.tr("Head (cm)"), keyboard = KeyboardType.Decimal) }
                    Box(Modifier.weight(1f)) { CTField(len, { len = it; saved = false }, l.tr("Length (cm)"), keyboard = KeyboardType.Decimal) }
                }
            }
            Spacer(gap(16))
            PrimaryButton(l.tr(if (data.confirmed) "Update birth details" else "Confirm birth"), enabled = valid()) {
                val rec = data.copy(
                    confirmed = true,
                    ga_weeks = gaW.toIntOrNull() ?: 0, ga_days = gaD.toIntOrNull() ?: 0,
                    weight_kg = wt.toDoubleOrNull() ?: 0.0, head_cm = head.toDoubleOrNull() ?: 0.0, length_cm = len.toDoubleOrNull() ?: 0.0,
                )
                data = rec
                vm.store.saveBirthRecord(j.journey_id, rec)
                // Seed the growth chart with the birth measurements.
                if (rec.weight_kg > 0 || rec.length_cm > 0 || rec.head_cm > 0) {
                    val entries = vm.store.growthEntries(j.journey_id)
                    if (entries.none { it.age_months == 0.0 }) {
                        vm.store.saveGrowthEntries(j.journey_id, entries + GrowthEntry(date = rec.date, age_months = 0.0, weight_kg = rec.weight_kg, length_cm = rec.length_cm, head_cm = rec.head_cm))
                    }
                }
                saved = true
            }
            if (saved) { Spacer(gap(8)); Note(l.tr("Saved. Postpartum mode is on - see the vaccination schedule and growth chart from Home."), tone = Tone.Green) }
            Spacer(gap(8))
            if (data.confirmed) OutlineButton(l.tr("Open vaccination schedule"), onClick = nav.vaccines)
        }
    }
}

// ---- 2. WHO growth chart --------------------------------------------------------

@Composable
private fun GrowthChart(table: List<com.caretrail.app.data.WhoLms.P>, points: List<Pair<Double, Double>>, unit: String) {
    val curves = remember(table) { WhoGrowth.curves(table) }
    val zs = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
    val allY = curves.flatMap { it.second } + points.map { it.second }
    if (allY.isEmpty()) return
    val minY = allY.min() * 0.95
    val maxY = allY.max() * 1.05
    val maxX = maxOf(24.0, points.maxOfOrNull { it.first } ?: 0.0)
    Canvas(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        val padL = 36.dp.toPx(); val padB = 22.dp.toPx(); val padT = 12.dp.toPx(); val padR = 10.dp.toPx()
        fun x(age: Double) = padL + (size.width - padL - padR) * (age / maxX).toFloat()
        fun y(v: Double) = padT + (size.height - padT - padB) * (1 - (v - minY) / (maxY - minY)).toFloat()
        val labelPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#7A8797"); textSize = 9.dp.toPx() }
        for (i in 0..3) {
            val v = minY + (maxY - minY) * i / 3
            drawLine(Color(0xFFE8EDF2), Offset(padL, y(v)), Offset(size.width - padR, y(v)))
            drawContext.canvas.nativeCanvas.drawText("%.1f".format(v), 4f, y(v) + 3.dp.toPx(), labelPaint)
        }
        var m = 0
        while (m <= maxX) {
            drawContext.canvas.nativeCanvas.drawText("${m}m", x(m.toDouble()) - 7.dp.toPx(), size.height - 5.dp.toPx(), labelPaint)
            m += 6
        }
        drawContext.canvas.nativeCanvas.drawText(unit, 4f, padT, labelPaint)
        zs.forEachIndexed { zi, z ->
            val path = Path()
            curves.forEachIndexed { i, c ->
                val px = x(c.first.toDouble()); val py = y(c.second[zi])
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, if (z == 0.0) CT.Brand else CT.Accent.copy(alpha = 0.55f), style = Stroke(width = if (z == 0.0) 2.dp.toPx() else 1.dp.toPx()))
        }
        points.forEach { p -> drawCircle(CT.Red700, radius = 4.dp.toPx(), center = Offset(x(p.first), y(p.second))) }
    }
}

@Composable
fun GrowthScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val birth = remember(j.journey_id) { vm.store.birthRecord(j.journey_id) }
        if (!birth.confirmed) {
            Page(title = l.tr("Baby growth chart"), back = nav.home) {
                Spacer(gap(8))
                Note(l.tr("Record the birth first - the WHO growth chart needs the baby's birth date and sex."), tone = Tone.Blue)
                Spacer(gap(12))
                PrimaryButton(l.tr("Record the birth"), onClick = nav.birth)
            }
        } else {
            var indicator by remember { mutableStateOf("weight") }
            var entries by remember(j.journey_id) { mutableStateOf(vm.store.growthEntries(j.journey_id)) }
            var date by remember { mutableStateOf(Logic.today()) }
            var value by remember { mutableStateOf("") }
            val table = WhoGrowth.tableFor(indicator, birth.gender)
            val unit = if (indicator == "weight") "kg" else "cm"
            val points = entries.mapNotNull { e ->
                val v = if (indicator == "weight") e.weight_kg else if (indicator == "length") e.length_cm else e.head_cm
                if (v > 0) e.age_months to v else null
            }.sortedBy { it.first }
            val latest = points.lastOrNull()
            val z = latest?.let { WhoGrowth.zScore(table, it.first, it.second) }
            Page(title = l.tr("Baby growth chart"), back = nav.home) {
                Spacer(gap(8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickChip2(l.tr("Weight"), indicator == "weight") { indicator = "weight"; value = "" }
                    PickChip2(l.tr("Length"), indicator == "length") { indicator = "length"; value = "" }
                    PickChip2(l.tr("Head"), indicator == "head") { indicator = "head"; value = "" }
                }
                Spacer(gap(12))
                GrowthChart(table, points, unit)
                Spacer(gap(6))
                Muted(l.tr("WHO Child Growth Standards, 0-24 months. The navy line is the median; the pale band is the usual range. Red dots are your baby's measurements."))
                latest?.let {
                    Spacer(gap(8))
                    when (WhoGrowth.band(z)) {
                        WhoGrowth.Band.ON_TRACK -> Note("✅ " + l.tr("Latest measurement is on track, within the usual WHO range."), tone = Tone.Green)
                        WhoGrowth.Band.BELOW -> Note("⚠ " + l.tr("Latest measurement is below the usual WHO range for age. Discuss it with your doctor or ASHA worker."))
                        WhoGrowth.Band.ABOVE -> Note("⚠ " + l.tr("Latest measurement is above the usual WHO range for age. Discuss it with your doctor."))
                    }
                }
                Spacer(gap(12))
                CTCard {
                    SectionTitle(l.tr("Add a measurement"))
                    Spacer(gap(8))
                    DateField(date, { date = it }, l.tr("Date measured"))
                    Spacer(gap(8))
                    CTField(value, { value = it }, "${l.tr("Value")} ($unit)", keyboard = KeyboardType.Decimal)
                    Spacer(gap(8))
                    PrimaryButton(l.tr("Save measurement"), enabled = (value.toDoubleOrNull() ?: 0.0) > 0.0) {
                        val age = WhoGrowth.ageMonths(birth.date, date) ?: return@PrimaryButton
                        val v = value.toDoubleOrNull() ?: return@PrimaryButton
                        val existing = entries.toMutableList()
                        val idx = existing.indexOfFirst { it.date == date }
                        val base = if (idx >= 0) existing[idx] else GrowthEntry(date = date, age_months = age)
                        val updated = when (indicator) {
                            "weight" -> base.copy(weight_kg = v, age_months = age)
                            "length" -> base.copy(length_cm = v, age_months = age)
                            else -> base.copy(head_cm = v, age_months = age)
                        }
                        if (idx >= 0) existing[idx] = updated else existing.add(updated)
                        entries = existing.sortedBy { it.date }
                        vm.store.saveGrowthEntries(j.journey_id, entries)
                        value = ""
                    }
                }
                if (entries.isNotEmpty()) {
                    Spacer(gap(12))
                    SectionTitle(l.tr("Measurements"))
                    entries.sortedByDescending { it.date }.take(10).forEach { e ->
                        Spacer(gap(6))
                        CTCard(shape = RoundedCornerShape(16.dp), padding = 12) {
                            Text("${e.date} · ${"%.1f".format(e.age_months)} ${l.tr("months")}", style = MaterialTheme.typography.labelMedium)
                            Text(buildString {
                                if (e.weight_kg > 0) append("${Logic.fmtValue(e.weight_kg)} kg   ")
                                if (e.length_cm > 0) append("${Logic.fmtValue(e.length_cm)} cm   ")
                                if (e.head_cm > 0) append("${l.tr("Head")} ${Logic.fmtValue(e.head_cm)} cm")
                            }, style = MaterialTheme.typography.bodySmall, color = CT.Muted)
                        }
                    }
                }
            }
        }
    }
}

// ---- 3. Pediatric immunization scheduler ----------------------------------------

@Composable
fun VaccinesScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val birth = remember(j.journey_id) { vm.store.birthRecord(j.journey_id) }
        if (!birth.confirmed) {
            Page(title = l.tr("Vaccination schedule"), back = nav.home) {
                Spacer(gap(8))
                Note(l.tr("Record the birth first - the baby's vaccination schedule starts from the birth date."), tone = Tone.Blue)
                Spacer(gap(12))
                PrimaryButton(l.tr("Record the birth"), onClick = nav.birth)
            }
        } else {
            var logs by remember(j.journey_id) { mutableStateOf(vm.store.vaccineLogs(j.journey_id)) }
            fun save(m: Map<String, VaccineLog>) { logs = m; vm.store.saveVaccineLogs(j.journey_id, m) }
            val items = VaccineSchedule.ITEMS.map { it to VaccineSchedule.state(it, birth.date, logs[it.key]) }
            Page(title = l.tr("Vaccination schedule"), back = nav.home) {
                Spacer(gap(8))
                Muted(l.tr("India's Universal Immunization Programme schedule, counted from the birth date. Confirm each vaccine with your doctor or health worker and tick it off here."))
                Spacer(gap(12))
                items.forEach { (item, state) ->
                    val due = VaccineSchedule.dueDate(birth.date, item) ?: ""
                    val done = state == VaccineSchedule.State.DONE
                    Spacer(gap(6))
                    CTCard(
                        color = if (done) CT.Green50 else if (state == VaccineSchedule.State.OVERDUE) CT.Amber50 else Color.White,
                        border = if (done) CT.Green300 else if (state == VaccineSchedule.State.OVERDUE) CT.Amber300 else CT.CardBorder,
                        shape = RoundedCornerShape(16.dp), padding = 12,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CareIcon("vaccination", 20.dp, tint = if (done) CT.Green800 else CT.Brand)
                            Column(Modifier.weight(1f)) {
                                Text(l.tr(item.label), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    when (state) {
                                        VaccineSchedule.State.DONE -> "✓ ${logs[item.key]?.date ?: ""}"
                                        VaccineSchedule.State.OVERDUE -> "${l.tr("Overdue")} · ${l.tr("due")} $due (${item.windowNote})"
                                        VaccineSchedule.State.DUE_SOON -> "${l.tr("Due")} $due (${item.windowNote})"
                                        VaccineSchedule.State.UPCOMING -> "${l.tr("Due")} $due (${item.windowNote})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state == VaccineSchedule.State.OVERDUE) CT.Amber900 else CT.Muted,
                                )
                            }
                            Text(
                                if (done) l.tr("Undo") else l.tr("Done"),
                                color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable {
                                    val next = logs.toMutableMap()
                                    if (done) next[item.key] = VaccineLog(item.key, false, "") else next[item.key] = VaccineLog(item.key, true, Logic.today())
                                    save(next)
                                }.padding(6.dp),
                            )
                        }
                    }
                }
                Spacer(gap(12))
                Note(l.tr("This schedule is a guide. Your doctor's advice and your MCP card record are the final word."))
            }
        }
    }
}

// ---- 4. Chronological report vault ----------------------------------------------

@Composable
fun VaultScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, offline ->
        val local = remember(j.journey_id) { vm.store.vaultEntries(j.journey_id) }
        class VaultRow(val filename: String, val date: String, val source: String, val status: String)
        val server = j.milestones.flatMap { m -> m.documents.map { d -> VaultRow(d.filename, "", l.tr(m.title), d.status) } }
        val rows = (local.map { VaultRow(it.filename, it.uploaded_at.take(10), l.tr(it.source), "") } + server)
            .distinctBy { it.filename }
            .sortedByDescending { it.date }
        fun category(name: String): String {
            val n = name.lowercase()
            return when {
                listOf("scan", "usg", "sono", "ultrasound", "tiffa").any { it in n } -> l.tr("Scan")
                listOf("rx", "prescription", "medicine").any { it in n } -> l.tr("Prescription")
                else -> l.tr("Lab report")
            }
        }
        val pending = pendingSyncCount(vm, j.journey_id)
        Page(title = l.tr("Report vault"), back = nav.home) {
            Spacer(gap(8))
            Muted(l.tr("All your reports in one place, newest first - lab results, sonography and prescriptions."))
            if (offline) { Spacer(gap(8)); Note(l.tr("Offline - showing reports from your last synced timeline."), tone = Tone.Blue) }
            Spacer(gap(12))
            PrimaryButton(l.t("uploadReport")) { nav.upload(null) }
            Spacer(gap(12))
            if (rows.isEmpty()) CTCard { Muted(l.tr("No reports yet. Upload your first report and it will appear here.")) }
            rows.forEach { r ->
                Spacer(gap(8))
                CTCard(shape = RoundedCornerShape(16.dp), padding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CareIcon(if (category(r.filename) == l.tr("Scan")) "scan" else if (category(r.filename) == l.tr("Prescription")) "review" else "test", 20.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.filename, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(listOf(r.date, r.source).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CT.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Chip(category(r.filename), CT.BrandSoft, CT.Brand)
                            if (r.status.isNotEmpty()) { Spacer(gap(4)); Chip(l.tr(r.status), CT.Ink.copy(alpha = 0.06f), CT.Ink) }
                        }
                    }
                }
            }
            Spacer(gap(12))
            if (pending > 0) Note(l.tr("$pending records on this phone are waiting to sync to the CareTrail server. Sync arrives with the backend update; your data is safe on this phone."), tone = Tone.Amber)
            else Muted(l.tr("Reports are stored on the CareTrail server; the vault view is cached on this phone for offline reading. See Data safety for what that means."))
            Spacer(gap(8))
            Text(l.tr("Data safety"), color = CT.Brand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = nav.dataSafety).padding(vertical = 8.dp))
        }
    }
}
