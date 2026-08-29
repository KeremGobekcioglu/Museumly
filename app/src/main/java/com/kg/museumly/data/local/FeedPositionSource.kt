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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feed_position")

@Singleton
class FeedPositionSource @Inject constructor(
    @ApplicationContext private val context: Context
)
{
    private val frontierKey = intPreferencesKey("frontier")

    suspend fun getFrontier(): Int
    {
        val prefs : Preferences = context.dataStore.data.first()
        val stored: Int = prefs[frontierKey] ?: return 0
        return stored
    }
    /**
     * Highest page index the user has ever reached. Not "where they left
     * off" — if they swiped to 30 then browsed back to 5 and closed, we
     * want 30. Going backwards is free; going forwards is the thing worth
     * remembering.
     */
    suspend fun setFrontier(position: Int)
    {
        context.dataStore.edit {
            prefs ->
                val current : Int? = prefs[frontierKey]
                if(current == null || position > current)
                {
                    prefs[frontierKey] = position
                }
        }
    }
}