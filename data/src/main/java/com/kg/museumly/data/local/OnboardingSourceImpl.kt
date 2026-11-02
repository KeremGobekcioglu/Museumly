package com.kg.museumly.data.local
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.kg.museumly.domain.OnboardingSourceInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
/** Its own file, so the backup rules can exclude exactly this one. */
private val Context.onboardingStore: DataStore<Preferences>
        by preferencesDataStore(name = "onboarding")

@Singleton
class OnboardingSource @Inject constructor(
    @ApplicationContext private val context: Context
) : OnboardingSourceInterface {

    private val doneKey = booleanPreferencesKey("done")

    override suspend fun hasCompleted(): Boolean {
        val prefs: Preferences = context.onboardingStore.data.first()
        return prefs[doneKey] ?: false
    }

    override suspend fun setCompleted() {
        context.onboardingStore.edit { prefs ->
            prefs[doneKey] = true
        }
    }
}