package com.caretrail.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.ApiException
import com.caretrail.app.data.CommunityBlocked
import com.caretrail.app.data.CommunityMsg
import com.caretrail.app.data.CommunityRules
import com.caretrail.app.data.CommunityText
import com.caretrail.app.data.communityBlock
import com.caretrail.app.data.communityBlocks
import com.caretrail.app.data.communityDelete
import com.caretrail.app.data.communityJoin
import com.caretrail.app.data.communityMe
import com.caretrail.app.data.communityMessages
import com.caretrail.app.data.communityPost
import com.caretrail.app.data.communityReport
import com.caretrail.app.data.communityUnblock
import kotlinx.coroutines.launch

/** Always-visible safety note, kept in code and in tests. */
@Composable
fun CommunityNotice(modifier: Modifier = Modifier) {
    Note(CommunityText.notice, modifier, Tone.Blue)
}

/** Stateless join card: guidelines, nickname, consent. Testable without a ViewModel. */
@Composable
fun CommunityJoinContent(
    nickname: String,
    onNickname: (String) -> Unit,
    problem: String?,
    busy: Boolean,
    onJoin: () -> Unit,
) {
    CommunityNotice()
    Spacer(gap(12))
    CTCard {
        SectionTitle(CommunityText.guidelinesTitle)
        Spacer(gap(6))
        CommunityText.guidelines.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = CT.Ink)
            Spacer(gap(4))
        }
        Spacer(gap(10))
        CTField(nickname, onNickname, "Choose a nickname (not your real name or phone number)")
        if (problem != null) { Spacer(gap(6)); Note(problem, tone = Tone.Red) }
        Spacer(gap(10))
        Muted(CommunityText.consentLine)
        Spacer(gap(8))
        PrimaryButton(if (busy) "…" else "Accept and join", enabled = !busy && nickname.isNotBlank(), onClick = onJoin)
    }
}

/** One chat bubble with its moderation actions. */
@Composable
fun CommunityBubble(
    msg: CommunityMsg,
    onReport: (CommunityMsg) -> Unit,
    onBlock: (CommunityMsg) -> Unit,
    onDelete: (CommunityMsg) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.own) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!msg.own) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(CT.BrandSoft), contentAlignment = Alignment.Center) {
                Text(msg.nickname.take(1).uppercase(), color = CT.Brand, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(if (msg.own) CT.Brand else Color.White, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Text(
                if (msg.own) "You" else msg.nickname,
                color = if (msg.own) Color.White.copy(alpha = 0.8f) else CT.Brand,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(gap(2))
            Text(
                if (msg.author_blocked) "You blocked this member. Their messages are hidden." else msg.body,
                color = if (msg.own) Color.White else if (msg.author_blocked) CT.Muted else CT.Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!msg.own && !msg.author_blocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onReport(msg) }) { Text("Report", color = CT.Red700, style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { onBlock(msg) }) { Text("Block ${msg.nickname}", color = CT.Muted, style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (msg.own) {
                TextButton(onClick = { onDelete(msg) }) { Text("Delete", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    Spacer(gap(8))
}

/** Stateless report sheet, shown instead of the composer while a report is open. */
@Composable
fun CommunityReportContent(
    msg: CommunityMsg,
    selected: String?,
    onSelect: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    CTCard {
        SectionTitle("Report this message")
        Spacer(gap(6))
        Muted("\"${msg.body.take(120)}\" - ${msg.nickname}")
        Spacer(gap(8))
        CommunityRules.reportReasons.forEach { reason ->
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected == reason) CT.BrandSoft else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(reason) }
                    .padding(vertical = 8.dp, horizontal = 10.dp),
            ) { Text(CommunityText.reasonLabels.getValue(reason), color = CT.Ink, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(gap(8))
        Muted(CommunityText.reportNote)
        Spacer(gap(8))
        PrimaryButton(if (busy) "…" else "Send report", enabled = !busy && selected != null, onClick = onSend)
        Spacer(gap(4))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = CT.Muted) }
    }
}

/** Stateless chat body: notice, messages, composer. Testable without a ViewModel. */
@Composable
fun CommunityChatContent(
    messages: List<CommunityMsg>,
    suspended: Boolean,
    input: String,
    onInput: (String) -> Unit,
    inputProblem: String?,
    sending: Boolean,
    error: String,
    onSend: () -> Unit,
    onReport: (CommunityMsg) -> Unit,
    onBlock: (CommunityMsg) -> Unit,
    onDelete: (CommunityMsg) -> Unit,
) {
    CommunityNotice()
    Spacer(gap(12))
    if (suspended) {
        Note(CommunityText.suspendedNote, tone = Tone.Red)
        Spacer(gap(8))
    }
    if (messages.isEmpty()) {
        CTCard { Muted(CommunityText.emptyRoom) }
        Spacer(gap(8))
    }
    messages.forEach { CommunityBubble(it, onReport, onBlock, onDelete) }
    if (error.isNotEmpty()) { Note(error, tone = Tone.Red); Spacer(gap(8)) }
    if (!suspended) {
        CTCard {
            CTField(input, onInput, "Write to the room…", singleLine = false)
            if (inputProblem != null) { Spacer(gap(6)); Note(inputProblem, tone = Tone.Red) }
            Spacer(gap(8))
            PrimaryButton(if (sending) "…" else "Send", enabled = !sending && input.isNotBlank(), onClick = onSend)
        }
    }
}

/** Opt-in community room. Join with a nickname, read and post text messages,
 * report or block other members, delete your own messages. */
@Composable
fun CommunityScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var joined by remember { mutableStateOf<Boolean?>(null) }
    var nickname by remember { mutableStateOf("") }
    var suspended by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf<List<CommunityMsg>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reporting by remember { mutableStateOf<CommunityMsg?>(null) }
    var reportReason by remember { mutableStateOf<String?>(null) }
    var confirmBlock by remember { mutableStateOf<CommunityMsg?>(null) }
    var blocked by remember { mutableStateOf<List<CommunityBlocked>>(emptyList()) }

    suspend fun refresh() {
        val list = vm.api.communityMessages()
        messages = list.messages
        blocked = vm.api.communityBlocks().blocked
    }

    LaunchedEffect(Unit) {
        try {
            val me = vm.api.communityMe()
            joined = me.joined
            suspended = me.suspended
            if (me.joined) refresh()
        } catch (e: Exception) {
            error = e.message ?: l.tr("Something went wrong. Please try again.")
            joined = false
        }
    }

    Page(title = l.tr("Community room"), back = nav.home) {
        when {
            joined == null -> Muted(l.tr("Loading…"))
            joined == false -> CommunityJoinContent(
                nickname = nickname,
                onNickname = { nickname = it },
                problem = error.takeIf { it.isNotEmpty() }
                    ?: CommunityRules.nicknameProblem(nickname).takeIf { nickname.isNotBlank() },
                busy = busy,
                onJoin = {
                    CommunityRules.nicknameProblem(nickname)?.let { error = it; return@CommunityJoinContent }
                    busy = true; error = ""
                    scope.launch {
                        try {
                            vm.api.communityJoin(nickname.trim())
                            joined = true
                            error = ""
                            refresh()
                        } catch (e: Exception) { error = e.message ?: l.tr("Could not join. Please try again.") }
                        busy = false
                    }
                },
            )
            else -> {
                if (confirmBlock != null) {
                    val target = confirmBlock!!
                    CTCard {
                        SectionTitle("Block ${target.nickname}?")
                        Spacer(gap(6))
                        Muted(CommunityText.blockNote)
                        Spacer(gap(8))
                        PrimaryButton("Block", onClick = {
                            busy = true
                            scope.launch {
                                try { vm.api.communityBlock(target.author_id); confirmBlock = null; refresh() }
                                catch (e: Exception) { error = e.message ?: "" }
                                busy = false
                            }
                        })
                        TextButton(onClick = { confirmBlock = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = CT.Muted) }
                    }
                    Spacer(gap(8))
                }
                if (reporting != null) {
                    CommunityReportContent(
                        msg = reporting!!,
                        selected = reportReason,
                        onSelect = { reportReason = it },
                        busy = busy,
                        onSend = {
                            val msg = reporting!!; val reason = reportReason ?: return@CommunityReportContent
                            busy = true; error = ""
                            scope.launch {
                                try {
                                    vm.api.communityReport(msg.id, reason)
                                    reporting = null; reportReason = null
                                } catch (e: Exception) {
                                    error = if (e is ApiException && e.code == 429) l.tr("Too many reports today. Please try again tomorrow.")
                                        else e.message ?: l.tr("Could not send the report. Please try again.")
                                }
                                busy = false
                            }
                        },
                        onCancel = { reporting = null; reportReason = null },
                    )
                    Spacer(gap(8))
                }
                CommunityChatContent(
                    messages = messages,
                    suspended = suspended,
                    input = input,
                    onInput = { input = it },
                    inputProblem = CommunityRules.bodyProblem(input).takeIf { input.isNotBlank() },
                    sending = busy,
                    error = error,
                    onSend = {
                        CommunityRules.bodyProblem(input)?.let { error = it; return@CommunityChatContent }
                        val text = input.trim()
                        busy = true; error = ""; input = ""
                        scope.launch {
                            try {
                                vm.api.communityPost(text)
                                refresh()
                            } catch (e: Exception) {
                                input = text
                                error = if (e is ApiException && e.code == 429) l.tr("You are posting too quickly. Wait a few minutes and try again.")
                                    else e.message ?: l.tr("Could not send. Please try again.")
                            }
                            busy = false
                        }
                    },
                    onReport = { reporting = it; reportReason = null },
                    onBlock = { confirmBlock = it },
                    onDelete = { msg ->
                        scope.launch {
                            try { vm.api.communityDelete(msg.id); refresh() }
                            catch (e: Exception) { error = e.message ?: "" }
                        }
                    },
                )
                if (blocked.isNotEmpty()) {
                    Spacer(gap(8))
                    CTCard {
                        SectionTitle(l.tr("Blocked members"))
                        Spacer(gap(4))
                        blocked.forEach { b ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.nickname, color = CT.Ink, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    scope.launch {
                                        try { vm.api.communityUnblock(b.patient_id); refresh() }
                                        catch (e: Exception) { error = e.message ?: "" }
                                    }
                                }) { Text(l.tr("Unblock"), color = CT.Brand, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}
