package com.kg.museumly.presentation.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kg.museumly.presentation.R
private const val TAG: String = "GoogleIdToken"

suspend fun requestGoogleIdToken(context: Context): String?
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
    } catch (e: GetCredentialException) {
        Log.e(TAG, "getCredential failed: ${e.javaClass.simpleName} | ${e.type} | ${e.message}")
        return null
    }

    // Step 4: unwrap the token
    val credential: Credential = response.credential
    if (credential !is CustomCredential) {
        Log.e(TAG, "Unexpected credential class: ${credential.javaClass.simpleName}")
        return null
    }
    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Log.e(TAG, "Unexpected credential type: ${credential.type}")
        return null
    }

    val googleCredential: GoogleIdTokenCredential = try {
        GoogleIdTokenCredential.createFrom(credential.data)
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Could not parse Google ID token", e)
        return null
    }

    val idToken: String = googleCredential.idToken
    Log.d(TAG, "Got ID token for ${googleCredential.id}: $idToken")
    return idToken
}