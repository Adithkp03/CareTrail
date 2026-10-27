package com.caretrail.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.*

/** Patient reviews the exact contents before saving a portable, unencrypted file. */
@Composable
fun ReferralScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var prepared by remember { mutableStateOf<ReferralPackage?>(null) }
        var received by remember { mutableStateOf<ReferralPackage?>(null) }
        var status by remember { mutableStateOf("") }
        var consent by remember { mutableStateOf(false) }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runCatching {
                val p = checkNotNull(prepared)
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(AppJson.encodeToString(ReferralPackage.serializer(), p).toByteArray(Charsets.UTF_8))
                } ?: error("Could not open destination")
            }.onSuccess { status = "Referral saved to your chosen location. It is not encrypted; share only with your chosen clinician." }
                .onFailure { status = "Could not save referral. No records were changed." }
        }
        val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (out.size() <= 1_000_000) { val n = stream.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                    out.toByteArray() } ?: error("No file")
                check(bytes.size <= 1_000_000)
                checkNotNull(Referral.parse(String(bytes, Charsets.UTF_8)))
            }.onSuccess { received = it; status = "Checksum matches. This does not verify the sender or clinical accuracy. No local records were imported." }
                .onFailure { received = null; status = "Invalid, changed, unsupported or oversized referral. No records were changed." }
        }
        Page(title = l.tr("Referral transfer"), back = nav.home) {
            Spacer(gap(8))
            Note(l.tr("This is a patient-carried summary, not an ABDM exchange or an accepted hospital referral. Original reports are not included. Bring them separately."), tone = Tone.Blue)
            Spacer(gap(12))
            PrimaryButton(l.tr("Prepare my referral")) {
                consent = false
                prepared = Referral.build(j.patient.name, vm.store.healthIntake(j.journey_id), vm.store.registration(j.journey_id),
                    vm.store.rhProfile(j.journey_id), vm.store.dangerLogs(j.journey_id), vm.store.birthRecord(j.journey_id),
                    vm.store.growthEntries(j.journey_id), vm.store.vaccineLogs(j.journey_id), vm.store.bpReadings(j.journey_id),
                    vm.store.sugarReadings(j.journey_id), vm.store.highRisk(j.journey_id))
            }
            prepared?.let { p ->
                Spacer(gap(12))
                CTCard { SectionTitle(l.tr("Review before export")); Text(Referral.summaryText(p)) }
                var fullPreview by remember { mutableStateOf(false) }
                OutlineButton(l.tr(if (fullPreview) "Hide complete file contents" else "Show complete file contents")) { fullPreview = !fullPreview }
                if (fullPreview) CTCard { Text(AppJson.encodeToString(ReferralPackage.serializer(), p)) }
                Spacer(gap(8))
                Note(l.tr("The JSON also includes intake history, maternal AND partner test-status fields, Rh/ICT records, danger-sign details and the listed growth, vaccine, BP and sugar logs. It excludes mood data and local image paths. Saving makes a separate copy you must protect and delete yourself."))
                OutlineButton(l.tr(if (consent) "Export acknowledged" else "I have reviewed and want to export")) { consent = !consent }
                PrimaryButton(l.tr("Save unencrypted referral JSON"), enabled = consent) { save.launch("caretrail-referral.json") }
            }
            Spacer(gap(12))
            OutlineButton(l.tr("Preview a received referral (read only)")) { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            received?.let { p -> Spacer(gap(8)); CTCard { SectionTitle(l.tr("Received summary")); Text(Referral.summaryText(p)) } }
            if (status.isNotEmpty()) { Spacer(gap(8)); Note(l.tr(status), tone = Tone.Blue) }
        }
    }
}
