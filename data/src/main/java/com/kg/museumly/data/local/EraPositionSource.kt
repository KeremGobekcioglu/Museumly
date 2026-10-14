package com.kg.museumly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kg.museumly.domain.EraPositionSourceInterface
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
/** Its own file, so no era key can ever overwrite a feed key. */
private val Context.eraPositionStore: DataStore<Preferences>
        by preferencesDataStore(name = "era_position")

@Singleton
class EraPositionSource @Inject constructor(
    @ApplicationContext private val context: Context
) : EraPositionSourceInterface {

    /** "frontier_european@1600s", "frontier_all@1600s": reads as the list it belongs to. */
    private fun frontierKey(era: Era, section: Section?): Preferences.Key<Int> {
        var sectionPart: String = "all"
        if (section != null) {
            sectionPart = section.id
        }
        return intPreferencesKey("frontier_" + sectionPart + "@" + era.id)
    }

    override suspend fun getFrontier(era: Era, section: Section?): Int {
        val prefs: Preferences = context.eraPositionStore.data.first()
        val stored: Int? = prefs[frontierKey(era, section)]
        if (stored == null) {
            return 0
        }
        return stored
    }

    override suspend fun setFrontier(era: Era, section: Section?, index: Int) {
        context.eraPositionStore.edit { prefs ->
            val key: Preferences.Key<Int> = frontierKey(era, section)
            val current: Int? = prefs[key]
            if (current == null || index > current) {
                prefs[key] = index
            }
        }
    }
}