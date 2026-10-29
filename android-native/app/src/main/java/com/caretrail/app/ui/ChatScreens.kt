package com.caretrail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.AppJson
import com.caretrail.app.data.ChatHistory
import com.caretrail.app.data.ChatMsg
import com.caretrail.app.data.DoctorQuestionList
import kotlinx.coroutines.launch

/**
 * Sprint 6 general-help assistant. General information and app help only - the
 * backend refuses individualized medical advice in code before any model runs.
 */
@Composable
fun AssistantScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var ack by remember { mutableStateOf(vm.store.chatAcknowledged) }
    var messages by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var offline by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    fun cache() {
        try { vm.store.putCache("chat_history", AppJson.encodeToString(ChatHistory.serializer(), ChatHistory(messages))) } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        vm.store.getCache("chat_history")?.let {
            try { messages = AppJson.decodeFromString(ChatHistory.serializer(), it).items; offline = true } catch (_: Exception) {}
        }
        try {
            messages = vm.api.assistantHistory().items
            offline = false
            cache()
        } catch (_: Exception) { /* cached copy stays */ }
    }

    Page(title = l.tr("Help assistant"), back = nav.home) {
        Note(l.tr("General information and app help only - not medical advice. For anything about your own health, ask your doctor."))
        Spacer(gap(12))
        if (!ack) {
            CTCard {
                Text(l.tr("Before you start"), style = MaterialTheme.typography.titleSmall)
                Spacer(gap(6))
                Text(
                    l.tr("This assistant answers general questions about pregnancy care and helps you use CareTrail - appointments, reminders, reports and features. It does not give medical advice, and it never decides anything about your health. Your questions are sent to CareTrail's server and processed by an AI language service with personal details removed. The conversation is stored so you can continue it later, and you can clear it any time."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(gap(10))
                PrimaryButton(l.tr("I understand")) { vm.store.chatAcknowledged = true; ack = true }
            }
        } else {
            if (offline) { Note(l.tr("Offline - showing your last loaded conversation."), Modifier.padding(bottom = 8.dp)) }
            if (messages.isEmpty()) {
                CTCard {
                    Muted(l.tr("Try asking:"))
                    Spacer(gap(6))
                    listOf("When is my next appointment?", "What is the anomaly scan?", "How do I upload a report?").forEach { q ->
                        TextButton(onClick = { input = q }) { Text(l.tr(q), color = CT.Brand, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(gap(12))
            }
            messages.forEach { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 300.dp)
                            .background(
                                when {
                                    mine -> CT.Brand
                                    m.urgent -> CT.Red50
                                    else -> Color.White
                                },
                                RoundedCornerShape(16.dp),
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            m.text,
                            color = when {
                                mine -> Color.White
                                m.urgent -> CT.Red800
                                else -> CT.Ink
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!mine && m.suggest_doctor) {
                            Spacer(gap(6))
                            TextButton(onClick = nav.doctorQuestions) {
                                Text(l.tr("Ask your doctor →"), color = CT.Brand, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(gap(8))
            }
            if (sending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) { Text("…", color = CT.Muted) }
                }
                Spacer(gap(8))
            }
            if (error.isNotEmpty()) { Note(error, tone = Tone.Red); Spacer(gap(8)) }
            CTCard {
                CTField(input, { input = it }, l.tr("Ask a question…"), singleLine = false)
                Spacer(gap(8))
                PrimaryButton(if (sending) "…" else l.tr("Send"), enabled = !sending && input.isNotBlank()) {
                    val q = input.trim()
                    input = ""; sending = true; error = ""
                    messages = messages + ChatMsg(role = "user", text = q)
                    scope.launch {
                        try {
                            val r = vm.api.assistantChat(q, l.lang)
                            messages = messages + ChatMsg(id = r.message_id, role = "assistant", text = r.answer, source = r.source, urgent = r.urgent, suggest_doctor = r.suggest_doctor)
                            offline = false
                            cache()
                        } catch (e: Exception) {
                            error = e.message ?: l.tr("Something went wrong. Please try again.")
                            messages = messages.dropLast(1)
                            input = q
                        }
                        sending = false
                    }
                }
            }
            if (messages.isNotEmpty()) {
                Spacer(gap(8))
                if (confirmClear) {
                    Note(l.tr("Clear the whole conversation? This cannot be undone."))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { OutlineButton(l.tr("Cancel")) { confirmClear = false } }
                        Column(Modifier.weight(1f)) {
                            PrimaryButton(l.tr("Clear chat")) {
                                scope.launch {
                                    try { vm.api.clearAssistantHistory() } catch (_: Exception) {}
                                    messages = emptyList(); cache(); confirmClear = false
                                }
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(l.tr("Clear chat history"), color = CT.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Sprint 6: patient -> doctor questions. Every question is written and sent
 * explicitly; it reaches only clinicians the patient granted access to.
 */
@Composable
fun AskDoctorScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var data by remember { mutableStateOf<DoctorQuestionList?>(null) }
        var input by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }

        suspend fun load() {
            try { data = vm.api.doctorQuestions(j.journey_id); message = "" }
            catch (e: Exception) { message = e.message ?: l.tr("Could not load questions.") }
        }
        LaunchedEffect(j.journey_id) { load() }

        Page(title = l.tr("Ask your doctor"), back = nav.home) {
            Note(
                l.tr("Not for emergencies. For warning signs - bleeding, leaking fluid, severe headache, blurred vision, reduced baby movements, severe abdominal pain, fever - call your doctor or go to the hospital now. Your doctor checks questions when they can; no reply time is promised."),
                tone = Tone.Red,
            )
            Spacer(gap(12))
            val linked = data?.linked_doctors ?: emptyList()
            if (data != null && linked.isEmpty()) {
                CTCard {
                    Text(l.tr("No doctor linked yet"), style = MaterialTheme.typography.titleSmall)
                    Spacer(gap(6))
                    Muted(l.tr("Your question needs somewhere to go. Link your doctor first - they sign in with their own verified account and see only what you share."))
                    Spacer(gap(10))
                    OutlineButton(l.tr("Open doctor section"), onClick = nav.doctor)
                }
                Spacer(gap(12))
            }
            if (linked.isNotEmpty()) {
                CTCard {
                    Text(l.tr("Send a question to") + " " + linked.joinToString(", ") { it.name }, style = MaterialTheme.typography.titleSmall)
                    Spacer(gap(6))
                    Muted(l.tr("Only your linked doctor sees this. Describe your concern in your own words; they reply through CareTrail."))
                    Spacer(gap(8))
                    CTField(input, { input = it }, l.tr("Type your question…"), singleLine = false)
                    Spacer(gap(8))
                    PrimaryButton(if (sending) "…" else l.tr("Send to my doctor"), enabled = !sending && input.isNotBlank()) {
                        val q = input.trim()
                        sending = true; message = ""
                        scope.launch {
                            try {
                                vm.api.askDoctor(j.journey_id, q)
                                input = ""
                                load()
                            } catch (e: Exception) { message = e.message ?: l.tr("Could not send. Please try again.") }
                            sending = false
                        }
                    }
                }
                Spacer(gap(12))
            }
            if (message.isNotEmpty()) { Note(message, tone = Tone.Red); Spacer(gap(8)) }
            val items = data?.items ?: emptyList()
            if (items.isNotEmpty()) {
                SectionTitle(l.tr("Your questions"))
                Spacer(gap(8))
                items.forEach { q ->
                    CTCard {
                        Text(q.question, style = MaterialTheme.typography.bodyMedium)
                        Muted(q.created_at.take(10))
                        Spacer(gap(8))
                        if (q.answered && q.answer != null) {
                            Column(Modifier.fillMaxWidth().background(CT.Green50, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text(q.answer, color = CT.Green800, style = MaterialTheme.typography.bodyMedium)
                                Muted("${q.answerer_name ?: l.tr("Your doctor")} · ${q.answered_at?.take(10) ?: ""}")
                            }
                        } else {
                            Text(l.tr("Waiting for your doctor's reply"), color = CT.Amber900, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(gap(8))
                }
            } else if (data != null && linked.isNotEmpty()) {
                CTCard { Muted(l.tr("No questions yet. Your conversation with your doctor will appear here.")) }
            }
        }
    }
}
