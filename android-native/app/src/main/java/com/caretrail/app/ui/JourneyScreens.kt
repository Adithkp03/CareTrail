package com.caretrail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.JourneyState
import com.caretrail.app.data.CareContent
import com.caretrail.app.data.Journey
import com.caretrail.app.data.Langs
import com.caretrail.app.data.Logic
import com.caretrail.app.data.Milestone
import com.caretrail.app.data.NextUp
import com.caretrail.app.data.RhEngine
import com.caretrail.app.data.VaccineSchedule
import com.caretrail.app.data.birthRecord
import com.caretrail.app.data.vaccineLogs
import com.caretrail.app.data.healthIntake
import com.caretrail.app.data.rhProfile

/** Loads the patient's journey and renders [content] once it is ready. */
@Composable
fun JourneyGate(vm: AppViewModel, onEmpty: () -> Unit, content: @Composable (Journey, NextUp?, Boolean) -> Unit) {
    val state by vm.journey.collectAsState()
    val l = rememberL(vm)
    LaunchedEffect(Unit) { vm.refreshJourney() }
    when (val s = state) {
        is JourneyState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CT.Brand)
                Spacer(gap(12)); Muted(l.t("loading"))
            }
        }
        is JourneyState.Empty -> LaunchedEffect(Unit) { onEmpty() }
        is JourneyState.Failed -> Page {
            Spacer(gap(48))
            Note(s.message, tone = Tone.Red)
            Spacer(gap(12))
            OutlineButton("Retry") { vm.refreshJourney() }
        }
        is JourneyState.Ready -> content(s.journey, s.nextUp, s.offline)
    }
}

@Composable
private fun StatusDot(status: String): String = when (status) { "done" -> "✓"; "next", "upcoming" -> "○"; else -> "●" }

@Composable
fun MilestoneRow(m: Milestone, l: L, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CareIcon(Logic.iconFor(m), 20.dp)
        Text(StatusDot(m.status), color = CT.Brand)
        Column(Modifier.weight(1f)) {
            Text(l.tr(m.title), style = MaterialTheme.typography.bodyMedium)
            val sub = buildString {
                if (m.status == "done" && m.completed_at != null) append("✓ ${m.completed_at}")
                m.completion_level?.let { append(" · ${l.tr(Logic.LEVEL_TEXT[it])}") }
                if (m.status == "upcoming" && m.scheduled_date != null) append("📅 ${m.scheduled_date}")
                if (m.status == "now") append("${l.t("window")}: ${m.windowLabel} ${l.t("weeks")}")
                if (m.status == "next") append("${m.windowLabel} ${l.t("weeks")}")
            }
            if (sub.isNotBlank()) Muted(sub)
            m.signoff?.let { Text("✅ ${l.t("signedOffBy")} ${it.doctor_name}", style = MaterialTheme.typography.labelSmall, color = CT.Green800) }
        }
        if (m.overdue) Chip(l.t("overdue"), CT.Amber100, CT.Amber900)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, offline ->
        val zones = Logic.zones(j)
        val week = j.gestational_age.weeks
        val stage = Logic.trimester(week)
        val total = j.milestones.size
        val done = j.summary["done"] ?: 0
        val att = Logic.attention(j)
        val attention = att.overdueTitles.map { "${l.t("overdueMilestone")}: ${l.tr(it)}" } +
            att.unresolved.map { "${l.t("reviewPending")}: ${l.tr(it.label)} (${Logic.fmtValue(it.value)} ${it.unit})" }
        val current = zones["now"]?.firstOrNull()
        val next = zones["upcoming"]?.firstOrNull() ?: zones["next"]?.firstOrNull()
        // Sprint 2: postpartum mode starts from a confirmed birth record.
        val birth = vm.store.birthRecord(j.journey_id)
        val postpartum = birth.confirmed && birth.date.isNotEmpty()
        val babyDays = if (postpartum) java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(birth.date), java.time.LocalDate.now()).toInt() else 0
        val nextVax = if (postpartum) VaccineSchedule.openItems(birth.date, vm.store.vaccineLogs(j.journey_id)).firstOrNull() else null

        Page {
            if (offline) Note("Offline - showing your last loaded timeline.", Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(CT.SoftBlue), contentAlignment = Alignment.Center) {
                        Text(j.patient.name.take(1), color = CT.Brand, style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("CARETRAIL", color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                        Text(j.patient.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                LangPicker(l.lang, { vm.translator.setLang(it) }, compact = true)
            }
            if (l.lang !in Langs.reviewed) { Spacer(gap(12)); Note(l.t("translationNote")) }
            Spacer(gap(16))
            if (!postpartum) {
            // "You are here" hero
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(CT.Navy, CT.NavyEnd))).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(l.t("journeyTitle"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                        Spacer(gap(8))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$week", color = Color.White, style = MaterialTheme.typography.displayLarge)
                            Text(" ${l.t("weeks")}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(gap(4))
                        Text("+ ${j.gestational_age.plus_days} ${l.t("days")} · ${l.t(Logic.STAGES[stage])}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        CareIcon("pregnancy", 34.dp, tint = Color(0xFFB9CFFF))
                    }
                }
                Spacer(gap(12))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(l.t("youAreHere"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Text("$done / $total ${l.t("completedOf")}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(gap(8))
                LinearProgressIndicator(
                    progress = { Logic.progressFraction(j) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = CT.Accent, trackColor = Color.White.copy(alpha = 0.15f),
                )
            }
            Spacer(gap(16))
            // NOW / NEXT / attention
            CTCard(shape = RoundedCornerShape(16.dp), onClick = { if (current != null) nav.milestone(current.id) else nav.timeline() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon("visit", 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(l.t("now").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                        Text(if (current != null) l.tr(current.title) else l.tr("No care step due right now"), style = MaterialTheme.typography.titleSmall)
                        Muted(if (current != null) l.t("currentWindow") else l.tr("Open your journey to see all steps"))
                    }
                }
            }
            Spacer(gap(8))
            CTCard(shape = RoundedCornerShape(16.dp), onClick = { if (next != null) nav.milestone(next.id) else nav.timeline() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.Peach), contentAlignment = Alignment.Center) { CareIcon("review", 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(l.t("next").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                        Text(if (next != null) l.tr(next.title) else l.tr("No next step listed"), style = MaterialTheme.typography.titleSmall)
                        Muted(next?.scheduled_date ?: next?.let { "${it.window_weeks[0].toInt()}-${it.window_weeks[1].toInt()} ${l.t("weeks")}" } ?: "")
                    }
                }
            }
            } else {
            // Postpartum hero: baby's age + next vaccine instead of the pregnancy timeline.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(CT.Navy, CT.NavyEnd))).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(l.tr("Your baby").uppercase(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                        Spacer(gap(8))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${babyDays / 7}", color = Color.White, style = MaterialTheme.typography.displayLarge)
                            Text(" ${l.t("weeks")}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(gap(4))
                        Text("+ ${babyDays % 7} ${l.t("days")}" + (if (birth.gender.isNotEmpty()) " · " + l.tr(if (birth.gender == "girl") "Girl" else "Boy") else ""), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        CareIcon("pregnancy", 34.dp, tint = Color(0xFFB9CFFF))
                    }
                }
            }
            Spacer(gap(16))
            CTCard(shape = RoundedCornerShape(16.dp), onClick = nav.vaccines) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon("vaccination", 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(l.tr("Next vaccine").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                        Text(if (nextVax != null) l.tr(nextVax.first.label) else l.tr("All vaccines done"), style = MaterialTheme.typography.titleSmall)
                        Muted(if (nextVax != null) "${l.tr("Due")} ${VaccineSchedule.dueDate(birth.date, nextVax.first) ?: ""} (${nextVax.first.windowNote})" else l.tr("The schedule is complete - well done"))
                    }
                }
            }
            Spacer(gap(8))
            CTCard(shape = RoundedCornerShape(16.dp), onClick = nav.growth) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.Peach), contentAlignment = Alignment.Center) { CareIcon("review", 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(l.tr("Growth chart").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                        Text(l.tr("Weight, length and head against WHO standards"), style = MaterialTheme.typography.titleSmall)
                        Muted(l.tr("Log today's measurement after each check-up"))
                    }
                }
            }
            }
            Spacer(gap(8))
            val hasAtt = attention.isNotEmpty()
            CTCard(
                color = if (hasAtt) CT.Amber50 else Color.White, border = if (hasAtt) CT.Amber300 else CT.CardBorder,
                shape = RoundedCornerShape(16.dp), onClick = nav.reminders,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { CareIcon("bell", 20.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(l.t("attentionNeeded").uppercase() + if (hasAtt) " · ${attention.size}" else "", color = if (hasAtt) CT.Amber900 else CT.Ink, style = MaterialTheme.typography.labelSmall)
                        Text(attention.firstOrNull() ?: l.t("noOpenItems"), style = MaterialTheme.typography.bodySmall, color = if (hasAtt) CT.Amber900 else CT.Ink)
                        if (attention.size > 1) Text("+ ${attention.size - 1} ${l.tr("more to review")}", style = MaterialTheme.typography.labelSmall, color = CT.Amber900)
                    }
                }
            }
            Spacer(gap(8))
            // Sprint 1: health profile prompt, Rh follow-ups and the 28-week danger-sign check.
            val intakeDone = vm.store.healthIntake(j.journey_id).completed
            if (!intakeDone) {
                CTCard(shape = RoundedCornerShape(16.dp), onClick = nav.intake) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon("visit", 20.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(l.tr("Finish your health profile").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                            Text(l.tr("Conditions, surgeries, allergies and first-visit tests - answer once, use every visit."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(gap(8))
            }
            val rhOpen = RhEngine.openDueActions(vm.store.rhProfile(j.journey_id), week)
            if (rhOpen > 0) {
                CTCard(color = CT.Amber50, border = CT.Amber300, shape = RoundedCornerShape(16.dp), onClick = nav.rh) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { CareIcon("blood", 20.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(l.tr("Rh follow-up due").uppercase(), color = CT.Amber900, style = MaterialTheme.typography.labelSmall)
                            Text(l.tr("ICT / Anti-D steps need attention."), style = MaterialTheme.typography.bodySmall, color = CT.Amber900)
                        }
                    }
                }
                Spacer(gap(8))
            }
            if (!postpartum && week >= com.caretrail.app.data.DangerSigns.START_WEEK) {
                CTCard(color = CT.Red50, border = CT.Red200, shape = RoundedCornerShape(16.dp), onClick = nav.danger) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { CareIcon("warning", 20.dp, tint = CT.Red800) }
                        Column(Modifier.weight(1f)) {
                            Text(l.tr("Danger-sign check").uppercase(), color = CT.Red800, style = MaterialTheme.typography.labelSmall)
                            Text(l.tr("Quick preeclampsia check for 28+ weeks - tick any symptoms you have today."), style = MaterialTheme.typography.bodySmall, color = CT.Red800)
                        }
                    }
                }
                Spacer(gap(8))
            }
            if (!postpartum && week >= 36) {
                CTCard(shape = RoundedCornerShape(16.dp), onClick = nav.birth) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CT.BrandSoft), contentAlignment = Alignment.Center) { CareIcon("pregnancy", 20.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(l.tr("Baby arrived?").uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelSmall)
                            Text(l.tr("Record the birth to switch to postpartum mode - growth chart and vaccines."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(gap(8))
            }
            Spacer(gap(8))
            // Doctor instructions
            if (!offline) DoctorInstructions(vm, j.journey_id, l)
            Spacer(gap(16))
            // Quick access
            CTCard {
                SectionTitle(l.tr("Quick access"))
                Spacer(gap(12))
                val items = if (postpartum) listOf(
                    Triple("vaccination", l.tr("Vaccines"), nav.vaccines), Triple("pregnancy", l.tr("Growth chart"), nav.growth),
                    Triple("review", l.tr("Report vault"), nav.vault), Triple("upload", l.t("uploadReport"), { nav.upload(null) }),
                    Triple("visit", l.t("doctorView"), nav.doctor), Triple("bell", l.tr("Reminders"), nav.reminders),
                    Triple("test", l.tr("Health profile"), nav.intake), Triple("hospital", l.tr("First-visit checklist"), nav.registration),
                ) else listOf(
                    Triple("review", l.tr("Timeline"), nav.timeline), Triple("bell", l.tr("Reminders"), nav.reminders),
                    Triple("movement", l.tr("Movements"), nav.movements), Triple("upload", l.t("uploadReport"), { nav.upload(null) }),
                    Triple("visit", l.t("doctorView"), nav.doctor), Triple("pregnancy", l.tr("Care guidance"), nav.care),
                    Triple("test", l.tr("Health profile"), nav.intake), Triple("hospital", l.tr("First-visit checklist"), nav.registration),
                    Triple("blood", l.tr("Rh & ICT"), nav.rh), Triple("warning", l.tr("Danger signs"), nav.danger),
                )
                items.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (icon, label, go) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CT.BrandSoft).clickable(role = Role.Button, onClick = go).padding(vertical = 14.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CareIcon(icon, 24.dp)
                                Spacer(gap(6))
                                Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Spacer(gap(12))
            Text(l.tr("Prepare for your next visit"), color = CT.Brand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = nav.prep).padding(vertical = 8.dp))
            Muted(l.t("medicalReviewNote"))
            Text(l.t("dataSafety"), color = CT.Brand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = nav.dataSafety).padding(vertical = 8.dp))
            Spacer(gap(8))
            Text(l.tr("Warning signs? Contact your doctor now. Read care guidance."), color = CT.Red800, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().background(CT.Red50, RoundedCornerShape(12.dp)).clickable(onClick = nav.care).padding(12.dp))
            Spacer(gap(16))
            OutlineButton(l.tr("Your data"), onClick = nav.account)
            Spacer(gap(8))
            OutlineButton(l.t("logout")) { vm.logout() }
        }
    }
}

@Composable
fun TimelineScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, offline ->
        val week = j.gestational_age.weeks
        val current = Logic.trimester(week)
        var expanded by remember { mutableStateOf(setOf<Int>()) }
        var showByStatus by remember { mutableStateOf(false) }
        Page(title = l.tr("Your timeline"), back = nav.home) {
            if (offline) Note("Offline - showing your last loaded timeline.", Modifier.padding(vertical = 8.dp))
            Spacer(gap(8))
            CTCard(color = CT.Brand, border = CT.Brand, shape = RoundedCornerShape(16.dp)) {
                Text(l.t("youAreHere").uppercase(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text("$week ${l.t("weeks")} + ${j.gestational_age.plus_days} ${l.t("days")}", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(l.t(Logic.STAGES[current]), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                Text("${l.tr("Due date")}: ${j.edd}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(gap(12))
            Muted("✓ ${l.t("done")}   ● ${l.t("now")}   ○ ${l.t("next")}")
            Spacer(gap(8))
            CTCard {
                Logic.STAGES.forEachIndexed { i, stage ->
                    val items = j.milestones.filter { Logic.milestoneStage(it) == i }
                    val shown = Logic.highlighted(items, i, current)
                    val rest = items.filter { it !in shown }
                    val lineColor = if (i == current) CT.Brand else CT.Brand.copy(alpha = 0.2f)
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(if (i == Logic.STAGES.lastIndex) Color.Transparent else lineColor))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.padding(bottom = 16.dp)) {
                            Text(l.t(stage), style = MaterialTheme.typography.titleSmall)
                            if (i == current) {
                                Spacer(gap(4))
                                Chip("● ${l.t("youAreHere")} · $week ${l.t("weeks")}", CT.BrandSoft, CT.Brand)
                            }
                            shown.forEach { MilestoneRow(it, l) { nav.milestone(it.id) } }
                            if (rest.isNotEmpty()) {
                                Text("${l.t("showAllMilestones")} · ${items.size}", color = CT.Brand, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clickable { expanded = if (i in expanded) expanded - i else expanded + i }.padding(vertical = 6.dp))
                                if (i in expanded) rest.forEach { MilestoneRow(it, l) { nav.milestone(it.id) } }
                            }
                        }
                    }
                }
                if (j.milestones.any { it.signoff != null }) Muted(l.t("prototypeReview"))
            }
            Spacer(gap(12))
            Text("${l.t("journeyTitle")} · ${l.t("done")} / ${l.t("now")} / ${l.t("next")}", color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { showByStatus = !showByStatus }.padding(vertical = 8.dp))
            if (showByStatus) {
                Logic.zones(j).forEach { (zone, list) ->
                    if (list.isEmpty()) return@forEach
                    Text("${l.t(zone)} · ${list.size}".uppercase(), style = MaterialTheme.typography.labelSmall, color = CT.Muted, modifier = Modifier.padding(top = 8.dp))
                    list.forEach { m ->
                        CTCard(Modifier.padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), padding = 6, onClick = { nav.milestone(m.id) }) { MilestoneRow(m, l) { nav.milestone(m.id) } }
                    }
                }
            }
            Spacer(gap(12))
            Muted(l.t("medicalReviewNote"))
        }
    }
}

@Composable
fun CareScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, offline ->
        Page(title = l.tr("Care guidance"), back = nav.home) {
            if (!offline) DoctorInstructions(vm, j.journey_id, l)
            Spacer(gap(12))
            CTCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareIcon("pregnancy", 20.dp)
                    Column { Text(l.t("earlyCare"), style = MaterialTheme.typography.titleSmall); Muted(l.t("earlyCareHint")) }
                }
                Spacer(gap(12))
                CareContent.steps.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(if (s.urgent) CT.Red50 else CT.BrandSoft, RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CareIcon(s.icon, 22.dp, tint = if (s.urgent) CT.Red800 else CT.Brand)
                        Column { Text(l.care(s.title), style = MaterialTheme.typography.titleSmall); Text(l.care(s.body), style = MaterialTheme.typography.bodySmall, color = CT.Muted) }
                    }
                }
                Spacer(gap(8))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareIcon("test", 20.dp); Text(l.t("firstVisitChecks"), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(gap(8))
                CareContent.tests.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(CT.Ink.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareIcon(t.icon, 18.dp); Text(l.care(t.text), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(gap(16))
            Column(Modifier.fillMaxWidth().background(CT.Red50, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("${l.t("dangerSigns")}:", color = CT.Red800, style = MaterialTheme.typography.titleSmall)
                Spacer(gap(6))
                j.danger_signs.forEach { Text("• ${l.tr(it)}", color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(gap(12))
            Muted(l.t("medicalReviewNote"))
        }
    }
}

@Composable
fun PrepScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    JourneyGate(vm, onEmpty = nav.newJourney) { j, nu, _ ->
        Page(title = l.tr("Visit preparation"), back = nav.home) {
            Spacer(gap(8))
            j.next_appointment?.let {
                CTCard {
                    Text(l.t("nextAppointment").uppercase(), style = MaterialTheme.typography.labelSmall, color = CT.Muted)
                    Text(l.tr(it.title), style = MaterialTheme.typography.titleSmall)
                    Muted(it.scheduled_date)
                }
                Spacer(gap(12))
            }
            val m = nu?.milestone
            if (m != null) {
                CTCard {
                    Text("${l.tr("Get ready")}: ${l.tr(m.title)}".uppercase(), style = MaterialTheme.typography.labelSmall, color = CT.Muted)
                    nu.purpose?.let { Spacer(gap(8)); Text(l.tr(it), style = MaterialTheme.typography.bodyMedium) }
                    nu.what_to_bring?.let { Spacer(gap(8)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CareIcon("review", 16.dp); Text(l.tr(it), style = MaterialTheme.typography.bodySmall) } }
                    nu.fasting?.let { Spacer(gap(8)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CareIcon("food", 16.dp); Text(l.tr(it), color = CT.Amber900, style = MaterialTheme.typography.labelLarge) } }
                    if (nu.questions.isNotEmpty()) {
                        Spacer(gap(8))
                        Text(l.tr("Questions worth asking"), color = CT.Brand, style = MaterialTheme.typography.labelLarge)
                        nu.questions.forEach { Text("• ${l.tr(it)}", style = MaterialTheme.typography.bodySmall, color = CT.Muted) }
                    }
                }
            } else {
                Note(l.tr(if (nu != null) "No next visit preparation is listed. Check your timeline and confirm your next visit with your doctor." else "Visit preparation is loading or unavailable. Check your timeline and confirm instructions with your doctor."), tone = Tone.Blue)
            }
        }
    }
}
