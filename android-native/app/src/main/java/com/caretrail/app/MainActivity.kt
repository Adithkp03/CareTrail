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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Closed-app appointment reminders: periodic local check, opt-in and generic text.
        ReminderWorker.schedule(applicationContext)
        setContent { CareTrailTheme { CareTrailApp(vm, intent.getBooleanExtra("daily_reminders", false)) } }
    }
}

@Composable
fun CareTrailApp(vm: AppViewModel, openDailyReminders: Boolean = false) {
    val nav = rememberNavController()
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val loggedIn by vm.loggedIn.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val authNotice = remember { SnackbarHostState() }
    val noticeScope = rememberCoroutineScope()
    val l = rememberL(vm)
    val googleSuccess: () -> Unit = {
        noticeScope.launch { authNotice.showSnackbar(l.tr("Google sign-in successful")) }
        Unit
    }

    // Session changes drive navigation: sign-in lands on Home, sign-out/expiry on Login.
    LaunchedEffect(loggedIn) {
        if (loggedIn) nav.navigate(
            if (openDailyReminders) "daily_reminders"
            else if (DailyReminderScheduler.setupDone(appCtx)) Routes.HOME
            else "daily_setup",
        ) { popUpTo(0) { inclusive = true } }
        else nav.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
    }

    fun go(r: String) = nav.navigate(r) { launchSingleTop = true }
    fun tab(r: String) = nav.navigate(r) { popUpTo(Routes.HOME); launchSingleTop = true }
    val callbacks = Nav(
        home = { tab(Routes.HOME) }, timeline = { tab(Routes.TIMELINE) }, reminders = { tab(Routes.REMINDERS) },
        movements = { tab(Routes.MOVEMENTS) }, care = { go(Routes.CARE) }, prep = { go(Routes.PREP) },
        doctor = { go(Routes.DOCTOR) }, account = { tab(Routes.ACCOUNT) }, accountData = { go(Routes.ACCOUNT_DATA) }, dataSafety = { go(Routes.DATA_SAFETY) },
        intake = { go(Routes.INTAKE) }, registration = { go(Routes.REGISTRATION) }, rh = { go(Routes.RH) }, danger = { go(Routes.DANGER) },
        birth = { go(Routes.BIRTH) }, growth = { go(Routes.GROWTH) }, vaccines = { go(Routes.VACCINES) }, vault = { go(Routes.VAULT) }, facilities = { go(Routes.FACILITIES) },
        referral = { go(Routes.REFERRAL) },
        wellness = { go(Routes.WELLNESS) }, recovery = { go(Routes.RECOVERY) }, mindCheck = { go(Routes.MINDCHECK) },
        bpLog = { go(Routes.BPLOG) }, familyPlanning = { go(Routes.FAMILYPLAN) }, mindfulness = { go(Routes.MINDFUL) },
        chat = { go(Routes.CHAT) }, community = { go(Routes.COMMUNITY) }, doctorQuestions = { go(Routes.DOCTORQ) },
        newJourney = { nav.navigate(Routes.NEW_JOURNEY) { popUpTo(Routes.HOME) { inclusive = true } } },
        back = { if (!nav.popBackStack()) tab(Routes.HOME) },
        milestone = { go("milestone/$it") },
        upload = { go(if (it == null) "upload" else "upload?milestone=$it") },
    )
    val showBar = route != null && route.substringBefore('?').substringBefore('/') in setOf("home", "timeline", "reminders", "movements", "care", "prep", "doctor", "account", "account_data", "datasafety", "milestone", "upload", "intake", "registration", "rh", "danger", "birth", "growth", "vaccines", "vault", "facilities", "wellness", "recovery", "mindcheck", "bplog", "familyplan", "mindful", "referral", "chat", "community", "doctorq")

    Box(Modifier.fillMaxSize().background(CT.Bg).statusBarsPadding()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = if (loggedIn) Routes.HOME else Routes.WELCOME) {
                composable("daily_setup") { DailyRemindersScreen(vm, true) { nav.navigate(Routes.HOME) { popUpTo("daily_setup") { inclusive = true } } } }
                composable("daily_reminders") { DailyRemindersScreen(vm, false) { if (!nav.popBackStack()) go(Routes.HOME) } }
                composable(Routes.WELCOME) { WelcomeScreen(vm, onLogin = { go(Routes.LOGIN) }, onSignup = { go(Routes.SIGNUP) }) }
                composable(Routes.LOGIN) { LoginScreen(vm, onGoogleSuccess = googleSuccess) { nav.navigate(Routes.SIGNUP) } }
                composable(Routes.SIGNUP) { SignupScreen(vm, onGoogleSuccess = googleSuccess) { nav.popBackStack() } }
                composable(Routes.NEW_JOURNEY) { NewJourneyScreen(vm) { nav.navigate(Routes.HOME) { popUpTo(Routes.NEW_JOURNEY) { inclusive = true } } } }
                composable(Routes.HOME) { HomeScreen(vm, callbacks) }
                composable(Routes.TIMELINE) { TimelineScreen(vm, callbacks) }
                composable(Routes.REMINDERS) { RemindersScreen(vm, callbacks) { go("daily_reminders") } }
                composable(Routes.MOVEMENTS) { MovementsScreen(vm, callbacks) }
                composable(Routes.CARE) { CareScreen(vm, callbacks) }
                composable(Routes.PREP) { PrepScreen(vm, callbacks) }
                composable(Routes.DOCTOR) { DoctorScreen(vm, callbacks) }
                composable(Routes.ACCOUNT) { ProfileScreen(vm, callbacks) }
                composable(Routes.ACCOUNT_DATA) { AccountScreen(vm, callbacks) }
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
                composable(Routes.CHAT) { AssistantScreen(vm, callbacks) }
                composable(Routes.COMMUNITY) { CommunityScreen(vm, callbacks) }
                composable(Routes.DOCTORQ) { AskDoctorScreen(vm, callbacks) }
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
    GoogleAuthNotice(authNotice, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
internal fun BottomBar(vm: AppViewModel, route: String?, nav: Nav) {
    val l = rememberL(vm)
    val tabs = listOf(
        Triple(Routes.HOME, "home" to l.tr("Home"), nav.home), Triple(Routes.TIMELINE, "review" to l.tr("Timeline"), nav.timeline),
        Triple(Routes.REMINDERS, "bell" to l.tr("Reminders"), nav.reminders), Triple(Routes.COMMUNITY, "community" to l.tr("Community"), nav.community),
        Triple(Routes.ACCOUNT, "profile" to l.tr("Profile"), nav.account),
    )
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        tabs.forEach { (r, iconLabel, go) ->
            val selected = route?.substringBefore('?') == r
            Column(
                Modifier.weight(1f).background(if (selected) CT.Brand else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab, onClick = go).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CareIcon(iconLabel.first, 18.dp, tint = if (selected) Color.White else CT.Ink.copy(alpha = 0.75f))
                Text(iconLabel.second, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (iconLabel.second.length > 9) 9.sp else 10.5.sp, letterSpacing = 0.sp, lineHeight = if (iconLabel.second.length > 9) 11.5.sp else 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = if (selected) Color.White else CT.Ink.copy(alpha = 0.75f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp))
            }
        }
    }
}


/** Explicit colors keep the app typography from darkening snackbar text. */
@Composable
fun GoogleAuthNotice(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier) { data ->
        Snackbar(containerColor = CT.Brand, contentColor = Color.White) {
            Text(data.visuals.message, color = Color.White)
        }
    }
}
