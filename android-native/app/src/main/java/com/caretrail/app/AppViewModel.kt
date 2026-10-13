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
import com.caretrail.app.data.Translator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface JourneyState {
    data object Loading : JourneyState
    data object Empty : JourneyState
    data class Ready(val journey: Journey, val nextUp: NextUp?, val offline: Boolean) : JourneyState
    data class Failed(val message: String) : JourneyState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val _loggedIn = MutableStateFlow(store.token != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    val api = Api(BuildConfig.API_URL, { store.token }, { expireSession() })
    val repo = Repo(api, store)
    val translator = Translator(api, store, viewModelScope)

    private val _journey = MutableStateFlow<JourneyState>(JourneyState.Loading)
    val journey: StateFlow<JourneyState> = _journey

    private val _inbox = MutableStateFlow<Inbox?>(null)
    val inbox: StateFlow<Inbox?> = _inbox

    private fun expireSession() {
        store.token = null
        _loggedIn.value = false
    }

    fun onSignedIn(token: String) {
        store.token = token
        _journey.value = JourneyState.Loading
        _loggedIn.value = true
    }

    fun logout() {
        store.clearAccount()
        _journey.value = JourneyState.Loading
        _inbox.value = null
        _loggedIn.value = false
    }

    fun refreshJourney() {
        viewModelScope.launch {
            try {
                val res = repo.myJourney()
                val j = res.value
                if (j == null) { _journey.value = JourneyState.Empty; return@launch }
                // Show the timeline immediately; next-up fills in after.
                _journey.value = JourneyState.Ready(j, (_journey.value as? JourneyState.Ready)?.nextUp, res.offline)
                if (!store.langChosen && Langs_valid(j.patient.language)) translator.setLang(j.patient.language, explicit = false)
                val nu = repo.nextUp(j.journey_id)
                _journey.value = JourneyState.Ready(j, nu, res.offline)
            } catch (e: SessionExpired) {
                // handled by expireSession
            } catch (e: ApiException) {
                _journey.value = JourneyState.Failed(e.message ?: "Failed to load")
            } catch (e: Exception) {
                _journey.value = JourneyState.Failed(e.message ?: "Failed to load")
            }
        }
    }

    fun setInbox(i: Inbox?) { _inbox.value = i }

    @Suppress("FunctionName")
    private fun Langs_valid(code: String) = com.caretrail.app.data.Langs.valid(code)
}
