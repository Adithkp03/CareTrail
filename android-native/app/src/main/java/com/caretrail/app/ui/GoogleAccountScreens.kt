package com.caretrail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.BuildConfig
import com.caretrail.app.data.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GoogleUiState(val flow: GoogleAccountFlow) {
    var busy by mutableStateOf(false)
    var pending by mutableStateOf(false)
    var error by mutableStateOf("")
}

@Composable
fun rememberGoogleUi(vm: AppViewModel, onSuccess: () -> Unit): GoogleUiState {
    val currentSuccess by rememberUpdatedState(onSuccess)
    return remember(vm) {
        GoogleUiState(GoogleAccountFlow(vm.api::supabaseExchange, vm.api::linkSupabase) { session ->
            vm.onSignedIn(session.token, session.patient_id)
            currentSuccess()
        })
    }
}

@Composable
fun GoogleEntry(vm: AppViewModel, state: GoogleUiState, otherBusy: Boolean) {
    val l = rememberL(vm)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlineButton(if (state.busy) l.tr("Signing in...") else l.t("continueGoogle"), enabled = !state.busy && !otherBusy) {
        state.busy = true; state.error = ""
        scope.launch {
            try {
                val nonce = java.util.UUID.randomUUID().toString()
                val idToken = requestGoogleIdToken(context, BuildConfig.GOOGLE_SERVER_CLIENT_ID, nonce)
                val token = vm.supabaseAuth.signInWithGoogleIdToken(idToken, nonce)
                state.flow.begin(token, l.lang)
                state.pending = state.flow.needsAccount
            } catch (e: GoogleSignInCancelled) { /* No error or success on picker dismissal. */ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.error = l.t("googleFailed") }
            finally { state.busy = false }
        }
    }
    if (state.error.isNotEmpty()) Text(state.error, color = CT.Red700, style = MaterialTheme.typography.bodySmall)
}

/** This is the actual account-creation/link screen, also rendered in UI tests. */
@Composable
fun GoogleAccountPanel(
    l: L, busy: Boolean, error: String,
    onLanguage: (String) -> Unit, onCreate: (Boolean) -> Unit, onLink: (String, String) -> Unit, onBack: () -> Unit,
) {
    var linking by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Page(title = if (linking) l.tr("Link your CareTrail account") else l.tr("Create your CareTrail account"),
        back = { if (!busy) { if (linking) { linking = false; password = "" } else onBack() } },
        backLabel = l.tr("Back")) {
        Spacer(gap(8))
        Note(l.tr("Google account selected. Finish below to sign in to CareTrail."), tone = Tone.Blue)
        Spacer(gap(20))
        if (linking) {
            Text(l.tr("Use your existing phone number and password. Your care history stays with that account."), style = MaterialTheme.typography.bodyMedium)
            Spacer(gap(16))
            CTField(phone, { phone = it }, l.t("phone"), keyboard = KeyboardType.Phone)
            Spacer(gap(12))
            CTField(password, { password = it }, l.t("password"), password = true)
        } else {
            Text(l.tr("New to CareTrail? No phone number or password is needed with Google."), style = MaterialTheme.typography.bodyMedium)
            Spacer(gap(16))
            Text(l.t("language"), style = MaterialTheme.typography.labelLarge, color = CT.Muted)
            Spacer(gap(6))
            LangPicker(l.lang, onLanguage)
            Spacer(gap(12))
            CTCard {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(consent, { if (!busy) consent = it }, enabled = !busy)
                    Text(l.t("consent"), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(top = 12.dp).clickable(enabled = !busy) { consent = !consent })
                }
            }
        }
        if (error.isNotEmpty()) { Spacer(gap(12)); Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
        Spacer(gap(20))
        if (linking) {
            PrimaryButton(if (busy) l.tr("Signing in...") else l.t("linkExisting"), enabled = !busy && phone.trim().length >= 6 && password.isNotEmpty()) { onLink(phone, password) }
        } else {
            PrimaryButton(if (busy) l.tr("Signing in...") else l.tr("Create account with Google"), enabled = !busy && consent) { onCreate(consent) }
            Spacer(gap(20))
            Text(l.tr("Already use CareTrail with a phone number?"), style = MaterialTheme.typography.bodySmall, color = CT.Muted)
            Spacer(gap(6))
            OutlineButton(l.t("linkExisting"), enabled = !busy) { linking = true }
        }
    }
}

@Composable
fun GooglePendingScreen(vm: AppViewModel, state: GoogleUiState) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    fun runAction(action: suspend () -> Unit) {
        state.busy = true; state.error = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: ApiException) {
                state.error = when (e.code) {
                    401 -> l.tr("Sign-in could not be verified. Check your phone and password, or go back and select Google again.")
                    409 -> l.tr("These accounts cannot be linked. Sign in to your existing CareTrail account or choose another Google account.")
                    else -> l.t("googleFailed")
                }
            } catch (e: Exception) { state.error = l.t("googleFailed") }
            finally { state.busy = false }
        }
    }
    androidx.activity.compose.BackHandler {
        if (!state.busy) { state.flow.clear(); state.pending = false; state.error = "" }
    }
    GoogleAccountPanel(l, state.busy, state.error,
        onLanguage = { vm.translator.setLang(it) },
        onCreate = { consent -> runAction { state.flow.create(consent, l.lang) } },
        onLink = { phone, password -> runAction { state.flow.link(phone, password) } },
        onBack = { state.flow.clear(); state.pending = false; state.error = "" })
}

@Composable
fun AuthDivider(l: L) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(CT.CardBorder))
        Text(l.t("orDivider"), color = CT.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(CT.CardBorder))
    }
}
