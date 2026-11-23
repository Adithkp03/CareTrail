package com.caretrail.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.caretrail.app.BuildConfig
import com.caretrail.app.AppViewModel
import com.caretrail.app.JourneyState
import com.caretrail.app.data.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Manual, per-operation opt in. Opening this panel performs no network reads or uploads. */
@Composable
fun StructuredBackupPanel(vm: AppViewModel) {
    val state by vm.journey.collectAsState()
    val jid = (state as? JourneyState.Ready)?.journey?.journey_id ?: return
    val session = vm.store.token ?: return
    val backupApi = remember(session) { Api(BuildConfig.API_URL, { session }) }
    val scope = rememberCoroutineScope()
    var open by remember(jid, session) { mutableStateOf(false) }
    var selected by remember(jid, session) { mutableStateOf(emptySet<String>()) }
    var preview by remember(jid, session) { mutableStateOf<BackupListing?>(null) }
    var localPreview by remember(jid, session) { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember(jid, session) { mutableStateOf(false) }
    var status by remember(jid, session) { mutableStateOf("") }
    var pending by remember(jid, session) { mutableStateOf<BackupRecord?>(null) }
    var action by remember(jid, session) { mutableStateOf("") }
    val operations = remember(jid, session) { mutableMapOf<String, String>() }
    fun stillThisAccount() { check(vm.store.token == session) { "Account changed. Reopen backup from the signed-in account." } }
    OutlineButton(if (open) "Hide optional cloud backup" else "Optional cloud backup and restore") { open = !open }
    if (!open) return
    Note("Manual backup, off by default. Only the modules you select are uploaded to this account's server. Mood entries and unfinished birth text need their own selection. No report files, audio, sessions or full-phone backup. This is a prototype, not a clinical record or guaranteed disaster recovery.")
    StructuredBackup.modules.forEach { (id, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = id in selected, enabled = !busy, onCheckedChange = {
                selected = if (it) selected + id else selected - id
                preview = null; localPreview = emptyMap(); operations.clear()
            })
            Text(label, modifier = Modifier.weight(1f))
        }
    }
    OutlineButton("Preview selected local and server copies", enabled = !busy && selected.isNotEmpty()) {
        busy = true; status = "Reading selected backup metadata..."
        scope.launch {
            try {
                stillThisAccount()
                val locals = selected.mapNotNull { module -> StructuredBackup.local(vm.store, jid, module)?.let { module to it } }.toMap()
                val result = StructuredBackup.preview(backupApi, jid)
                stillThisAccount()
                localPreview = locals; preview = result
                status = "Preview ready. Nothing uploaded or restored. Check each module below."
            } catch (e: Exception) { status = e.message ?: "Preview failed. Local records kept." }
            finally { busy = false }
        }
    }
    val listing = preview
    if (listing != null) selected.forEach { module ->
        val row = listing.records.find { it.module == module }
        val local = localPreview[module]
        Text(StructuredBackup.modules.getValue(module), style = MaterialTheme.typography.titleSmall)
        Text("Phone: ${if (local == null) "no saved copy" else "saved structured record"}. Server: ${when { row == null -> "no copy"; row.deleted -> "deleted at revision ${row.revision}"; else -> "revision ${row.revision}, ${row.updated_at}" }}")
        var showValues by remember(module, listing) { mutableStateOf(false) }
        TextButton(onClick = { showValues = !showValues }) { Text(if (showValues) "Hide record contents" else "Review record contents") }
        if (showValues) {
            Text("Phone copy: ${local ?: "None"}")
            Text("Server copy: ${if (row?.deleted == false) row.data.toString() else "None"}")
        }
        OutlineButton("Upload phone copy: ${StructuredBackup.modules.getValue(module)}", enabled = !busy && local != null) {
            pending = row ?: BackupRecord(module, 0); action = "upload"
        }
        if (row != null && !row.deleted) {
            OutlineButton("Restore missing phone copy", enabled = !busy && local == null) { pending = row; action = "restore" }
            if (local != null) Text("Restore will not replace an existing phone record. It is kept even if the server copy differs.")
            OutlineButton("Delete this server copy", enabled = !busy) { pending = row; action = "delete" }
        }
    }
    if (status.isNotEmpty()) Note(status, tone = Tone.Blue)
    pending?.let { record ->
        AlertDialog(onDismissRequest = { if (!busy) pending = null },
            title = { Text(when (action) { "upload" -> "Upload selected phone record?"; "restore" -> "Restore server record?"; else -> "Delete server backup?" }) },
            text = { Text("${StructuredBackup.modules[record.module]}. ${when (action) {
                "upload" -> "This replaces this module's server copy only if its revision has not changed. Your phone copy stays. Review the record in its form before confirming."
                "restore" -> "This writes to this phone only if no different local copy exists. A restored birth record may switch this journey to postpartum mode."
                else -> "This removes the server payload and keeps a revision tombstone to block stale uploads. Your phone copy stays."
            }}") },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        stillThisAccount()
                        when (action) {
                            "upload" -> {
                                val snapshot = requireNotNull(localPreview[record.module])
                                check(StructuredBackup.local(vm.store, jid, record.module) == snapshot) { "Phone record changed. Preview again before uploading." }
                                StructuredBackup.upload(backupApi, jid, record.module, record.revision, snapshot,
                                    operations.getOrPut(record.module) { UUID.randomUUID().toString() })
                            }
                            "restore" -> StructuredBackup.restore(vm.store, jid, record)
                            else -> StructuredBackup.remove(backupApi, jid, record)
                        }
                        stillThisAccount()
                        status = "${if (action == "upload") "Server acknowledged backup" else if (action == "restore") "Saved on this phone" else "Server copy deleted"}. Preview again before another change."
                        preview = null; localPreview = emptyMap(); operations.clear()
                    } catch (e: Exception) { status = "${e.message ?: "Action failed"}. Local records were not removed. If a request timed out, preview the server before retrying." }
                    finally { busy = false; pending = null }
                }
            }) { Text("Confirm") } })
    }
                    }
