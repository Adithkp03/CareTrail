package com.caretrail.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.Brief
import com.caretrail.app.data.Langs
import com.caretrail.app.data.Logic
import kotlinx.coroutines.launch

private const val WEB = "https://caretrail-web.vercel.app"

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DoctorScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        var brief by remember { mutableStateOf<Brief?>(null) }
        var email by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        LaunchedEffect(j.journey_id) { try { brief = vm.api.brief(j.journey_id) } catch (_: Exception) {} }
        Page(title = l.t("doctorView"), back = nav.home) {
            Muted("${j.patient.name} · ${j.gestational_age.weeks} ${l.t("weeks")}")
            Spacer(gap(8))
            Note("Your doctor can review this journey. Older review records may not have a verified doctor account.")
            Spacer(gap(12))
            CTCard(border = CT.Amber100, shape = RoundedCornerShape(12.dp)) {
                Text("Invite a doctor who already has a CareTrail doctor account. They sign in separately to review your journey. Typing a name does not count as a doctor review.", style = MaterialTheme.typography.bodySmall)
                Spacer(gap(8))
                CTField(email, { email = it }, "Doctor account email", keyboard = KeyboardType.Email)
                Spacer(gap(8))
                PrimaryButton("Give doctor access", enabled = email.isNotBlank()) {
                    scope.launch {
                        message = try { vm.api.grantClinician(j.journey_id, email.trim()); "Your doctor now has access to this journey. They must sign in with their own account." }
                        catch (e: Exception) { e.message ?: "Grant failed" }
                    }
                }
                Spacer(gap(8))
                Text("Journey code for your doctor:", style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.text.selection.SelectionContainer { Text(j.journey_id, style = MaterialTheme.typography.labelSmall) }
                Spacer(gap(8))
                OutlineButton("Doctor sign-in (opens website)") { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$WEB/clinician?journey=${j.journey_id}"))) }
            }
            if (message.isNotEmpty()) Text(message, color = CT.Brand, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            brief?.let { b ->
                Spacer(gap(12))
                CTCard {
                    Text(l.tr("Summary for your doctor").uppercase(), style = MaterialTheme.typography.labelSmall, color = CT.Muted)
                    Muted("${b.gestational_age.weeks}w${b.gestational_age.plus_days}d · ${l.tr("Due date")} ${b.edd}" +
                        (b.next_appointment?.let { " · ${l.tr("Next")}: ${l.tr(it.title)} (${it.scheduled_date})" } ?: ""))
                    if (b.latest_values.isNotEmpty()) {
                        Spacer(gap(8))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            b.latest_values.forEach { Chip("${l.tr(Langs.testLabel(it.code))}: ${Logic.fmtValue(it.value)} ${it.unit}", CT.Ink.copy(alpha = 0.06f), CT.Ink) }
                        }
                    }
                    b.recent_reports.firstOrNull()?.let { Muted("${l.tr("Latest report")}: ${it.filename} (${l.tr(it.status)})", Modifier.padding(top = 8.dp)) }
                }
            }
            Spacer(gap(16))
            Text(l.t("flagsQueue").uppercase(), style = MaterialTheme.typography.labelSmall, color = CT.Muted)
            Muted(l.tr("Yellow means a result needs doctor review, not a diagnosis or an emergency alert. For warning symptoms, contact your doctor now."), Modifier.padding(top = 4.dp))
            if (j.flags.isEmpty()) {
                Spacer(gap(8)); CTCard(shape = RoundedCornerShape(16.dp)) { Muted(l.t("noFlags")) }
            }
            j.flags.forEach { f ->
                Spacer(gap(8))
                val signed = f.signed_off != null
                Column(Modifier.fillMaxWidth().background(if (signed) CT.Green50 else CT.Amber50, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text("${l.tr(f.label)}: ${Logic.fmtValue(f.value)} ${f.unit}", style = MaterialTheme.typography.titleSmall)
                    Muted(f.observed_on)
                    Text(l.tr(f.message), style = MaterialTheme.typography.bodySmall)
                    f.signed_off?.let { Text("✅ ${l.t("signedOffBy")} ${it.doctor_name}", color = CT.Green800, style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (j.patient.name == "Anjali") {
                Spacer(gap(20))
                OutlineButton(l.tr("Reset demo")) {
                    scope.launch {
                        message = try { vm.api.seedDemo(); vm.refreshJourney(); "✅ " + l.tr("Demo reset - Anjali is back to the starting state.") }
                        catch (e: Exception) { l.tr("Demo reset is only available while signed in as the demo user.") }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = pending
        if (uri != null && data != null) {
            try { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(data.toByteArray()) }; note = l.tr("Your data was downloaded.") }
            catch (e: Exception) { error = l.tr("Could not download your data. Connect and try again.") }
        }
        pending = null
    }
    Page(title = l.tr("Your data"), back = nav.home) {
        Spacer(gap(8))
        CTCard {
            SectionTitle(l.tr("Download your data"))
            Muted(l.tr("A file with your dates, results, report list and doctor notes. Original report files are not included."))
            Spacer(gap(8))
            PrimaryButton(l.tr("Download my data"), enabled = !busy) {
                busy = true; error = ""; note = ""
                scope.launch {
                    try { pending = vm.api.exportData(); saver.launch("caretrail-my-data.json") }
                    catch (e: Exception) { error = l.tr("Could not download your data. Connect and try again.") }
                    busy = false
                }
            }
            if (note.isNotEmpty()) Text(note, color = CT.Brand, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(gap(12))
        CTCard(color = CT.Amber50, border = CT.Amber300) {
            SectionTitle(l.tr("Delete my account"))
            Text(l.tr("This permanently removes your journeys, results, reports and doctor notes from CareTrail. It cannot be undone. Download your data first if you want a copy."), style = MaterialTheme.typography.bodySmall, color = CT.Amber900)
            Spacer(gap(8))
            CTField(confirm, { confirm = it }, l.tr("Type DELETE to confirm"))
            Spacer(gap(8))
            androidx.compose.material3.Button(
                onClick = {
                    busy = true; error = ""
                    scope.launch {
                        try { vm.api.deleteAccount(); vm.logout() } catch (e: Exception) { error = e.message ?: l.tr("Could not delete the account. Try again."); busy = false }
                    }
                }, enabled = !busy && confirm == "DELETE", modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CT.Red800, disabledContainerColor = CT.Red800.copy(alpha = 0.4f)),
            ) { Text(l.tr("Delete my account"), color = androidx.compose.ui.graphics.Color.White) }
        }
        if (error.isNotEmpty()) Text(error, color = CT.Amber900, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun DataSafetyScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    Page(title = l.t("dataSafety"), back = nav.back) {
        Spacer(gap(8))
        listOf(
            l.t("medicalReviewNote"),
            l.tr("CareTrail is a prototype, not a clinical record or emergency service. It keeps pregnancy dates, reports, extracted results, and account details to build your timeline. Reports are stored on the service; recent timeline data may also be cached on this device for offline viewing."),
            l.tr("Cloud AI services may process report text, images, and questions. Phone numbers, email addresses and some name lines are masked in text before a cloud model call, but that masking is not complete protection, especially in images or unusual report formats. Do not upload a real patient report you are not allowed to share."),
            l.tr("AI trace records now keep provider metadata rather than prompts and outputs, and the trace view is scoped to your patient account."),
            l.tr("New uploaded files are stored in the app database with their report record. You can download your data or delete your account from the link below; this is not a backup. Ask the team before entering real health information."),
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp)) }
        var cryptoStatus by remember { mutableStateOf("") }
        Note(l.tr("ABDM is not connected. ABHA entries are unverified references. Referral files are patient-controlled copies, not signed clinical records. There is no end-to-end encryption or compliance certification."))
        Spacer(gap(8))
        OutlineButton(l.tr("Enable device-key encryption for local cache")) {
            cryptoStatus = runCatching { vm.store.migrateCacheToEncrypted() }
                .fold({ "Local cache and session token encrypted with this device's Android Keystore. Original report/image files and exported copies are not covered. Losing the device key can make the cache unreadable." },
                      { "Encryption could not be enabled. Existing data was kept. Retry on this device." })
        }
        if (cryptoStatus.isNotEmpty()) Note(l.tr(cryptoStatus), tone = Tone.Blue)
        Spacer(gap(8))
        Text(l.tr("Download or delete your data"), color = CT.Brand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.androidClick(nav.account))
    }
}
