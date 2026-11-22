package com.caretrail.app.ui

import com.caretrail.app.data.ApiException
import com.caretrail.app.data.TokenResponse

/** Google/Supabase credentials alone never establish a CareTrail session. */
class GoogleAccountFlow(
    private val exchange: suspend (String, Boolean, String, Boolean) -> TokenResponse,
    private val linkAccount: suspend (String, String, String) -> TokenResponse,
    private val signedIn: (TokenResponse) -> Unit,
) {
    private var pendingToken: String? = null
    val needsAccount: Boolean get() = pendingToken != null

    private fun finish(response: TokenResponse) {
        require(response.token.isNotBlank() && response.patient_id.isNotBlank()) { "Incomplete CareTrail session" }
        pendingToken = null
        signedIn(response)
    }

    suspend fun begin(token: String, language: String) {
        clear()
        try {
            finish(exchange(token, false, language, false))
        } catch (e: ApiException) {
            if (e.code == 404 && e.message == "no_caretrail_account") pendingToken = token
            else throw e
        }
    }

    suspend fun create(consent: Boolean, language: String) {
        require(consent) { "Consent required" }
        val token = checkNotNull(pendingToken)
        finish(exchange(token, true, language, true))
    }

    suspend fun link(phone: String, password: String) {
        require(phone.trim().length >= 6 && password.isNotEmpty())
        finish(linkAccount(checkNotNull(pendingToken), phone.trim(), password))
    }

    fun clear() { pendingToken = null }
}
