package com.kg.museumly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kg.museumly.domain.AuthTokenStoreInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore : DataStore<Preferences> by preferencesDataStore(name= "auth_preferences")

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthTokenStoreInterface {

    private val accessToken = stringPreferencesKey("access_token")
    override val token: Flow<String?> = context.authStore.data.map {
        preferences -> preferences[accessToken]
    }

    override suspend fun save(token: String) {
        context.authStore.edit {
            prefs : MutablePreferences ->
                prefs[accessToken] = token
        }
    }

    override suspend fun clear() {
        context.authStore.edit {
            prefs: MutablePreferences ->
                prefs.remove(accessToken)
        }
    }

}