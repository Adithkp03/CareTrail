package com.caretrail.app.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInCancelled : Exception("cancelled")
class GoogleSignInUnavailable(message: String) : Exception(message)

/**
 * Credential Manager wrapper: shows the system Google account picker and returns
 * the Google ID token. The ID token is verified server-side via Supabase; the app
 * never trusts client-side claims. Needs a Google Cloud Android OAuth client whose
 * SHA-1 matches the signing key, whitelisted on the Supabase Google provider; the
 * token is requested against the web client ID Supabase already trusts.
 */
suspend fun requestGoogleIdToken(context: Context, serverClientId: String): String {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val manager = CredentialManager.create(context)
    val response = try {
        manager.getCredential(context, request)
    } catch (e: GetCredentialCancellationException) {
        throw GoogleSignInCancelled()
    } catch (e: GetCredentialException) {
        throw GoogleSignInUnavailable(e.message ?: "Google sign-in unavailable")
    }
    val cred = response.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(cred.data).idToken
    }
    throw GoogleSignInUnavailable("Unexpected credential type")
}
