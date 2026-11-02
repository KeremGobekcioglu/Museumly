package com.kg.museumly.data

import com.kg.museumly.domain.ArtworkPrefetcher
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.model.Section
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedWarmupImplTest {

    private lateinit var repository: ArtworkRepository
    private lateinit var prefetcher: ArtworkPrefetcher
    private lateinit var warmup: FeedWarmupImpl

    @Before
    fun setUp() {
        repository = mockk()
        prefetcher = mockk()
        every { prefetcher.prefetch(any()) } just Runs
        every { repository.artworks(any(), any()) } returns flowOf(emptyList())
        warmup = FeedWarmupImpl(repository, prefetcher)
    }

    @Test
    fun start_twice_loadsOnce() = runBlocking {
        coEvery { repository.loadMore(any(), any(), any()) } returns LoadOutcome.Loaded

        warmup.start()
        warmup.start()
        warmup.awaitIfRunning()

        coVerify(exactly = 1) { repository.loadMore(Section.EUROPEAN, any(), any()) }
    }

    @Test
    fun failedLoad_doesNotThrow_andStillFinishes() = runBlocking {
        coEvery { repository.loadMore(any(), any(), any()) } throws IllegalStateException("boom")

        warmup.start()
        warmup.awaitIfRunning()

        verify(exactly = 0) { prefetcher.prefetch(any()) }
    }

    @Test
    fun awaitIfRunning_withoutStart_returnsAtOnce() = runBlocking {
        warmup.awaitIfRunning()
    }
}