package com.caretrail.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.ConfirmResponse
import com.caretrail.app.data.Flag
import com.caretrail.app.data.Logic
import com.caretrail.app.data.ProposedValue
import kotlinx.coroutines.launch
import java.io.File

private enum class Step { Pick, Extracting, Confirm, Done }

private class PickedFile(val name: String, val mime: String, val bytes: ByteArray)

@Composable
fun UploadScreen(vm: AppViewModel, preselect: String?, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Pick) }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var milestoneId by remember { mutableStateOf(preselect ?: "") }
    var error by remember { mutableStateOf("") }
    var documentId by remember { mutableStateOf("") }
    var duplicateOn by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var extractMessage by remember { mutableStateOf("") }
    val values = remember { mutableStateListOf<Pair<ProposedValue, Boolean>>() }
    var confirmed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConfirmResponse?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun read(uri: Uri, fallbackMime: String?) {
        try {
            var name = "report"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) name = c.getString(i)
            }
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (bytes.size > 15 * 1024 * 1024) { error = "That file is larger than 15 MB."; return }
            file = PickedFile(name, ctx.contentResolver.getType(uri) ?: fallbackMime ?: "application/octet-stream", bytes); error = ""
        } catch (e: Exception) { error = "Could not read that file." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { read(it, null) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { read(it, "image/jpeg") } }

    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        Page(title = l.t("uploadReport"), back = nav.back) {
            Spacer(gap(12))
            when (step) {
                Step.Pick -> {
                    Muted(l.tr("Take a photo of your report or choose a file (photo, PDF or text). You will check the values before anything is saved."))
                    Spacer(gap(12))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            OutlineButton("📷 " + l.tr("Take photo")) {
                                val f = File(ctx.cacheDir, "report_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                                cameraUri = uri; camera.launch(uri)
                            }
                        }
                        Column(Modifier.weight(1f)) { OutlineButton("📄 " + l.tr("Choose file")) { picker.launch("*/*") } }
                    }
                    file?.let { Muted("${it.name} (${it.bytes.size / 1024} KB)", Modifier.padding(top = 8.dp)) }
                    Spacer(gap(12))
                    Box1 {
                        OutlineButton(milestoneId.let { id -> j.milestones.firstOrNull { it.id == id }?.let { l.tr(it.title) } ?: "— ${l.tr("Link to a check-up (optional)")} —" }) { menuOpen = true }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("— ${l.tr("Link to a check-up (optional)")} —") }, onClick = { milestoneId = ""; menuOpen = false })
                            j.milestones.forEach { m -> DropdownMenuItem(text = { Text(l.tr(m.title)) }, onClick = { milestoneId = m.id; menuOpen = false }) }
                        }
                    }
                    if (error.isNotEmpty()) Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Spacer(gap(16))
                    PrimaryButton(l.t("uploadReport"), enabled = file != null) {
                        val f = file ?: return@PrimaryButton
                        step = Step.Extracting; error = ""
                        scope.launch {
                            try {
                                val up = vm.api.upload(j.journey_id, f.name, f.mime, f.bytes, milestoneId.ifEmpty { null })
                                documentId = up.document_id
                                duplicateOn = up.duplicate_of?.uploaded_at?.take(10) ?: ""
                                confirmed = false
                                val ext = vm.api.extract(up.document_id)
                                language = ext.language; provider = ext.provider; extractMessage = ext.message ?: ""
                                values.clear(); ext.proposed.forEach { values.add(it to true) }
                                step = Step.Confirm
                            } catch (e: Exception) { error = e.message ?: l.tr("Upload failed"); step = Step.Pick }
                        }
                    }
                }
                Step.Extracting -> Muted(l.tr("Reading your report…"), Modifier.padding(top = 32.dp))
                Step.Confirm -> {
                    if (duplicateOn.isNotEmpty()) Note("${l.tr("You already uploaded this exact file on")} $duplicateOn. ${l.tr("Saving it again may repeat the same values.")}")
                    Spacer(gap(8))
                    CTCard(border = CT.Amber300, shape = RoundedCornerShape(12.dp)) {
                        SectionTitle("Original report: ${file?.name}")
                        Muted("Compare every value, unit and date with the original before saving. Blurry, conflicting or missing-unit results should not be confirmed.")
                        val f = file
                        if (f != null && f.mime.startsWith("image/")) {
                            val bmp = remember(f) { decodeSampled(f.bytes) }
                            if (bmp != null) Image(bmp.asImageBitmap(), "Original uploaded report", Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp), contentScale = ContentScale.Fit)
                        } else if (f != null && f.mime.startsWith("text/")) {
                            Text(String(f.bytes).take(5000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).background(CT.Ink.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp))
                        } else if (f != null) {
                            Muted("📄 ${f.name} (PDF, preview not shown here)", Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(gap(10))
                    CTCard(shape = RoundedCornerShape(16.dp)) {
                        Text(l.tr("Is this right? Check the values before they go on your timeline.") + if (provider == "offline-parser") " (${l.tr("Read on this device.")})" else " (${l.tr("Read by AI")}: $provider.)" + if (language.isNotEmpty() && language != "en") " ${l.tr("Detected language")}: $language." else "", style = MaterialTheme.typography.bodySmall)
                    }
                    if (extractMessage.isNotEmpty()) { Spacer(gap(8)); Note(l.tr(extractMessage)) }
                    values.forEachIndexed { i, (v, inc) ->
                        Spacer(gap(8))
                        Column(Modifier.fillMaxWidth().background(if (v.confidence < 0.7) CT.Amber50 else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(inc, { values[i] = v to it })
                                Text(l.tr(v.label), style = MaterialTheme.typography.titleSmall)
                            }
                            Text("${Logic.fmtValue(v.value)} ${v.unit}  ·  ${v.observed_on}", style = MaterialTheme.typography.bodyMedium)
                            if (v.reference_range.isNotBlank()) Muted("Ref: ${v.reference_range}")
                            if (v.confidence < 0.7) Text(l.tr("Low confidence - check carefully."), color = CT.Amber900, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(gap(12))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmed, { confirmed = it })
                        Text("I checked the original report and these values match it.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (error.isNotEmpty()) Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall)
                    Spacer(gap(8))
                    PrimaryButton(l.tr("Save confirmed values"), enabled = confirmed) {
                        val chosen = values.filter { it.second }.map { it.first }
                        if (chosen.isEmpty()) { error = l.tr("Nothing selected to save."); return@PrimaryButton }
                        scope.launch {
                            try { result = vm.api.confirm(documentId, milestoneId.ifEmpty { null }, chosen); step = Step.Done; vm.refreshJourney() }
                            catch (e: Exception) { error = e.message ?: "Confirm failed" }
                        }
                    }
                    Spacer(gap(8))
                    OutlineButton(l.tr("Cancel")) { step = Step.Pick; file = null; values.clear() }
                }
                Step.Done -> {
                    val r = result
                    Note("✅ ${r?.observations?.size ?: 0} ${l.tr("values saved to your timeline.")}", tone = Tone.Green)
                    r?.flags?.forEach { f: Flag ->
                        Spacer(gap(8))
                        Note("${l.tr(f.label)}: ${Logic.fmtValue(f.value)} ${f.unit}. ${l.tr(f.message)}")
                    }
                    Spacer(gap(8))
                    Muted(l.tr("Yellow means a result needs doctor review, not a diagnosis or an emergency alert. For warning symptoms, contact your doctor now."))
                    Spacer(gap(16))
                    PrimaryButton(l.t("uploadReport")) { step = Step.Pick; file = null; values.clear(); result = null }
                    Spacer(gap(8))
                    OutlineButton("CareTrail", onClick = nav.home)
                }
            }
        }
    }
}

@Composable
private fun Box1(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

private fun decodeSampled(bytes: ByteArray): android.graphics.Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (o.outWidth / sample > 1600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
