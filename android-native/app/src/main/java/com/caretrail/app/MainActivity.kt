package com.caretrail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caretrail.app.ui.CT
import com.caretrail.app.ui.CareIcon
import com.caretrail.app.ui.CareTrailTheme
import com.caretrail.app.ui.Routes
import com.caretrail.app.ui.rememberL
import com.caretrail.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CareTrailTheme { CareTrailApp(vm) } }
    }
}

@Composable
fun CareTrailApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val loggedIn by vm.loggedIn.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Session changes drive navigation: sign-in lands on Home, sign-out/expiry on Login.
    LaunchedEffect(loggedIn) {
        if (loggedIn) nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        else nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }

    fun go(r: String) = nav.navigate(r) { launchSingleTop = true }
    fun tab(r: String) = nav.navigate(r) { popUpTo(Routes.HOME); launchSingleTop = true }
    val callbacks = Nav(
        home = { tab(Routes.HOME) }, timeline = { tab(Routes.TIMELINE) }, reminders = { tab(Routes.REMINDERS) },
        movements = { tab(Routes.MOVEMENTS) }, care = { go(Routes.CARE) }, prep = { go(Routes.PREP) },
        doctor = { go(Routes.DOCTOR) }, account = { go(Routes.ACCOUNT) }, dataSafety = { go(Routes.DATA_SAFETY) },
        intake = { go(Routes.INTAKE) }, registration = { go(Routes.REGISTRATION) }, rh = { go(Routes.RH) }, danger = { go(Routes.DANGER) },
        birth = { go(Routes.BIRTH) }, growth = { go(Routes.GROWTH) }, vaccines = { go(Routes.VACCINES) }, vault = { go(Routes.VAULT) }, facilities = { go(Routes.FACILITIES) },
        referral = { go(Routes.REFERRAL) },
        wellness = { go(Routes.WELLNESS) }, recovery = { go(Routes.RECOVERY) }, mindCheck = { go(Routes.MINDCHECK) },
        bpLog = { go(Routes.BPLOG) }, familyPlanning = { go(Routes.FAMILYPLAN) }, mindfulness = { go(Routes.MINDFUL) },
        newJourney = { nav.navigate(Routes.NEW_JOURNEY) { popUpTo(Routes.HOME) { inclusive = true } } },
        back = { if (!nav.popBackStack()) tab(Routes.HOME) },
        milestone = { go("milestone/$it") },
        upload = { go(if (it == null) "upload" else "upload?milestone=$it") },
    )
    val showBar = route != null && route.substringBefore('?').substringBefore('/') in setOf("home", "timeline", "reminders", "movements", "care", "prep", "doctor", "account", "datasafety", "milestone", "upload", "intake", "registration", "rh", "danger", "birth", "growth", "vaccines", "vault", "facilities", "wellness", "recovery", "mindcheck", "bplog", "familyplan", "mindful", "referral")

    Column(Modifier.fillMaxSize().background(CT.Bg).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = if (loggedIn) Routes.HOME else Routes.LOGIN) {
                composable(Routes.LOGIN) { LoginScreen(vm) { nav.navigate(Routes.SIGNUP) } }
                composable(Routes.SIGNUP) { SignupScreen(vm) { nav.popBackStack() } }
                composable(Routes.NEW_JOURNEY) { NewJourneyScreen(vm) { nav.navigate(Routes.HOME) { popUpTo(Routes.NEW_JOURNEY) { inclusive = true } } } }
                composable(Routes.HOME) { HomeScreen(vm, callbacks) }
                composable(Routes.TIMELINE) { TimelineScreen(vm, callbacks) }
                composable(Routes.REMINDERS) { RemindersScreen(vm, callbacks) }
                composable(Routes.MOVEMENTS) { MovementsScreen(vm, callbacks) }
                composable(Routes.CARE) { CareScreen(vm, callbacks) }
                composable(Routes.PREP) { PrepScreen(vm, callbacks) }
                composable(Routes.DOCTOR) { DoctorScreen(vm, callbacks) }
                composable(Routes.ACCOUNT) { AccountScreen(vm, callbacks) }
                composable(Routes.DATA_SAFETY) { DataSafetyScreen(vm, callbacks) }
                composable(Routes.INTAKE) { HealthIntakeScreen(vm, callbacks) }
                composable(Routes.REGISTRATION) { RegistrationScreen(vm, callbacks) }
                composable(Routes.RH) { RhScreen(vm, callbacks) }
                composable(Routes.DANGER) { DangerSignsScreen(vm, callbacks) }
                composable(Routes.BIRTH) { BirthEntryScreen(vm, callbacks) }
                composable(Routes.GROWTH) { GrowthScreen(vm, callbacks) }
                composable(Routes.VACCINES) { VaccinesScreen(vm, callbacks) }
                composable(Routes.VAULT) { VaultScreen(vm, callbacks) }
                composable(Routes.REFERRAL) { ReferralScreen(vm, callbacks) }
                composable(Routes.FACILITIES) { FacilitiesScreen(vm, callbacks) }
                composable(Routes.WELLNESS) { WellnessScreen(vm, callbacks) }
                composable(Routes.RECOVERY) { RecoveryCareScreen(vm, callbacks) }
                composable(Routes.MINDCHECK) { MindCheckScreen(vm, callbacks) }
                composable(Routes.BPLOG) { BpSugarScreen(vm, callbacks) }
                composable(Routes.FAMILYPLAN) { FamilyPlanningScreen(vm, callbacks) }
                composable(Routes.MINDFUL) { MindfulnessScreen(vm, callbacks) }
                composable(Routes.MILESTONE, listOf(navArgument("id") { type = NavType.StringType })) {
                    MilestoneScreen(vm, it.arguments?.getString("id") ?: "", callbacks)
                }
                composable(Routes.UPLOAD, listOf(navArgument("milestone") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    UploadScreen(vm, it.arguments?.getString("milestone"), callbacks)
                }
            }
        }
        if (showBar && loggedIn) BottomBar(vm, route, callbacks)
    }
}

@Composable
private fun BottomBar(vm: AppViewModel, route: String?, nav: Nav) {
    val l = rememberL(vm)
    val tabs = listOf(
        Triple(Routes.HOME, "home" to l.tr("Home"), nav.home), Triple(Routes.TIMELINE, "review" to l.tr("Timeline"), nav.timeline),
        Triple(Routes.REMINDERS, "bell" to l.tr("Reminders"), nav.reminders), Triple(Routes.MOVEMENTS, "movement" to l.tr("Movements"), nav.movements),
    )
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (r, iconLabel, go) ->
            val selected = route?.substringBefore('?') == r
            Column(
                Modifier.weight(1f).background(if (selected) CT.Navy else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab, onClick = go).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CareIcon(iconLabel.first, 19.dp, tint = if (selected) Color.White else CT.Ink.copy(alpha = 0.75f))
                Text(iconLabel.second, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else CT.Ink.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}
