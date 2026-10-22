package com.kg.museumly.presentation.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kg.museumly.presentation.R
private const val TAG: String = "GoogleIdToken"
sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    data object NoGoogleAccount : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data object Failed : GoogleIdTokenResult
}
suspend fun requestGoogleIdToken(context: Context): GoogleIdTokenResult
{
    // Step 2: build the request
    val webClientId: String = context.getString(R.string.google_web_client_id)
    val googleIdOption : GetGoogleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false)
        .build()

    val request: GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    // Step 3: make the call
    val credentialManager: CredentialManager = CredentialManager.create(context)

    val response: GetCredentialResponse = try {
        credentialManager.getCredential(context, request)
    } catch (e: NoCredentialException) {
        Log.w(TAG, "No Google account available: ${e.message}")
        return GoogleIdTokenResult.NoGoogleAccount
    } catch (e: GetCredentialCancellationException) {
        return GoogleIdTokenResult.Cancelled
    } catch (e: GetCredentialException) {
        Log.e(TAG, "getCredential failed: ${e.javaClass.simpleName} | ${e.type} | ${e.message}")
        return GoogleIdTokenResult.Failed
    }

    // Step 4: unwrap the token
    val credential: Credential = response.credential
    if (credential !is CustomCredential) {
        Log.e(TAG, "Unexpected credential class: ${credential.javaClass.simpleName}")
        return GoogleIdTokenResult.Failed
    }
    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Log.e(TAG, "Unexpected credential type: ${credential.type}")
        return GoogleIdTokenResult.Failed
    }

    val googleCredential: GoogleIdTokenCredential = try {
        GoogleIdTokenCredential.createFrom(credential.data)
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Could not parse Google ID token", e)
        return GoogleIdTokenResult.Failed
    }

    val idToken: String = googleCredential.idToken
    Log.d(TAG, "Got ID token for ${googleCredential.id}: $idToken")
    return GoogleIdTokenResult.Success(idToken = idToken)
}