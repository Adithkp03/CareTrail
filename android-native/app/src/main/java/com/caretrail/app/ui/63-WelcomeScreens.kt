package com.caretrail.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.R

/**
 * First-launch welcome screen: a soft mother-and-baby illustration (generated
 * for CareTrail, license-free) over the warm theme background, with the app
 * name, tagline, language choice and Login / Sign Up.
 */
@Composable
fun WelcomeScreen(vm: AppViewModel, onLogin: () -> Unit, onSignup: () -> Unit) {
    val l = rememberL(vm)
    Column(Modifier.fillMaxSize().background(CT.Bg).verticalScroll(rememberScrollState())) {
        Image(
            painterResource(R.drawable.welcome_illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        Column(
            Modifier.fillMaxWidth().offset(y = (-36).dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text("CareTrail", style = MaterialTheme.typography.displayLarge, color = CT.Brand)
            Spacer(gap(8))
            Text(l.t("tagline"), style = MaterialTheme.typography.bodyLarge, color = CT.Muted)
            Spacer(gap(24))
            Text(l.t("language"), style = MaterialTheme.typography.labelMedium, color = CT.Muted)
            Spacer(gap(6))
            LangPicker(l.lang, { vm.translator.setLang(it) })
            Spacer(gap(24))
            PrimaryButton(l.t("login"), onClick = onLogin)
            Spacer(gap(10))
            OutlineButton(l.t("signup"), onClick = onSignup)
        }
    }
}
