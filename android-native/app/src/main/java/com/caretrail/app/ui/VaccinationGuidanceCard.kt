package com.caretrail.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.caretrail.app.data.VaccinationGuidance

/** Read-only: no due chip, completion, scheduling, eligibility or dose mutation callback. */
@Composable
fun VaccinationGuidanceCard(guidance: VaccinationGuidance, l: L) {
    if (guidance.kind != "clinician_discussion") return
    var expanded by remember(guidance) { mutableStateOf(false) }
    CTCard(color = CT.BrandSoft, border = CT.BrandSoft, padding = 12) {
        Text(l.tr(guidance.title), style = MaterialTheme.typography.titleSmall)
        Spacer(gap(4))
        Text(l.tr(guidance.timing), style = MaterialTheme.typography.bodySmall)
        Spacer(gap(4))
        Muted(l.tr(guidance.summary))
        TextButton(onClick = { expanded = !expanded }) {
            Text(l.tr(if (expanded) "Hide vaccine guidance" else "Read about Td, boosters and Tdap"))
        }
        if (expanded) {
            listOf(guidance.course, guidance.booster, guidance.tdap, guidance.records).forEach { text ->
                Text(l.tr(text), style = MaterialTheme.typography.bodySmall)
                Spacer(gap(8))
            }
        }
    }
}
