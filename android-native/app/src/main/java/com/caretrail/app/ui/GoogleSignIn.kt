package com.caretrail.app.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest

class GoogleSignInCancelled : Exception("cancelled")
class GoogleSignInUnavailable(message: String) : Exception(message)

/**
 * Credential Manager wrapper: shows the system Google account picker and returns
 * the Google ID token. The ID token is verified server-side via Supabase; the app
 * never trusts client-side claims. Needs an Android OAuth client (package name +
 * SHA-1 of the signing key) in the same Google Cloud project as the web client;
 * the ID token's audience is the web client ID Supabase already trusts.
 */
private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * [nonce] is the raw nonce sent to Supabase alongside the ID token; Google
 * receives only its SHA-256 hash, so Supabase's nonce check (enabled on the
 * project) can verify the token was minted for this attempt.
 */
suspend fun requestGoogleIdToken(context: Context, serverClientId: String, nonce: String): String {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .setNonce(sha256Hex(nonce))
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
