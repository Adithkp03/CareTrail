package com.caretrail.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.DangerLog
import com.caretrail.app.data.DangerSigns
import com.caretrail.app.data.HealthIntake
import com.caretrail.app.data.IctRecord
import com.caretrail.app.data.Logic
import com.caretrail.app.data.RegistrationData
import com.caretrail.app.data.RhEngine
import com.caretrail.app.data.RhProfile
import com.caretrail.app.data.dangerLogs
import com.caretrail.app.data.healthIntake
import com.caretrail.app.data.registration
import com.caretrail.app.data.rhProfile
import com.caretrail.app.data.saveDangerLogs
import com.caretrail.app.data.saveHealthIntake
import com.caretrail.app.data.saveRegistration
import com.caretrail.app.data.saveRhProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---- Shared bits -------------------------------------------------------------

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Small selectable chip used for blood groups and ICT results. */
@Composable
private fun PickChip(label: String, selected: Boolean, onPick: () -> Unit) {
    Text(
        label, color = if (selected) Color.White else CT.Ink, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (selected) CT.Brand else CT.BrandSoft, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun dial(ctx: Context, phone: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim()))) } catch (_: Exception) {}
}

private fun sms(ctx: Context, phone: String, body: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone.trim())).putExtra("sms_body", body)) } catch (_: Exception) {}
}

private fun loadThumb(path: String, max: Int = 512): ImageBitmap? = try {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
} catch (_: Exception) { null }

// ---- 1. Structured onboarding intake -----------------------------------------

@Composable
fun HealthIntakeScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var data by remember(j.journey_id) { mutableStateOf(vm.store.healthIntake(j.journey_id)) }
        var saved by remember { mutableStateOf(false) }
        fun save(v: HealthIntake) { data = v; vm.store.saveHealthIntake(j.journey_id, v) }
        Page(title = l.tr("Your health profile"), back = nav.home) {
            Spacer(gap(8))
            Muted(l.tr("Answer these once, at your first visit. Your doctor uses this to plan your care. It stays on this phone and is ready to sync later."))
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Health conditions before pregnancy"))
                CheckRow(l.tr("High blood pressure (hypertension)"), "hypertension" in data.conditions) { on ->
                    save(data.copy(conditions = if (on) data.conditions + "hypertension" else data.conditions - "hypertension")); saved = false
                }
                CheckRow(l.tr("Diabetes"), "diabetes" in data.conditions) { on ->
                    save(data.copy(conditions = if (on) data.conditions + "diabetes" else data.conditions - "diabetes")); saved = false
                }
                CheckRow(l.tr("Thyroid disease"), "thyroid" in data.conditions) { on ->
                    save(data.copy(conditions = if (on) data.conditions + "thyroid" else data.conditions - "thyroid")); saved = false
                }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Past surgeries"))
                CheckRow(l.tr("Previous cesarean delivery"), "previous_cesarean" in data.surgeries) { on ->
                    save(data.copy(surgeries = if (on) data.surgeries + "previous_cesarean" else data.surgeries - "previous_cesarean")); saved = false
                }
                CheckRow(l.tr("Myomectomy (fibroid removal)"), "myomectomy" in data.surgeries) { on ->
                    save(data.copy(surgeries = if (on) data.surgeries + "myomectomy" else data.surgeries - "myomectomy")); saved = false
                }
                Spacer(gap(8))
                CTField(data.other_history, { save(data.copy(other_history = it)); saved = false }, l.tr("Any other past illness or surgery"), singleLine = false)
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Allergies"))
                Muted(l.tr("List any drug or food allergies, one per line. Write none if you have none."))
                Spacer(gap(8))
                CTField(data.allergies, { save(data.copy(allergies = it)); saved = false }, l.tr("Drug or food allergies"), singleLine = false)
            }
            Spacer(gap(16))
            PrimaryButton(l.tr(if (data.completed) "Saved - update anyway" else "Save my health profile")) {
                save(data.copy(completed = true)); saved = true
            }
            if (saved) { Spacer(gap(8)); Note(l.tr("Health profile saved on this phone."), tone = Tone.Green) }
            Spacer(gap(8))
            OutlineButton(l.tr("Next: first-visit checklist"), onClick = nav.registration)
        }
    }
}

// ---- 1b. First-visit registration checklist ----------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var data by remember(j.journey_id) { mutableStateOf(vm.store.registration(j.journey_id)) }
        fun save(v: RegistrationData) { data = v; vm.store.saveRegistration(j.journey_id, v) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        try {
                            val dir = File(ctx.filesDir, "mcp/${j.journey_id}").apply { mkdirs() }
                            val f = File(dir, "${System.currentTimeMillis()}.jpg")
                            ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                            if (f.length() > 0) f.absolutePath else null
                        } catch (_: Exception) { null }
                    }
                    if (path != null) save(data.copy(mcp_images = data.mcp_images + path))
                }
            }
        }
        Page(title = l.tr("First-visit checklist"), back = nav.home) {
            Spacer(gap(8))
            CTCard {
                SectionTitle(l.tr("STI / serology tests (both partners)"))
                Muted(l.tr("Ask for these at your first booking visit."))
                CheckRow(l.tr("HIV test - you"), data.hiv_mother) { save(data.copy(hiv_mother = it)) }
                CheckRow(l.tr("HIV test - your partner"), data.hiv_partner) { save(data.copy(hiv_partner = it)) }
                CheckRow(l.tr("VDRL test - you"), data.vdrl_mother) { save(data.copy(vdrl_mother = it)) }
                CheckRow(l.tr("VDRL test - your partner"), data.vdrl_partner) { save(data.copy(vdrl_partner = it)) }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("HPLC (hemoglobinopathy screening)"))
                Muted(l.tr("HPLC checks for Thalassemia trait and hemoglobin variants. Ask your doctor or lab to schedule it."))
                CheckRow(l.tr("HPLC scheduled"), data.hplc_scheduled) { save(data.copy(hplc_scheduled = it)) }
                CheckRow(l.tr("HPLC done"), data.hplc_done) { save(data.copy(hplc_done = it)) }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Register your pregnancy"))
                Muted(l.tr("Register at your nearest government health facility (PHC / CHC / sub-centre). Your ASHA or Village Health Nurse can help you register and get your MCP card."))
                CheckRow(l.tr("Registered at a government facility"), data.registered) { save(data.copy(registered = it)) }
                Spacer(gap(8))
                CTField(data.facility, { save(data.copy(facility = it)) }, l.tr("Facility name (PHC / CHC)"))
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("ASHA / Village Health Nurse contacts"))
                Muted(l.tr("Save your local health worker numbers so they are one tap away, even offline."))
                Spacer(gap(8))
                CTField(data.asha_name, { save(data.copy(asha_name = it)) }, l.tr("ASHA name"))
                Spacer(gap(8))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CTField(data.asha_phone, { save(data.copy(asha_phone = it)) }, l.tr("ASHA phone"), keyboard = androidx.compose.ui.text.input.KeyboardType.Phone) }
                    if (data.asha_phone.isNotBlank()) Text(l.tr("Call"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { dial(ctx, data.asha_phone) }.padding(8.dp))
                }
                Spacer(gap(8))
                CTField(data.vhn_name, { save(data.copy(vhn_name = it)) }, l.tr("VHN name"))
                Spacer(gap(8))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CTField(data.vhn_phone, { save(data.copy(vhn_phone = it)) }, l.tr("VHN phone"), keyboard = androidx.compose.ui.text.input.KeyboardType.Phone) }
                    if (data.vhn_phone.isNotBlank()) Text(l.tr("Call"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { dial(ctx, data.vhn_phone) }.padding(8.dp))
                }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("MCP card locker"))
                Muted(l.tr("Save your Mother & Child Protection card number and a photo of the card. Photos stay in a private locker on this phone."))
                Spacer(gap(8))
                CTField(data.mcp_number, { save(data.copy(mcp_number = it)) }, l.tr("MCP card number"))
                Spacer(gap(12))
                if (data.mcp_images.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.mcp_images.forEach { path ->
                            val bmp = remember(path) { loadThumb(path) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bmp != null) Image(bmp, contentDescription = l.tr("MCP card photo"), modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                else Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon("review", 24.dp) }
                                Text(l.tr("Remove"), color = CT.Red700, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { File(path).delete(); save(data.copy(mcp_images = data.mcp_images - path)) }.padding(4.dp))
                            }
                        }
                    }
                    Spacer(gap(12))
                }
                OutlineButton(l.tr("Add MCP card photo")) { picker.launch("image/*") }
            }
            Spacer(gap(12))
            Note(l.tr("This checklist is stored on your phone and works offline. It does not replace the tests themselves - tick items only when they are actually done."))
        }
    }
}

// ---- 2. Rh isoimmunization decision engine ------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RhScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val weeks = j.gestational_age.weeks
        var data by remember(j.journey_id) { mutableStateOf(vm.store.rhProfile(j.journey_id)) }
        fun save(v: RhProfile) { data = v; vm.store.saveRhProfile(j.journey_id, v) }
        var ictDate by remember { mutableStateOf("") }
        var ictResult by remember { mutableStateOf("") }
        var ictNote by remember { mutableStateOf("") }
        val status = RhEngine.status(data)
        Page(title = l.tr("Rh factor & ICT tracking"), back = nav.home) {
            Spacer(gap(8))
            CTCard {
                SectionTitle(l.tr("Your blood group"))
                Muted(l.tr("From your first-visit blood test."))
                Spacer(gap(8))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RhEngine.GROUPS.forEach { g -> PickChip(g, data.maternal_group == g) { save(data.copy(maternal_group = g)) } }
                }
            }
            Spacer(gap(12))
            when (status) {
                RhEngine.Status.RECORD_MATERNAL -> Note(l.tr("Select your blood group above. If you are Rh-negative, CareTrail starts ICT tracking for you."), tone = Tone.Blue)
                RhEngine.Status.NO_RISK -> Note(l.tr("You are Rh-positive, so Rh isoimmunization is not a concern in this pregnancy."), tone = Tone.Green)
                else -> {
                    // Rh-negative mother: partner group drives the rest of the engine.
                    CTCard {
                        SectionTitle(l.tr("Partner's blood group"))
                        Muted(l.tr("Because you are Rh-negative, your partner needs a blood group test."))
                        Spacer(gap(8))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RhEngine.GROUPS.forEach { g -> PickChip(g, data.partner_group == g) { save(data.copy(partner_group = g)) } }
                        }
                    }
                    Spacer(gap(12))
                    when (status) {
                        RhEngine.Status.PROMPT_PARTNER -> Note("⚠ ${l.tr("You are Rh-negative. Please get your partner's blood group tested and select it above.")}")
                        RhEngine.Status.PARTNER_NEGATIVE -> Note(l.tr("Your partner is also Rh-negative, so Rh isoimmunization is unlikely. Your doctor may still repeat ICT as a routine check."), tone = Tone.Green)
                        else -> {
                            CTCard(color = CT.Amber50, border = CT.Amber300) {
                                Text("⚠ ${l.tr("Rh mismatch possible")}", color = CT.Amber900, style = MaterialTheme.typography.titleSmall)
                                Spacer(gap(6))
                                Text(l.tr("You are Rh-negative and your partner is Rh-positive. CareTrail is tracking your ICT (Indirect Coombs Test) and Anti-D prophylaxis with your doctor."), color = CT.Amber900, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(gap(12))
                            CTCard {
                                SectionTitle(l.tr("ICT & Anti-D plan"))
                                Spacer(gap(8))
                                RhEngine.actions(data, weeks).forEach { a ->
                                    val (title, sub) = when (a.key) {
                                        "ict_initial" -> l.tr("Initial ICT (Indirect Coombs Test)") to l.tr("Book the first ICT now.")
                                        "ict_28w" -> l.tr("Repeat ICT at 28 weeks") to if (a.due) l.tr("You are at or past 28 weeks - repeat the ICT now.") else l.tr("Due when you reach 28 weeks.")
                                        else -> l.tr("Anti-D injection at 28 weeks") to if (a.due) l.tr("You are at or past 28 weeks - discuss Anti-D immunoglobulin with your doctor now.") else l.tr("Discuss Anti-D prophylaxis with your doctor at 28 weeks.")
                                    }
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(if (a.done) "✅" else if (a.due) "●" else "○", color = if (a.done) CT.Green800 else if (a.due) CT.Amber900 else CT.Muted)
                                        Column { Text(title, style = MaterialTheme.typography.bodyMedium); Text(sub, style = MaterialTheme.typography.bodySmall, color = if (a.done) CT.Muted else if (a.due) CT.Amber900 else CT.Muted) }
                                    }
                                }
                                if (weeks >= 28 && !data.anti_d_given) {
                                    Spacer(gap(8))
                                    Note("⚠ ${l.tr("28 weeks reached: repeat ICT and ask your doctor about the Anti-D injection.")}")
                                }
                            }
                            Spacer(gap(12))
                            CTCard {
                                SectionTitle(l.tr("Log an ICT result"))
                                Spacer(gap(8))
                                DateField(ictDate, { ictDate = it }, l.tr("Test date"))
                                Spacer(gap(8))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PickChip(l.tr("Negative"), ictResult == "negative") { ictResult = "negative" }
                                    PickChip(l.tr("Positive"), ictResult == "positive") { ictResult = "positive" }
                                }
                                Spacer(gap(8))
                                CTField(ictNote, { ictNote = it }, l.tr("Note (optional)"))
                                Spacer(gap(8))
                                PrimaryButton(l.tr("Save ICT result"), enabled = ictDate.isNotEmpty() && ictResult.isNotEmpty()) {
                                    save(data.copy(ict_log = (data.ict_log + IctRecord(date = ictDate, weeks = weeks, result = ictResult, note = ictNote.trim())).sortedBy { it.date }))
                                    ictDate = ""; ictResult = ""; ictNote = ""
                                }
                                if (data.ict_log.isNotEmpty()) {
                                    Spacer(gap(12))
                                    data.ict_log.forEach { r ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            CareIcon("blood", 18.dp)
                                            Column {
                                                Text("${r.date} · ${l.tr(if (r.result == "positive") "Positive" else "Negative")} · ${r.weeks} ${l.t("weeks")}", style = MaterialTheme.typography.bodyMedium)
                                                if (r.note.isNotEmpty()) Muted(r.note)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(gap(12))
                            CTCard {
                                CheckRow(l.tr("Anti-D prophylaxis given"), data.anti_d_given) { save(data.copy(anti_d_given = it, anti_d_date = if (it) Logic.today() else "")) }
                                if (data.anti_d_given) { Spacer(gap(8)); DateField(data.anti_d_date, { save(data.copy(anti_d_date = it)) }, l.tr("Anti-D date")) }
                            }
                        }
                    }
                }
            }
            Spacer(gap(12))
            Note(l.tr("This tracker supports your doctor's plan. A positive ICT or any bleeding needs medical attention - contact your doctor."))
        }
    }
}

// ---- 3. Danger-sign checklist (28+ weeks) -------------------------------------

@Composable
fun DangerSignsScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val weeks = j.gestational_age.weeks
        val reg = remember(j.journey_id) { vm.store.registration(j.journey_id) }
        var logs by remember(j.journey_id) { mutableStateOf(vm.store.dangerLogs(j.journey_id)) }
        val today = Logic.today()
        var selected by remember(j.journey_id, today) { mutableStateOf(logs.firstOrNull { it.date == today }?.symptoms ?: emptyList()) }
        var savedTick by remember { mutableStateOf(false) }
        val urgent = DangerSigns.urgent(selected)
        Page(title = l.tr("Danger-sign check"), back = nav.home) {
            Spacer(gap(8))
            if (weeks < DangerSigns.START_WEEK) {
                Note(l.tr("This preeclampsia checklist starts at 28 weeks. Until then, know the general warning signs below and contact your doctor for any of them."), tone = Tone.Blue)
                Spacer(gap(12))
            } else {
                CTCard {
                    SectionTitle(l.tr("How are you today?"))
                    Muted(l.tr("Tick anything you have had today. These can be signs of preeclampsia from 28 weeks onward."))
                    Spacer(gap(6))
                    DangerSigns.PREECLAMPSIA.forEach { (key, label) ->
                        CheckRow(l.tr(label), key in selected) { on ->
                            selected = if (on) selected + key else selected - key; savedTick = false
                        }
                    }
                }
                Spacer(gap(12))
                if (urgent) {
                    Column(Modifier.fillMaxWidth().background(CT.Red50, RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CareIcon("warning", 22.dp, tint = CT.Red800)
                            Text(l.tr("Contact your doctor now"), color = CT.Red800, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(gap(6))
                        Text(l.tr("These can be signs of preeclampsia, which can become serious quickly. Call your doctor or health worker now, or go to the nearest hospital. Do not wait for your next visit."), color = CT.Red800, style = MaterialTheme.typography.bodySmall)
                        Spacer(gap(12))
                        PrimaryButton(l.tr("Call 108 (ambulance)")) { dial(ctx, "108") }
                        if (reg.asha_phone.isNotBlank()) {
                            Spacer(gap(8))
                            OutlineButton(l.tr("Call") + " " + reg.asha_name.ifBlank { l.tr("ASHA") }) { dial(ctx, reg.asha_phone) }
                        }
                        if (reg.asha_phone.isNotBlank() || reg.vhn_phone.isNotBlank()) {
                            Spacer(gap(8))
                            val to = reg.asha_phone.ifBlank { reg.vhn_phone }
                            OutlineButton(l.tr("Send SMS to health worker")) {
                                sms(ctx, to, "CareTrail danger signs (${j.patient.name}, ${weeks}w): " + selected.joinToString(", ") { DangerSigns.symptomLabel(it) })
                            }
                        }
                    }
                    Spacer(gap(12))
                }
                PrimaryButton(l.tr(if (savedTick) "Saved" else "Save today's check")) {
                    val entry = DangerLog(date = today, weeks = weeks, symptoms = selected)
                    val next = logs.filterNot { it.date == today } + entry
                    logs = next.sortedByDescending { it.date }
                    vm.store.saveDangerLogs(j.journey_id, logs)
                    savedTick = true
                }
                if (logs.isNotEmpty()) {
                    Spacer(gap(16))
                    SectionTitle(l.tr("Recent checks"))
                    logs.take(7).forEach { log ->
                        Spacer(gap(8))
                        CTCard(color = if (log.symptoms.isEmpty()) Color.White else CT.Red50, border = if (log.symptoms.isEmpty()) CT.CardBorder else CT.Red200, shape = RoundedCornerShape(16.dp), padding = 12) {
                            Text("${log.date} · ${log.weeks} ${l.t("weeks")}", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (log.symptoms.isEmpty()) l.tr("No danger signs") else log.symptoms.joinToString(", ") { l.tr(DangerSigns.symptomLabel(it)) },
                                style = MaterialTheme.typography.bodySmall, color = if (log.symptoms.isEmpty()) CT.Muted else CT.Red800,
                            )
                        }
                    }
                }
                Spacer(gap(12))
            }
            Column(Modifier.fillMaxWidth().background(CT.Red50, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("${l.t("dangerSigns")}:", color = CT.Red800, style = MaterialTheme.typography.titleSmall)
                Spacer(gap(6))
                j.danger_signs.forEach { Text("• ${l.tr(it)}", color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(gap(8))
            Muted(l.tr("This checklist is not emergency monitoring. For warning signs at any stage, contact your doctor immediately."))
        }
    }
}
