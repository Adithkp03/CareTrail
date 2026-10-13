package com.caretrail.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.AskAnswer
import com.caretrail.app.data.CareContent
import com.caretrail.app.data.Explanation
import com.caretrail.app.data.Langs
import com.caretrail.app.data.Logic
import com.caretrail.app.data.Milestone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MilestoneScreen(vm: AppViewModel, id: String, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<Milestone?>(null) }
    var offline by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<Explanation?>(null) }
    var error by remember { mutableStateOf("") }
    var listenMsg by remember { mutableStateOf("") }
    var askOpen by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<AskAnswer?>(null) }
    var asking by remember { mutableStateOf(false) }
    var scheduleDate by remember { mutableStateOf("") }

    suspend fun load() {
        try { val r = vm.repo.milestone(id); m = r.value; offline = r.offline } catch (e: Exception) { error = e.message ?: "Failed" }
        explanation = vm.repo.explanation(id, l.lang)
    }
    LaunchedEffect(id, l.lang) { load() }

    fun play(bytes: ByteArray, ext: String) {
        val f = File.createTempFile("ct_audio", ".$ext", ctx.cacheDir); f.writeBytes(bytes)
        MediaPlayer().apply { setDataSource(f.path); prepare(); setOnCompletionListener { release(); f.delete() }; start() }
    }

    suspend fun recordAndAsk() {
        listenMsg = l.tr("Listening… speak your question now")
        val f = File.createTempFile("ct_q", ".m4a", ctx.cacheDir)
        @Suppress("DEPRECATION") val rec = MediaRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC); rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); rec.setOutputFile(f.path); rec.prepare(); rec.start()
            delay(4000); rec.stop(); rec.release()
            asking = true
            val res = vm.api.askVoice(f.readBytes(), "question.m4a", "audio/mp4", l.lang)
            question = res.transcript; answer = AskAnswer(res.answer, res.urgent)
            res.audio_base64?.let { play(android.util.Base64.decode(it, android.util.Base64.DEFAULT), "wav") }
            listenMsg = ""
        } catch (e: Exception) {
            runCatching { rec.release() }
            listenMsg = l.tr("Voice questions are not available right now. You can type your question instead.")
        } finally { asking = false; f.delete() }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) scope.launch { recordAndAsk() } else listenMsg = l.tr("Voice questions are not available right now. You can type your question instead.") }

    val ms = m
    Page(back = nav.back, backLabel = l.t("appName")) {
        if (ms == null) {
            if (error.isNotEmpty()) Note(error, tone = Tone.Red) else Muted(l.t("loading"))
            return@Page
        }
        if (offline) Note("Offline - showing the last loaded copy.", Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CareIcon(Logic.iconFor(ms), 26.dp)
                Text(l.tr(ms.title), style = MaterialTheme.typography.titleLarge)
            }
            val (bg, fg) = when (ms.status) {
                "done" -> CT.Green100 to CT.Green800; "now" -> CT.Brand to Color.White
                "upcoming" -> CT.Blue100 to CT.Blue800; else -> CT.Ink.copy(alpha = 0.1f) to CT.Muted
            }
            Chip(l.t(ms.status) + if (ms.overdue) " · ${l.t("overdue")}" else "", bg, fg)
        }
        Spacer(gap(4))
        Muted(buildString {
            append("${l.t("window")}: ${ms.windowLabel} ${l.t("weeks")}")
            ms.scheduled_date?.let { append(" · 📅 $it") }; ms.completed_at?.let { append(" · ✓ $it") }
        })
        ms.completion_level?.let { Text(l.tr(Logic.LEVEL_TEXT[it]), color = CT.Brand, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)) }
        ms.signoff?.let {
            Spacer(gap(12))
            Column(Modifier.fillMaxWidth().background(CT.Green50, RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text("✅ ${l.t("signedOffBy")} ${it.doctor_name}", style = MaterialTheme.typography.labelLarge)
                Muted(l.t("prototypeReview"))
                if (it.note.isNotBlank()) Text(l.tr(it.note), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(gap(12))
        CTCard {
            SectionTitle(l.t("whyNow"))
            Muted(l.tr("This care step is planned for this stage of pregnancy, using the dates you entered. Your doctor may choose a different date.") + " ${ms.windowLabel} ${l.t("weeks")}.")
            Spacer(gap(10))
            OutlineButton(l.t("uploadReport")) { nav.upload(ms.id) }
        }
        if (ms.key in setOf("first_consultation", "baseline_bloods", "nt_scan")) {
            Spacer(gap(12))
            CTCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CareIcon("pregnancy", 20.dp); SectionTitle(l.t("earlyCare")) }
                Spacer(gap(8))
                CareContent.steps.forEach { s ->
                    Text("• ${l.care(s.title)}", color = if (s.urgent) CT.Red800 else CT.Ink, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (s.urgent) CT.Red50 else CT.BrandSoft, RoundedCornerShape(10.dp)).padding(8.dp))
                }
                if (ms.key == "first_consultation" || ms.key == "baseline_bloods") {
                    Spacer(gap(10)); SectionTitle(l.t("firstVisitChecks"))
                    CareContent.tests.forEach { Text("• ${l.care(it.text)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        if (ms.prep_notes.isNotBlank()) {
            Spacer(gap(12)); CTCard { SectionTitle(l.t("prepNotes")); Muted(l.tr(ms.prep_notes)) }
        }
        explanation?.let { Spacer(gap(12)); CTCard { Text(it.text, style = MaterialTheme.typography.bodyMedium) } }
        Spacer(gap(12))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                OutlineButton("🔊 ${l.t("listen")}") {
                    listenMsg = ""
                    scope.launch {
                        try { play(vm.api.explanationAudio(id, l.lang), "wav") }
                        catch (e: Exception) { listenMsg = l.tr("Audio is not available right now. Please try again.") }
                    }
                }
            }
            Column(Modifier.weight(1f)) { OutlineButton("🎙️ ${l.t("ask")}") { askOpen = !askOpen } }
        }
        if (listenMsg.isNotEmpty()) Muted(listenMsg, Modifier.padding(top = 4.dp))
        if (askOpen) {
            Spacer(gap(8))
            CTCard {
                CTField(question, { question = it }, l.tr("Ask about this check-up…"))
                Spacer(gap(8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        PrimaryButton(if (asking) "…" else l.t("ask"), enabled = !asking && question.isNotBlank()) {
                            asking = true; answer = null
                            scope.launch {
                                try { answer = vm.api.ask(question, l.lang) }
                                catch (e: Exception) { answer = AskAnswer(e.message ?: l.tr("Something went wrong. Please try again.")) }
                                asking = false
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        OutlineButton("🎙️ ${l.tr("Ask by voice")}", enabled = !asking) {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) scope.launch { recordAndAsk() }
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                answer?.let { a ->
                    Spacer(gap(10))
                    Column(Modifier.fillMaxWidth().background(if (a.urgent) CT.Red50 else CT.Ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text(a.answer, color = if (a.urgent) CT.Red800 else CT.Ink, style = MaterialTheme.typography.bodyMedium)
                        a.note?.let { Muted(l.tr(it)) }
                        a.citation?.let { Muted("${l.tr("Source")}: ${l.tr(it.source)}") }
                    }
                }
            }
        }
        if (ms.observations.isNotEmpty()) {
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.t("results"))
                ms.observations.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Muted(l.tr(Langs.testLabel(o.code))); Text("${Logic.fmtValue(o.value)} ${o.unit}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (ms.documents.isNotEmpty()) {
            Spacer(gap(12))
            CTCard { SectionTitle(l.t("reports")); ms.documents.forEach { Muted("📄 ${it.filename} (${l.tr(it.status)})", Modifier.padding(top = 4.dp)) } }
        }
        if (ms.status != "done") {
            Spacer(gap(20))
            PrimaryButton(l.t("markDone")) {
                scope.launch { try { vm.api.completeMilestone(id); vm.refreshJourney(); load() } catch (e: Exception) { error = e.message ?: "Failed" } }
            }
            Spacer(gap(8))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { DateField(scheduleDate, { scheduleDate = it }, l.t("schedule")) }
                Column(Modifier.weight(0.6f)) {
                    OutlineButton(l.t("schedule"), enabled = scheduleDate.isNotEmpty()) {
                        scope.launch { try { vm.api.scheduleMilestone(id, scheduleDate); vm.refreshJourney(); load() } catch (e: Exception) { error = e.message ?: "Failed" } }
                    }
                }
            }
            if (error.isNotEmpty()) Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall)
        }
    }
}
