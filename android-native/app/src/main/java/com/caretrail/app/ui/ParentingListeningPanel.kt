package com.caretrail.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** No remote player, image, cookies or audio is loaded by this panel. */
@Composable
fun ParentingListeningPanel() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    OutlineButton(if (open) "Hide parenting listening" else "Parenting listening: UNICEF India") { open = !open }
    if (!open) return
    CTCard {
        SectionTitle("Parenthood and co-parenting")
        Text("UNICEF Talks, episode 7. A conversation with Soledad and Ravi about sharing parenting and making time for play. For parents and caregivers, not a maternity treatment guide.")
        Spacer(gap(8))
        Muted("12 minutes 26 seconds. English captions checked. Opens the original UNICEF India video on YouTube, not an in-app recording. Internet needed. The player may show ads or offer sign-in; CareTrail requires no purchase or subscription.")
        Spacer(gap(8))
        OutlineButton("Open original video (YouTube)") {
            failed = false
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=q-kQ2f2oYuk"))) }
            catch (_: Exception) { failed = true }
        }
        Muted("Source: UNICEF India, UNICEF Talks collection. Link and playback checked 23 November 2026. This is optional general parenting content, not medical advice. No account or health details are added to the link.")
        if (failed) Note("Could not open the video. Try again when a browser or YouTube app is available.")
    }
}
