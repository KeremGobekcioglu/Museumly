package com.kg.museumly.data

import android.util.Log
import com.kg.museumly.domain.ArtworkPrefetcher
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.FeedWarmup
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Why its own scope: onboarding's ViewModel is cleared the moment the user
 * finishes, and that would cancel a load still in flight.
 *
 * Failures are swallowed on purpose. The feed's own load and its Retry state
 * take over, so the warm-up is never the only way the first page can arrive.
 */
@Singleton
class FeedWarmupImpl @Inject constructor(
    private val repository: ArtworkRepository,
    private val prefetcher: ArtworkPrefetcher
) : FeedWarmup {

    private companion object {
        // First run has no saved section, and the feed falls back to EUROPEAN.
        val FIRST_RUN_SECTION: Section = Section.EUROPEAN
        const val PREFETCH_COUNT = 3
    }

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Only touched under the lock in start().
    private var job: Job? = null

    override fun start() {
        synchronized(this) {
            if (job != null) {
                return
            }
            job = scope.launch {
                try {
                    repository.loadMore(FIRST_RUN_SECTION)
                    val first: List<Artwork> =
                        repository.artworks(FIRST_RUN_SECTION).first().take(PREFETCH_COUNT)
                    prefetcher.prefetch(first.map { it.imageUrl })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d("WARMUP", "warm-up failed, the feed will load it: ${e.message}")
                }
            }
        }
    }

    override suspend fun awaitIfRunning() {
        val running: Job = synchronized(this) { job } ?: return
        running.join()
    }
}