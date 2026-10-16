package com.kg.museumly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore : DataStore<Preferences>
    by preferencesDataStore(name = "provider_turn")

/**
 * Which provider loadMore() tries first in its round-robin. Without this,
 * the pointer only lived in memory on ArtworkRepositoryImpl and reset to 0
 * on every process death — since providers are tried alphabetically, a cold
 * start would always retry the first provider instead of resuming rotation.
 */
@Singleton
class ProviderTurnSource @Inject constructor(
    @ApplicationContext private val context: Context
)
{
    /**
     * One turn per list. listKey comes from ListScope, so the feed keeps its
     * old key ("turn_european") and an era gets its own ("turn_european@1600s").
     */
    private fun turnKey(listKey: String) : Preferences.Key<Int>
    {
        return intPreferencesKey("turn_" + listKey)
    }

    suspend fun getTurn(listKey: String): Int
    {
        val prefs = context.dataStore.data.first()
        return prefs[turnKey(listKey)] ?: 0
    }

    suspend fun setTurn(turnValue: Int, listKey: String)
    {
        context.dataStore.edit {
            prefs ->
                prefs[turnKey(listKey)] = turnValue
        }
    }
}