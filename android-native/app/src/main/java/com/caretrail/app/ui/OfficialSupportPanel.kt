package com.caretrail.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.caretrail.app.data.OfficialSupport

/** Local text only until the user chooses to open an official source. */
@Composable
fun OfficialSupportPanel(l: L) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    OutlineButton(if (open) "Hide government support" else "Government support: official guidance") { open = !open }
    if (!open) return
    Note("India: English guidance in this first version, not an eligibility decision. Rules and local services can change. Ask your ASHA, Anganwadi worker or government facility. No application, payment or document upload happens in CareTrail.")
    OfficialSupport.resources.forEach { resource ->
        Spacer(gap(8))
        CTCard {
            SectionTitle(resource.title)
            Text(resource.summary)
            Spacer(gap(8))
            Text(resource.access)
            TextButton(onClick = {
                error = false
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))) }
                catch (_: Exception) { error = true }
            }) { Text(l.tr("Open official source (browser)")) }
        }
    }
    Muted("Sources checked ${OfficialSupport.reviewedOn}. Links need internet and open outside CareTrail. We do not send your account or health details with them.")
    if (error) Note("No browser opened. Ask your local health worker to help access the official scheme page.")
}
