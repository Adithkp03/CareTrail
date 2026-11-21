package com.caretrail.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caretrail.app.AppViewModel
import com.caretrail.app.BuildConfig
import com.caretrail.app.data.ApiException
import com.caretrail.app.data.Langs
import com.caretrail.app.data.Logic
import kotlinx.coroutines.launch

@Composable
private fun LoginArt(l: L) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF152A54), Color(0xFF3B5AAB), Color(0xFF95ADF0))))
            .padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 64.dp)
    ) {
        // On the narrowest phones (320dp) "Welcome" alone overflows the header
        // and breaks mid-word; step the size down there so it wraps at the word.
        val welcomeStyle = if (maxWidth < 300.dp) {
            MaterialTheme.typography.displayLarge.copy(fontSize = 23.sp)
        } else {
            MaterialTheme.typography.displayLarge
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(80.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFE8EFFF), Color(0xFF839EE8), Color(0xFF24487F))))
        )
        Column {
            Text("CARETRAIL", color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(gap(48))
            Text("Welcome back.", color = Color.White, style = welcomeStyle)
            Spacer(gap(10))
            Text(l.t("tagline"), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, onSignup: () -> Unit) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingGoogleToken by remember { mutableStateOf<String?>(null) }
    var linkPhone by remember { mutableStateOf("") }
    var linkPassword by remember { mutableStateOf("") }
    var newConsent by remember { mutableStateOf(false) }
    val context = LocalContext.current

    suspend fun doLogin(p: String, pw: String) {
        val res = vm.api.login(p, pw)
        vm.onSignedIn(res.token)
    }

    Page {
        LoginArt(l)
        CTCard(Modifier.offset(y = (-32).dp)) {
            Text(l.t("phone"), style = MaterialTheme.typography.labelMedium, color = CT.Muted)
            Spacer(gap(6))
            CTField(phone, { phone = it }, l.t("phone"), keyboard = KeyboardType.Phone)
            Spacer(gap(12))
            Text(l.t("password"), style = MaterialTheme.typography.labelMedium, color = CT.Muted)
            Spacer(gap(6))
            CTField(password, { password = it }, l.t("password"), password = true)
            if (error.isNotEmpty()) { Spacer(gap(8)); Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
            Spacer(gap(16))
            PrimaryButton(l.t("login"), enabled = !busy && phone.isNotBlank() && password.isNotEmpty()) {
                busy = true; error = ""
                scope.launch {
                    try { doLogin(phone.trim(), password) }
                    catch (e: ApiException) {
                        error = if (e.message == "Wrong phone or password") l.t("wrongLogin") else if (e.code == 0) l.t("loginFailed") + " (network)" else l.t("loginFailed")
                        busy = false
                    } catch (e: Exception) { error = l.t("loginFailed"); busy = false }
                }
            }
        }
        if (vm.supabaseAuth.configured) {
            Row(Modifier.offset(y = (-26).dp).fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(CT.CardBorder))
                Text(l.t("orDivider"), color = CT.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(CT.CardBorder))
            }
            OutlineButton(l.t("continueGoogle"), enabled = !busy, modifier = Modifier.offset(y = (-18).dp)) {
                busy = true; error = ""
                scope.launch {
                    try {
                        val nonce = java.util.UUID.randomUUID().toString()
                        val idToken = requestGoogleIdToken(context, BuildConfig.GOOGLE_SERVER_CLIENT_ID, nonce)
                        val sbToken = vm.supabaseAuth.signInWithGoogleIdToken(idToken, nonce)
                        try {
                            val res = vm.api.supabaseExchange(sbToken, consent = false, language = l.lang, create = false)
                            vm.onSignedIn(res.token)
                        } catch (e: ApiException) {
                            if (e.code == 404 && e.message == "no_caretrail_account") {
                                pendingGoogleToken = sbToken; busy = false
                            } else { error = l.t("googleFailed"); busy = false }
                        }
                    } catch (e: GoogleSignInCancelled) { busy = false }
                    catch (e: Exception) { error = l.t("googleFailed"); busy = false }
                }
            }
            pendingGoogleToken?.let { sbToken ->
                CTCard(Modifier.offset(y = (-10).dp)) {
                    Text(l.t("googleChoiceTitle"), style = MaterialTheme.typography.titleMedium)
                    Spacer(gap(6))
                    Text(l.t("googleChoiceBody"), style = MaterialTheme.typography.bodySmall, color = CT.Muted)
                    Spacer(gap(12))
                    CTField(linkPhone, { linkPhone = it }, l.t("phone"), keyboard = KeyboardType.Phone)
                    Spacer(gap(8))
                    CTField(linkPassword, { linkPassword = it }, l.t("password"), password = true)
                    Spacer(gap(10))
                    PrimaryButton(l.t("linkExisting"), enabled = !busy && linkPhone.length >= 6 && linkPassword.isNotEmpty()) {
                        busy = true; error = ""
                        scope.launch {
                            try { val res = vm.api.linkSupabase(sbToken, linkPhone.trim(), linkPassword); vm.onSignedIn(res.token) }
                            catch (e: ApiException) {
                                error = when { e.code == 401 -> l.t("wrongLogin"); e.code == 409 -> e.message ?: l.t("linkFailed"); else -> l.t("linkFailed") }
                                busy = false
                            } catch (e: Exception) { error = l.t("linkFailed"); busy = false }
                        }
                    }
                    Spacer(gap(16))
                    Row(Modifier.clickable { newConsent = !newConsent }, verticalAlignment = Alignment.Top) {
                        Checkbox(newConsent, { newConsent = it })
                        Text(l.t("consent"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                    }
                    Spacer(gap(10))
                    OutlineButton(l.t("createNewAccount"), enabled = !busy && newConsent) {
                        busy = true; error = ""
                        scope.launch {
                            try { val res = vm.api.supabaseExchange(sbToken, consent = true, language = l.lang, create = true); vm.onSignedIn(res.token) }
                            catch (e: Exception) { error = l.t("googleFailed"); busy = false }
                        }
                    }
                }
            }
        }
        Column(Modifier.offset(y = (-20).dp)) {
            OutlineButton(l.t("tryDemo"), enabled = !busy) {
                busy = true; error = ""
                scope.launch {
                    try {
                        try { doLogin("9000000001", "demo1234") }
                        catch (e: ApiException) { vm.api.seedDemo(); doLogin("9000000001", "demo1234") }
                    } catch (e: Exception) { error = l.t("demoFailed"); busy = false }
                }
            }
            Spacer(gap(16))
            Text(l.t("signup"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSignup).padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(gap(16))
            Text(l.t("language"), style = MaterialTheme.typography.labelLarge, color = CT.Muted)
            Spacer(gap(6))
            LangPicker(l.lang, { vm.translator.setLang(it) })
            if (l.lang !in Langs.reviewed) { Spacer(gap(8)); Note(l.t("translationNote")) }
        }
    }
}

@Composable
fun SignupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Page(title = l.t("signup"), back = onBack) {
        Spacer(gap(16))
        CTField(name, { name = it }, l.t("yourName"))
        Spacer(gap(12))
        CTField(phone, { phone = it }, l.t("phone"), keyboard = KeyboardType.Phone)
        Spacer(gap(12))
        CTField(password, { password = it }, l.t("password"), password = true)
        if (password.isNotEmpty() && password.length < 8) Muted("8+", Modifier.padding(top = 4.dp))
        Spacer(gap(12))
        Text(l.t("language"), style = MaterialTheme.typography.labelLarge, color = CT.Muted)
        Spacer(gap(6))
        LangPicker(l.lang, { vm.translator.setLang(it) })
        Spacer(gap(12))
        CTCard(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.clickable { consent = !consent }, verticalAlignment = Alignment.Top) {
                Checkbox(consent, { consent = it })
                Text(l.t("consent"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (error.isNotEmpty()) { Spacer(gap(8)); Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
        Spacer(gap(16))
        PrimaryButton(l.t("signup"), enabled = !busy && consent && name.isNotBlank() && phone.length >= 6 && password.length >= 8) {
            busy = true; error = ""
            scope.launch {
                try {
                    val res = vm.api.signup(name.trim(), phone.trim(), password, l.lang, consent)
                    vm.onSignedIn(res.token)
                } catch (e: ApiException) {
                    error = if (e.message?.startsWith("An account") == true) l.t("phoneExists") else l.t("signupFailed"); busy = false
                } catch (e: Exception) { error = l.t("signupFailed"); busy = false }
            }
        }
    }
}

@Composable
fun NewJourneyScreen(vm: AppViewModel, onDone: () -> Unit) {
    val l = rememberL(vm)
    val scope = rememberCoroutineScope()
    var lmp by remember { mutableStateOf("") }
    var edd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Page(title = l.t("startJourney")) {
        Muted(l.t("journeyIntro"))
        Spacer(gap(20))
        Text(l.t("lmpLabel"), style = MaterialTheme.typography.labelLarge)
        Spacer(gap(6))
        DateField(lmp, { lmp = it; if (it.isNotEmpty()) edd = "" }, l.t("lmpLabel"))
        Spacer(gap(16))
        Text(l.t("eddLabel"), style = MaterialTheme.typography.labelLarge)
        Spacer(gap(6))
        DateField(edd, { edd = it; if (it.isNotEmpty()) lmp = "" }, l.t("eddLabel"))
        if (error.isNotEmpty()) { Spacer(gap(8)); Text(error, color = CT.Red700, style = MaterialTheme.typography.bodySmall) }
        Spacer(gap(20))
        PrimaryButton(l.t("startJourney"), enabled = !busy) {
            if (lmp.isEmpty() && edd.isEmpty()) { error = l.t("giveOneDate"); return@PrimaryButton }
            busy = true; error = ""
            scope.launch {
                try {
                    vm.api.createJourney(lmp.ifEmpty { null }, edd.ifEmpty { null })
                    vm.refreshJourney(); onDone()
                } catch (e: Exception) { error = e.message ?: l.t("couldNotStart"); busy = false }
            }
        }
    }
}
