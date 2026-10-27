package com.caretrail.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.R
import com.caretrail.app.data.BpReading
import com.caretrail.app.data.BpRules
import com.caretrail.app.data.FollowUps
import com.caretrail.app.data.MentalHealth
import com.caretrail.app.data.Mindfulness
import com.caretrail.app.data.MoodCheckin
import com.caretrail.app.data.RecoveryContent
import com.caretrail.app.data.SugarReading
import com.caretrail.app.data.birthRecord
import com.caretrail.app.data.bpReadings
import com.caretrail.app.data.followUps
import com.caretrail.app.data.healthIntake
import com.caretrail.app.data.highRisk
import com.caretrail.app.data.moodCheckins
import com.caretrail.app.data.saveBpReadings
import com.caretrail.app.data.saveFollowUps
import com.caretrail.app.data.saveHighRisk
import com.caretrail.app.data.saveMoodCheckins
import com.caretrail.app.data.saveSugarReadings
import com.caretrail.app.data.sugarReadings
import kotlinx.coroutines.delay
import java.time.LocalDate

private fun dial(ctx: Context, phone: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (_: Exception) {}
}

@Composable
private fun HubCard(icon: String, title: String, sub: String, onClick: () -> Unit) {
    CTCard(shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon(icon, 20.dp) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = CT.Muted)
            }
            CareIcon("arrow", 16.dp, tint = CT.Muted)
        }
    }
}

/** True when the journey has a confirmed birth (postpartum mode). */
private data class Pp(val ok: Boolean, val birthDate: String, val highRisk: Boolean)

@Composable
private fun ppState(vm: AppViewModel, journeyId: String): Pp {
    val birth = vm.store.birthRecord(journeyId)
    val intake = vm.store.healthIntake(journeyId)
    val hr = vm.store.highRisk(journeyId).any() ||
        intake.conditions.any { it == "hypertension" || it == "diabetes" }
    return Pp(birth.confirmed && birth.date.isNotEmpty(), birth.date, hr)
}

@Composable
private fun NotPostpartumYet(l: L, nav: Nav) {
    Note(l.tr("These features switch on after the birth is recorded."), tone = Tone.Blue)
    Spacer(gap(12))
    OutlineButton(l.tr("Record the birth"), onClick = nav.birth)
}

// ---- 1. Postpartum & wellness hub ------------------------------------------------

@Composable
fun WellnessScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val pp = ppState(vm, j.journey_id)
        var done by remember(j.journey_id) { mutableStateOf(vm.store.followUps(j.journey_id)) }
        Page(title = l.tr("Your recovery & wellbeing"), back = nav.home) {
            Spacer(gap(8))
            if (!pp.ok) { NotPostpartumYet(l, nav); return@Page }
            val days = FollowUps.daysSince(pp.birthDate) ?: 0
            Muted(l.tr("Day $days after delivery. Small steps count - rest, feed, heal."))
            Spacer(gap(12))

            // Time-based follow-ups (PRD 3.3).
            val twoWeek = FollowUps.twoWeekState(pp.birthDate, pp.highRisk, done)
            val c42 = FollowUps.checkup42State(pp.birthDate, done)
            if (twoWeek == FollowUps.State.DUE || c42 == FollowUps.State.DUE) {
                CTCard(color = CT.Red50, border = CT.Red200, shape = RoundedCornerShape(16.dp)) {
                    SectionTitle(l.tr("Checkups due"), )
                    Spacer(gap(8))
                    if (twoWeek == FollowUps.State.DUE) {
                        Text(l.tr("Your 2-week high-risk review is due (blood pressure / sugar check after hypertension or diabetes in pregnancy)."), style = MaterialTheme.typography.bodySmall, color = CT.Red800)
                        Spacer(gap(6))
                        OutlineButton(l.tr("Mark 2-week review done")) {
                            done = done.copy(two_week_done = LocalDate.now().toString())
                            vm.store.saveFollowUps(j.journey_id, done)
                        }
                        Spacer(gap(8))
                    }
                    if (c42 == FollowUps.State.DUE) {
                        Text(l.tr("Your mandatory 42-day postnatal checkup is due. Book it even if you feel fine - BP, wound, bleeding, mood and family planning are reviewed."), style = MaterialTheme.typography.bodySmall, color = CT.Red800)
                        Spacer(gap(6))
                        OutlineButton(l.tr("Mark 42-day checkup done")) {
                            done = done.copy(checkup42_done = LocalDate.now().toString())
                            vm.store.saveFollowUps(j.journey_id, done)
                        }
                    }
                }
                Spacer(gap(12))
            } else if (c42 == FollowUps.State.UPCOMING) {
                Note(l.tr("Your mandatory 42-day checkup is due on ${FollowUps.checkup42DueDate(pp.birthDate) ?: ""} - CareTrail will flag it here."), tone = Tone.Blue)
                Spacer(gap(12))
            }

            HubCard("pregnancy", l.tr("Recovery & wound care"), l.tr("Rest, hygiene, breastfeeding, stitch safety")) { nav.recovery() }
            Spacer(gap(8))
            HubCard("review", l.tr("Mind check-in"), l.tr("Two-minute mood screening with help lines")) { nav.mindCheck() }
            Spacer(gap(8))
            HubCard("blood", l.tr("BP & sugar log"), l.tr("Daily home readings, catches late preeclampsia")) { nav.bpLog() }
            Spacer(gap(8))
            HubCard("hospital", l.tr("Family planning"), l.tr("Spacing and safe options after delivery")) { nav.familyPlanning() }
            Spacer(gap(8))
            HubCard("food", l.tr("Mindfulness corner"), l.tr("Daily affirmation, paced breathing, calming audio")) { nav.mindfulness() }
        }
    }
}

// ---- 2. Recovery & wound care (PRD 3.2) ------------------------------------------

@Composable
fun RecoveryCareScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val pp = ppState(vm, j.journey_id)
        Page(title = l.tr("Recovery & wound care"), back = nav.wellness, backLabel = l.tr("Wellbeing")) {
            Spacer(gap(8))
            if (!pp.ok) { NotPostpartumYet(l, nav); return@Page }
            RecoveryContent.SECTIONS.forEach { s ->
                CTCard {
                    SectionTitle(l.tr(s.title))
                    Spacer(gap(8))
                    s.points.forEach { p ->
                        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = CT.Brand)
                            Text(l.tr(p), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    if (s.warning.isNotEmpty()) {
                        Spacer(gap(4))
                        Note(l.tr(s.warning), tone = Tone.Red)
                    }
                }
                Spacer(gap(12))
            }
            Muted(l.t("medicalReviewNote"))
        }
    }
}

// ---- 3. Family planning & spacing (PRD 3.3) ---------------------------------------

@Composable
fun FamilyPlanningScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val pp = ppState(vm, j.journey_id)
        Page(title = l.tr("Family planning & spacing"), back = nav.wellness, backLabel = l.tr("Wellbeing")) {
            Spacer(gap(8))
            if (!pp.ok) { NotPostpartumYet(l, nav); return@Page }
            CTCard {
                SectionTitle(l.tr("Why spacing matters"))
                Spacer(gap(8))
                RecoveryContent.FAMILY_PLANNING.forEach { p ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = CT.Brand)
                        Text(l.tr(p), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Safe options after delivery"))
                Spacer(gap(8))
                RecoveryContent.FP_METHODS.forEach { m ->
                    Text(l.tr(m.name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Text(l.tr(m.detail), style = MaterialTheme.typography.bodySmall, color = CT.Muted, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Spacer(gap(12))
            Note(l.tr("Discuss the right method for you at your 42-day checkup or with your ASHA worker."), tone = Tone.Green)
        }
    }
}

// ---- 4. Mental health red-flag check-in (PRD 3.3) ---------------------------------

@Composable
fun MindCheckScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val pp = ppState(vm, j.journey_id)
        var flags by remember(j.journey_id) { mutableStateOf(setOf<String>()) }
        var selfHarm by remember(j.journey_id) { mutableStateOf(false) }
        var savedAt by remember { mutableStateOf("") }
        var history by remember(j.journey_id) { mutableStateOf(vm.store.moodCheckins(j.journey_id)) }
        Page(title = l.tr("Mind check-in"), back = nav.wellness, backLabel = l.tr("Wellbeing")) {
            Spacer(gap(8))
            if (!pp.ok) { NotPostpartumYet(l, nav); return@Page }
            Muted(l.tr("After delivery, mood changes are common and treatable. Tick anything that has been true most days over the last two weeks. This stays on your phone."))
            Spacer(gap(12))

            CTCard {
                SectionTitle(l.tr("In the last 2 weeks"))
                Spacer(gap(8))
                MentalHealth.SIGNS.forEach { (key, label) ->
                    val on = key in flags
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (on) CT.BrandSoft else Color.Transparent)
                            .clickable(role = Role.Checkbox) { flags = if (on) flags - key else flags + key }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (on) CT.Brand else CT.FieldBorder))
                        Spacer(gap(10))
                        Text(l.tr(label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(gap(8))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (selfHarm) CT.Red50 else Color.Transparent)
                        .clickable(role = Role.Checkbox) { selfHarm = !selfHarm }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (selfHarm) CT.Red800 else CT.FieldBorder))
                    Spacer(gap(10))
                    Text(l.tr("Thoughts of harming myself, or that my family would be better off without me"), style = MaterialTheme.typography.bodySmall, color = CT.Red800, modifier = Modifier.weight(1f))
                }
            }
            Spacer(gap(12))

            // Zero-click SOS routing: helplines are one tap, always visible once self-harm is ticked.
            if (selfHarm) {
                CTCard(color = CT.Red50, border = CT.Red200, shape = RoundedCornerShape(16.dp)) {
                    Text(l.tr("You are not alone, and this is treatable."), style = MaterialTheme.typography.titleSmall, color = CT.Red800)
                    Spacer(gap(6))
                    Text(l.tr("Please talk to someone right now - these lines are free, confidential and open 24x7:"), style = MaterialTheme.typography.bodySmall, color = CT.Red800)
                    Spacer(gap(10))
                    MentalHealth.SOS_LINES.forEach { (name, number, note) ->
                        CTCard(shape = RoundedCornerShape(12.dp), padding = 12, onClick = { dial(ctx, number) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.tr(name), style = MaterialTheme.typography.labelLarge)
                                    Text(l.tr(note), style = MaterialTheme.typography.bodySmall, color = CT.Muted)
                                }
                                Text(number, color = CT.Brand, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Spacer(gap(6))
                    }
                }
                Spacer(gap(12))
            } else if (flags.isNotEmpty()) {
                Note(l.tr("What you ticked can be postpartum depression or anxiety - common and treatable, not a failing. Please mention this to your doctor or ASHA worker at the next visit, or call Tele-MANAS (14416) any time."), tone = Tone.Amber)
                Spacer(gap(12))
            }

            PrimaryButton(l.tr("Save check-in")) {
                val rec = MoodCheckin(date = LocalDate.now().toString(), flags = flags.toList(), self_harm = selfHarm)
                history = history + rec
                vm.store.saveMoodCheckins(j.journey_id, history)
                savedAt = rec.date
            }
            if (savedAt.isNotEmpty()) { Spacer(gap(8)); Note(l.tr("Check-in saved on your phone."), tone = Tone.Green) }
            if (history.isNotEmpty()) {
                Spacer(gap(12))
                val flagged = history.count { MentalHealth.anyFlag(it) }
                Muted(l.tr("${history.size} check-ins saved, $flagged with flags. Your doctor can review these at a visit."))
            }
        }
    }
}

// ---- 5. Post-discharge BP & sugar log (PRD 3.3) ------------------------------------

@Composable
private fun RiskToggle(label: String, on: Boolean, onFlip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (on) CT.BrandSoft else Color.Transparent)
            .clickable(role = Role.Checkbox, onClick = onFlip)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (on) CT.Brand else CT.FieldBorder))
        Spacer(gap(10))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun BpSugarScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val pp = ppState(vm, j.journey_id)
        var hr by remember(j.journey_id) { mutableStateOf(vm.store.highRisk(j.journey_id)) }
        var bp by remember(j.journey_id) { mutableStateOf(vm.store.bpReadings(j.journey_id)) }
        var sugar by remember(j.journey_id) { mutableStateOf(vm.store.sugarReadings(j.journey_id)) }
        var sys by remember { mutableStateOf("") }
        var dia by remember { mutableStateOf("") }
        var fast by remember { mutableStateOf("") }
        var post by remember { mutableStateOf("") }
        Page(title = l.tr("BP & sugar log"), back = nav.wellness, backLabel = l.tr("Wellbeing")) {
            Spacer(gap(8))
            if (!pp.ok) { NotPostpartumYet(l, nav); return@Page }
            Muted(l.tr("A daily home reading catches late-onset preeclampsia - dangerous BP can rise even weeks after delivery."))
            Spacer(gap(12))

            // High-risk comorbidity flags drive the 2-week follow-up prompt (PRD 3.3).
            CTCard {
                SectionTitle(l.tr("Were you told you had any of these in pregnancy?"))
                Spacer(gap(6))
                RiskToggle(l.tr("Gestational hypertension"), hr.gest_hypertension) { hr = hr.copy(gest_hypertension = !hr.gest_hypertension); vm.store.saveHighRisk(j.journey_id, hr) }
                RiskToggle(l.tr("Preeclampsia"), hr.preeclampsia) { hr = hr.copy(preeclampsia = !hr.preeclampsia); vm.store.saveHighRisk(j.journey_id, hr) }
                RiskToggle(l.tr("Gestational diabetes on insulin"), hr.gdm_insulin) { hr = hr.copy(gdm_insulin = !hr.gdm_insulin); vm.store.saveHighRisk(j.journey_id, hr) }
                if (hr.any() || pp.highRisk) { Spacer(gap(6)); Note(l.tr("High-risk: a BP/sugar review is due 2 weeks after delivery - see the Wellbeing screen."), tone = Tone.Amber) }
            }
            Spacer(gap(12))

            CTCard {
                SectionTitle(l.tr("Add today's blood pressure"))
                Spacer(gap(8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CTField(sys, { sys = it.filter { c -> c.isDigit() }.take(3) }, l.tr("Systolic (top)"), keyboard = KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { CTField(dia, { dia = it.filter { c -> c.isDigit() }.take(3) }, l.tr("Diastolic (bottom)"), keyboard = KeyboardType.Number) }
                }
                Spacer(gap(8))
                PrimaryButton(l.tr("Save BP"), enabled = (sys.toIntOrNull() ?: 0) in 50..260 && (dia.toIntOrNull() ?: 0) in 30..200) {
                    bp = (bp + BpReading(date = LocalDate.now().toString(), systolic = sys.toInt(), diastolic = dia.toInt())).takeLast(90)
                    vm.store.saveBpReadings(j.journey_id, bp)
                    sys = ""; dia = ""
                }
            }
            if (bp.isNotEmpty()) {
                Spacer(gap(12))
                CTCard {
                    SectionTitle(l.tr("Recent readings"))
                    Spacer(gap(8))
                    bp.takeLast(7).asReversed().forEach { r ->
                        val lev = BpRules.level(r.systolic, r.diastolic)
                        val (chipBg, chipFg, txt) = when (lev) {
                            BpRules.Level.NORMAL -> Triple(CT.Green50, CT.Green800, l.tr("Normal"))
                            BpRules.Level.RAISED -> Triple(CT.Amber50, CT.Amber900, l.tr("High"))
                            BpRules.Level.SEVERE -> Triple(CT.Red50, CT.Red800, l.tr("Very high"))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.date, style = MaterialTheme.typography.bodySmall, color = CT.Muted, modifier = Modifier.weight(1f))
                            Text("${r.systolic}/${r.diastolic}", style = MaterialTheme.typography.labelLarge)
                            Spacer(gap(8))
                            Chip(txt, chipBg, chipFg)
                        }
                    }
                }
                if (BpRules.latePreeclampsiaWarning(bp)) {
                    Spacer(gap(8))
                    Note(l.tr("A reading at or above 140/90 after delivery can mean late-onset preeclampsia. If BP is 160/110 or more, or you have a severe headache, blurred vision or upper tummy pain - go to hospital now. Otherwise show these readings to your doctor within 24 hours."), tone = Tone.Red)
                }
            }

            if (hr.gdm_insulin) {
                Spacer(gap(12))
                CTCard {
                    SectionTitle(l.tr("Blood sugar (insulin follow-up)"))
                    Spacer(gap(8))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { CTField(fast, { fast = it.filter { c -> c.isDigit() }.take(3) }, l.tr("Fasting (mg/dL)"), keyboard = KeyboardType.Number) }
                        Box(Modifier.weight(1f)) { CTField(post, { post = it.filter { c -> c.isDigit() }.take(3) }, l.tr("After meal"), keyboard = KeyboardType.Number) }
                    }
                    Spacer(gap(8))
                    PrimaryButton(l.tr("Save sugar"), enabled = (fast.toIntOrNull() ?: 0) > 0 || (post.toIntOrNull() ?: 0) > 0) {
                        sugar = (sugar + SugarReading(date = LocalDate.now().toString(), fasting = fast.toIntOrNull() ?: 0, post_meal = post.toIntOrNull() ?: 0)).takeLast(90)
                        vm.store.saveSugarReadings(j.journey_id, sugar)
                        fast = ""; post = ""
                    }
                    if (sugar.isNotEmpty()) {
                        Spacer(gap(8))
                        sugar.takeLast(5).asReversed().forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.date, style = MaterialTheme.typography.bodySmall, color = CT.Muted, modifier = Modifier.weight(1f))
                                Text(
                                    buildString {
                                        if (r.fasting > 0) append(l.tr("Fasting") + " " + r.fasting)
                                        if (r.fasting > 0 && r.post_meal > 0) append(" · ")
                                        if (r.post_meal > 0) append(l.tr("After meal") + " " + r.post_meal)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // Exportable chart for the 2-week clinical review (PRD 3.3).
            if (bp.isNotEmpty() || sugar.isNotEmpty()) {
                Spacer(gap(12))
                OutlineButton(l.tr("Share readings with your doctor")) {
                    val sb = StringBuilder()
                    sb.append("CareTrail postpartum log (born ").append(pp.birthDate).append(")\n\nBlood pressure:\n")
                    bp.forEach { sb.append(it.date).append("  ").append(it.systolic).append("/").append(it.diastolic).append("\n") }
                    if (sugar.isNotEmpty()) {
                        sb.append("\nBlood sugar (mg/dL):\n")
                        sugar.forEach { sb.append(it.date).append("  fasting ").append(it.fasting).append("  after-meal ").append(it.post_meal).append("\n") }
                    }
                    try {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString())
                        ctx.startActivity(Intent.createChooser(send, null))
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

// ---- 6. Mindfulness corner: affirmation, paced breathing, soothing audio -----------

@Composable
fun MindfulnessScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    Page(title = l.tr("Mindfulness corner"), back = nav.wellness, backLabel = l.tr("Wellbeing")) {
        Spacer(gap(8))
        Muted(l.tr("Two quiet minutes a day. Everything here works offline."))
        Spacer(gap(12))

        // Daily affirmation (rotates by date).
        CTCard(color = CT.BrandSoft, border = CT.BrandSoft, shape = RoundedCornerShape(16.dp)) {
            Text(l.tr("TODAY'S AFFIRMATION"), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
            Spacer(gap(8))
            Text(l.tr(Mindfulness.affirmationOfTheDay()), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(gap(12))

        // Paced breathing 4-4-6.
        CTCard {
            SectionTitle(l.tr("Paced breathing"))
            Spacer(gap(4))
            Muted(l.tr("Breathe in for 4, hold for 4, out for 6. Even one minute settles the body."))
            Spacer(gap(10))
            var running by remember { mutableStateOf(false) }
            var phase by remember { mutableStateOf("") }        // in / hold / out
            var left by remember { mutableIntStateOf(0) }
            LaunchedEffect(running) {
                while (running) {
                    for ((p, secs) in listOf("in" to Mindfulness.BREATH_IN, "hold" to Mindfulness.BREATH_HOLD, "out" to Mindfulness.BREATH_OUT)) {
                        if (!running) break
                        phase = p
                        for (s in secs downTo 1) {
                            if (!running) break
                            left = s
                            delay(1000)
                        }
                    }
                }
            }
            val phaseLabel = when (phase) {
                "in" -> l.tr("Breathe in…")
                "hold" -> l.tr("Hold…")
                else -> l.tr("Breathe out…")
            }
            if (running) {
                Text(phaseLabel, style = MaterialTheme.typography.headlineSmall, color = CT.Brand, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("$left", style = MaterialTheme.typography.titleSmall, color = CT.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(gap(10))
            }
            PrimaryButton(l.tr(if (running) "Stop" else "Start breathing")) { running = !running }
        }
        Spacer(gap(12))

        // Soothing audio, bundled offline.
        CTCard {
            SectionTitle(l.tr("Soothing audio"))
            Spacer(gap(4))
            Muted(l.tr("A soft 1-minute instrumental loop. Plays from your phone - no internet needed."))
            Spacer(gap(10))
            var playing by remember { mutableStateOf(false) }
            val player = remember {
                MediaPlayer().apply {
                    try {
                        val afd = ctx.resources.openRawResourceFd(R.raw.calm_loop)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                        afd.close()
                        isLooping = true
                        prepare()
                    } catch (_: Exception) {}
                }
            }
            DisposableEffect(Unit) { onDispose { try { player.release() } catch (_: Exception) {} } }
            PrimaryButton(l.tr(if (playing) "Stop audio" else "Play calming audio")) {
                try {
                    if (playing) player.pause() else player.start()
                    playing = !playing
                } catch (_: Exception) {}
            }
        }
    }
}
