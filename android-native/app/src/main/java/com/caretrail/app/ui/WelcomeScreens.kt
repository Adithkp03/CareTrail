package com.caretrail.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.R
import kotlinx.coroutines.delay

/** Welcome photos: Wikimedia Commons, see android-native/NOTICE.md for credits. */
private val WELCOME_PHOTOS = listOf(
    R.drawable.welcome_1,
    R.drawable.welcome_2,
    R.drawable.welcome_3,
)

/**
 * First-launch welcome screen: slowly cycling mother-and-baby photos over a warm
 * background, with the app name, tagline, language choice and Login / Sign Up.
 */
@Composable
fun WelcomeScreen(vm: AppViewModel, onLogin: () -> Unit, onSignup: () -> Unit) {
    val l = rememberL(vm)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            index = (index + 1) % WELCOME_PHOTOS.size
        }
    }
    Column(Modifier.fillMaxSize().background(CT.Bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            Crossfade(targetState = index, animationSpec = tween(1200), label = "welcomePhoto") { i ->
                Image(
                    painterResource(WELCOME_PHOTOS[i]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
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
            Spacer(gap(20))
            Text(
                "Photos: Basile Morin (CC BY-SA 4.0), UK DFID (CC BY-SA 2.0), USAID Ethiopia (CC BY 2.0), via Wikimedia Commons",
                style = MaterialTheme.typography.labelSmall,
                color = CT.Muted,
            )
        }
    }
}
