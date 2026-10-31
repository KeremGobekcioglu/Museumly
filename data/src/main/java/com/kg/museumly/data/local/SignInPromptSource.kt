package com.kg.museumly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.kg.museumly.domain.SignInPromptSourceInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Its own file, so the backup rules can exclude exactly this one. */
private val Context.signInPromptStore: DataStore<Preferences>
        by preferencesDataStore(name = "sign_in_prompt")

@Singleton
class SignInPromptSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SignInPromptSourceInterface {

    private val askedKey = booleanPreferencesKey("asked")

    override suspend fun hasAsked(): Boolean {
        val prefs: Preferences = context.signInPromptStore.data.first()
        return prefs[askedKey] ?: false
    }

    override suspend fun setAsked() {
        context.signInPromptStore.edit { prefs ->
            prefs[askedKey] = true
        }
    }
}