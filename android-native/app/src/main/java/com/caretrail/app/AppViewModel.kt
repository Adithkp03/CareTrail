package com.caretrail.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caretrail.app.data.Api
import com.caretrail.app.data.ApiException
import com.caretrail.app.data.Inbox
import com.caretrail.app.data.Journey
import com.caretrail.app.data.NextUp
import com.caretrail.app.data.Repo
import com.caretrail.app.data.SessionExpired
import com.caretrail.app.data.Store
import com.caretrail.app.data.SupabaseAuth
import com.caretrail.app.data.Translator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

sealed interface JourneyState {
    data class Loading(val takingLonger: Boolean = false) : JourneyState
    data object Empty : JourneyState
    data class Ready(val journey: Journey, val nextUp: NextUp?, val offline: Boolean) : JourneyState
    data class Failed(val message: String) : JourneyState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val _loggedIn = MutableStateFlow(store.token != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    val api = Api(BuildConfig.API_URL, { store.token }, { expireSession() })
    val supabaseAuth = SupabaseAuth(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val repo = Repo(api, store)
    val translator = Translator(api, store, viewModelScope)

    private val journeyLoader = JourneyLoader(
        viewModelScope, { store.token }, { repo.myJourney() }, { repo.nextUp(it) },
        onReady = { j ->
            if (!store.langChosen && Langs_valid(j.patient.language)) translator.setLang(j.patient.language, explicit = false)
        },
    )
    val journey: StateFlow<JourneyState> = journeyLoader.state

    private val _inbox = MutableStateFlow<Inbox?>(null)
    val inbox: StateFlow<Inbox?> = _inbox

    private fun expireSession() {
        // Network session expiry is not a deliberate logout: keep local reminders running.
        journeyLoader.reset()
        store.token = null
        _loggedIn.value = false
    }

    fun onSignedIn(token: String, patientId: String = "") {
        journeyLoader.reset()
        DailyReminderScheduler.onExplicitSignIn(getApplication(), patientId)
        store.token = token
        _loggedIn.value = true
    }

    fun logout() {
        journeyLoader.reset()
        DailyReminderScheduler.deactivate(getApplication())
        store.clearAccount()
        _inbox.value = null
        _loggedIn.value = false
    }

    fun refreshJourney(force: Boolean = false) { journeyLoader.refresh(force) }

    fun setInbox(i: Inbox?) { _inbox.value = i }

    @Suppress("FunctionName")
    private fun Langs_valid(code: String) = com.caretrail.app.data.Langs.valid(code)
}

/** Single in-flight Home load, including fresh installs without a cached journey. */
internal class JourneyLoader(
    private val scope: CoroutineScope,
    private val token: () -> String?,
    private val load: suspend () -> Repo.Loaded<Journey?>,
    private val nextUp: suspend (String) -> NextUp?,
    private val onReady: (Journey) -> Unit = {},
) {
    private val mutable = MutableStateFlow<JourneyState>(JourneyState.Loading())
    val state: StateFlow<JourneyState> = mutable
    private var job: Job? = null
    private var generation = 0
    private var activeToken: String? = null

    fun reset() {
        generation++
        job?.cancel()
        job = null
        activeToken = null
        mutable.value = JourneyState.Loading()
    }

    fun refresh(force: Boolean = false) {
        val session = token() ?: return
        if (!force && job?.isActive == true && activeToken == session) return
        val previousSession = activeToken
        job?.cancel()
        val version = ++generation
        activeToken = session
        if (previousSession != session || mutable.value !is JourneyState.Ready) mutable.value = JourneyState.Loading()
        fun current() = version == generation && token() == session
        job = scope.launch {
            val slowNotice = launch {
                delay(8_000)
                if (current() && mutable.value is JourneyState.Loading) mutable.value = JourneyState.Loading(takingLonger = true)
            }
            try {
                // One budget for BOTH required requests, not 90 seconds per read.
                val result = withTimeout(45_000) { load() }
                if (!current()) return@launch
                val journey = result.value
                if (journey == null) {
                    mutable.value = JourneyState.Empty
                    return@launch
                }
                mutable.value = JourneyState.Ready(journey, null, result.offline)
                onReady(journey)
                slowNotice.cancel()
                // Optional next-up cannot hide an already loaded Home screen.
                val upcoming = try { withTimeout(15_000) { nextUp(journey.journey_id) } }
                catch (_: TimeoutCancellationException) { null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                if (current()) mutable.value = JourneyState.Ready(journey, upcoming, result.offline)
            } catch (_: TimeoutCancellationException) {
                if (current()) mutable.value = JourneyState.Failed("Your care timeline is taking too long to load. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpired) {
                if (current()) mutable.value = JourneyState.Failed("Your session expired. Please sign in again.")
            } catch (e: Exception) {
                if (current()) mutable.value = JourneyState.Failed(e.message ?: "Failed to load")
            } finally {
                slowNotice.cancel()
            }
        }
    }
}
