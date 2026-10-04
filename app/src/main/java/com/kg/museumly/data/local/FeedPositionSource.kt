package com.kg.museumly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kg.museumly.domain.domain.FeedPositionSourceInterface
import com.kg.museumly.domain.model.Section
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feed_position")

@Singleton
class FeedPositionSource @Inject constructor(
    @ApplicationContext private val context: Context
) : FeedPositionSourceInterface
{
    private val lastSectionKey = stringPreferencesKey("last_section")
    private fun frontierKey(section: Section): Preferences.Key<Int>
    {
        return intPreferencesKey("frontier_" + section.id)
    }
    // In-memory, not persisted to DataStore: scoped to this process's
    // lifetime, not forever. A user who hasn't opened the app in weeks has
    // likely forgotten the gesture exists and should see the hint again —
    // once per app launch is enough to not be annoying within a session.
    private var hasInspectedThisSession: Boolean = false

    override suspend fun getLastSection() : Section
    {
        // open datastore
        val prefs = context.dataStore.data.first()
        // first launch empty so return european
        val storedId: String = prefs[lastSectionKey] ?: return Section.EUROPEAN
        val match : Section? = Section.entries.firstOrNull {
            it.id == storedId
        }
        return match ?: Section.EUROPEAN
    }

    override suspend fun setLastSection(section: Section)
    {
        context.dataStore.edit {
            prefs ->
                prefs[lastSectionKey] = section.id
        }
    }

    override suspend fun getFrontier(section: Section): Int
    {
        val prefs: Preferences = context.dataStore.data.first()
        val stored: Int = prefs[frontierKey(section)] ?: return 0
        return stored
    }

    override suspend fun setFrontier(section: Section, position: Int)
    {
        context.dataStore.edit { prefs ->
            val key: Preferences.Key<Int> = frontierKey(section)
            val current: Int? = prefs[key]
            if (current == null || position > current) {
                prefs[key] = position
            }
        }
    }

    override fun hasInspected(): Boolean = hasInspectedThisSession

    override fun setInspected()
    {
        hasInspectedThisSession = true
    }
}