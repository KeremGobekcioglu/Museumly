package com.kg.museumly.data

import android.util.Log
import androidx.room.withTransaction
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkDetailMapper
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.ArtworkMapper
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.ProviderTurnSource
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.data.local.favorites.FavoriteEntity
import com.kg.museumly.data.local.favorites.FavoritesDao
import com.kg.museumly.data.local.placement.PlacementDao
import com.kg.museumly.data.local.placement.PlacementEntity
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkDetail
import com.kg.museumly.domain.model.ArtworkWithDetail
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.model.TimelineStop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * One department of one provider. The repository rotates over these
 * exactly the way it used to rotate over providers.
 * key is what the cursor is stored under: "cleveland:Chinese Art", "met:6".
 */
private class Source(val provider: ArtworkProvider, val department: String) {
    val key = provider.id + ":" + department
}
/**
 * One list of artworks, and the names its bookkeeping is stored under.
 *
 * era == null is the feed. Its keys are exactly what they were before eras
 * existed ("european", "met:11"), so saved cursors and positions stay valid.
 *
 * With an era, every key gets "@<era id>": "european@1600s", "met:11@1600s".
 * The cursor key has no section in it, because a department belongs to
 * exactly one section.
 *
 * Every list, cursor and turn key is built here and nowhere else. A key
 * built by hand somewhere is how the feed and an era end up sharing a cursor.
 */
private class ListScope(val section: Section, val era: Era?) {

    val listKey: String

    init {
        if (era == null) {
            listKey = section.id
        } else {
            listKey = section.id + "@" + era.id
        }
    }

    fun cursorKey(source: Source): String {
        if (era == null) {
            return source.key
        }
        return source.key + "@" + era.id
    }
}
@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    private val database: MuseumDatabase,
    private val artworkDao: ArtworkDao,
    private val artworkDetailDao: ArtworkDetailDao,
    private val cursorDao: ProviderCursorDao,
    private val providers: Set<@JvmSuppressWildcards ArtworkProvider>,
    private val seedSource: SeedSource,
    private val turnSource: ProviderTurnSource,
    private val favoritesDao: FavoritesDao,
    private val placementDao: PlacementDao
) : ArtworkRepository {
    private companion object {
        // callTimeout (NetworkModule) bounds a single HTTP call. fetchPage
        // can be several of those in a row — Met especially, one call per
        // object — so a provider that's merely slow, never erroring, never
        // tripping a single call's timeout, would otherwise have no ceiling
        // at all. This is that ceiling, generous relative to the 10s
        // per-call one since it has to cover the whole page.
        const val PROVIDER_FETCH_TIMEOUT_MS: Long = 20_000

        // How many works the timeline shows per era.
        const val TIMELINE_WORKS_PER_ERA: Int = 3
    }

    /**
     * Builds the rotation list for a section.
     *
     * Each provider names its own departments (see departmentsFor), so one
     * section can be several departments per provider. Two rules:
     *
     * - Live only: a department whose cursor row says exhausted (next == null)
     *   gets no slot. Otherwise its dead slot would hand its turn to whatever
     *   comes next and tilt the balance toward the other museum.
     * - Alternate museums: each provider cycles through its own live departments,
     *   and providers take turns. A provider with fewer departments repeats them,
     *   so every museum gets an equal share of pages.
     *
     * Asia: Chinese, met:6, Japanese, met:6, Korean, met:6, Indian, met:6
     *
     * A department can appear more than once; load tries each key once per call.
     * Must be called under the mutex — it reads cursor rows load may write.
     *
     * Exhaustion is per scope: a department finished in one era still has a
     * slot in the feed and in every other era, because each reads its own cursor.
     */
    private suspend fun sourcesFor(scope: ListScope): List<Source> {
        val ordered: List<ArtworkProvider> = providers.sortedBy { it.id }

        val perProvider: MutableList<List<String>> = ArrayList()
        var longest = 0
        for (provider in ordered) {
            val live: MutableList<String> = ArrayList()
            for (department in provider.departmentsFor(scope.section)) {
                val saved = cursorDao.get(scope.cursorKey(Source(provider, department)))
                // we need to check exhaustion for providers
                if (saved != null && saved.next == null) // try next provider, this is finished.
                    continue // exhausted: no slot
                live.add(department)
            }
            perProvider.add(live)
            if (live.size > longest) {
                longest = live.size
            }
        }

        val sources: MutableList<Source> = ArrayList()

        for (round in 0 until longest) {
            for (p in ordered.indices) {
                val departments = perProvider[p]
                if (departments.isEmpty())
                    continue
                val department = departments[round % departments.size]
                sources.add(Source((ordered[p]), department))
            }
        }
        return sources
    }

    /**
     * Only one writer at a time.
     *
     * insert() reads maxPosition(), then writes. Without this lock, two
     * writers can both read 19, and both write positions 20..39. Duplicate
     * positions break ORDER BY, so the feed order becomes random.
     *
     * SQLite already handles two writes at once. It does not handle the
     * math we do in between them. That is what this protects.
     *
     * Mutex, not synchronized, because these functions suspend.
     */
    private val mutex = Mutex()

    /**
     * Returns Flow, so the screen subscribes once and gets every future version automatically.
     */
    override fun artworks(section: Section, era: Era?): Flow<List<Artwork>> {
        val scope = ListScope(section, era)
        return artworkDao.observeByList(scope.listKey).map { rows: List<ArtworkEntity> ->
            val result: MutableList<Artwork> = ArrayList()
            for (row in rows) {
                result.add(ArtworkMapper.toDomain(row))
            }
            result
        }
    }

    override suspend fun artworkWithDetail(id: String): ArtworkWithDetail? {
        Log.d("REPOSITORY ARTWORKWITHDETAIL", "ARTWORK ID : $id")
        val artworkEntity = artworkDao.byId(id) ?: return null
        val artworkDetailEntity = artworkDetailDao.getById(id) ?: return null
        Log.d("REPOSITORY ARTWORKWITHDETAIL", "STILL NOT NULL")
        return ArtworkWithDetail(
            ArtworkMapper.toDomain(artworkEntity),
            ArtworkDetailMapper.toDomain(artworkDetailEntity)
        )
    }

    override suspend fun byId(id: String): Artwork? {
        val entity: ArtworkEntity? = artworkDao.byId(id)
        if (entity == null)
            return null
        return ArtworkMapper.toDomain(entity)
    }
    /**
     * Writes artworks to the database and appends them to one list, numbered
     * after that list's last placement.
     *
     * The caller must lock the mutex first. This function does not lock it.
     * Kotlin's Mutex cannot be locked twice by the same caller — it would
     * freeze forever. This is private so all callers stay in this file.
     *
     * One transaction for all three inserts:
     *  - placements have a foreign key on artworks, so artworks go in first,
     *    or the insert fails with FOREIGN KEY constraint failed.
     *  - the app can't die between the inserts and leave artworks that
     *    belong to no list.
     *  - Room notifies the screen once, when the transaction commits.
     */
    private suspend fun insert(
        items: List<Artwork>,
        details: List<ArtworkDetail>,
        scope: ListScope
    ) {
        var position: Int = placementDao.maxPosition(scope.listKey)
        val entities: MutableList<ArtworkEntity> = ArrayList()
        val detailEntities: MutableList<ArtworkDetailEntity> = ArrayList()
        val placements: MutableList<PlacementEntity> = ArrayList()
        for (i in items.indices) {
            position += 1
            val artwork: Artwork = items[i]
            val detail: ArtworkDetail = details[i]
            entities.add(ArtworkMapper.toEntity(artwork, scope.section.id))
            detailEntities.add(
                ArtworkDetailEntity(
                    id = artwork.id,
                    medium = detail.medium,
                    dimensions = detail.dimensions,
                    creditLine = detail.creditLine,
                    culture = detail.culture,
                    period = detail.period,
                    highResImageUrl = detail.highResImageUrl,
                    artistBio = detail.artistBio,
                    description = detail.description,
                    didYouKnow = detail.didYouKnow,
                )
            )
            placements.add(
                PlacementEntity(
                    artworkId = artwork.id,
                    position = position,
                    listKey = scope.listKey
                )
            )
        }
        database.withTransaction {
            artworkDao.insertAll(entities)
            artworkDetailDao.insertAll(detailEntities)
            placementDao.insertAll(placements)
        }
    }

        //    override suspend fun seedIfEmpty() {
    //        mutex.withLock {
    //            val existing: Int = artworkDao.count()
    //            if(existing > 0)
    //                return@withLock
    //            insert(seedSource.artworks())
    //        }
    //    }
    // TODO total deadline for loadMore (slow-but-alive network): each source
    // can take up to PROVIDER_FETCH_TIMEOUT_MS, one after another, so a section
    // with several sources can sit on the loading screen for minutes.
    //
    // Tried and reverted: withTimeoutOrNull around the whole loop.
    //  - It also covers Room work. Under runTest, Room runs on a real thread,
    //    the test scheduler idles and jumps virtual time to the deadline, so
    //    it fires instantly (broke ~15 repository tests).
    //  - It can fire after the transaction commits: artworks saved, load
    //    reported Failed.
    //
    // Plan: a budget, not a wrapper. deadline = timeSource.markNow() + total;
    // before each source, stop if passed, else fetch with
    // withTimeoutOrNull(min(PROVIDER_FETCH_TIMEOUT_MS, time left)).
    // Only fetchPage is ever timed. Tests swap timeSource for testTimeSource.
    override suspend fun loadMore(section: Section, size: Int, era: Era?): LoadOutcome {
        val scope = ListScope(section, era)
        return load(scope, size)
    }

    /**
     * Loads one page into the scope's list. The cursor, the turn and the
     * positions all come from the scope, so an era load never moves the
     * feed's bookkeeping, and the feed never moves an era's.
     *
     * The tried set keeps source.key: it only stops one call from fetching
     * the same department twice, and within one call every key has the same era.
     */
    private suspend fun load(scope: ListScope, size: Int): LoadOutcome {
        mutex.withLock {
            val sources = sourcesFor(scope)
            if (sources.isEmpty()) {
                // every department in this section is exhausted
                return LoadOutcome.Exhausted
            }
            val failures: MutableList<String> = ArrayList()
            // the same department can sit in the list several times;
            // try each one at most once per call
            val tried: MutableSet<String> = HashSet()
            val turn: Int = turnSource.getTurn(scope.listKey)
            for (attempt in sources.indices) {
                val index: Int = (turn + attempt) % sources.size
                val source: Source = sources[index]
                if (!tried.add(source.key)) {
                    continue
                }
                val cursorKey: String = scope.cursorKey(source)
                val saved: ProviderCursor? = cursorDao.get(cursorKey)
                // Safety net: sourcesFor already drops exhausted departments.
                if (saved != null && saved.next == null) {
                    Log.d("REPO", "exhausted, skipping")
                    continue
                }
                // No row means this source has never been fetched: start at the beginning.
                var cursor: String? = null
                if (saved != null)
                    cursor = saved.next
                // The provider does everything: rebuilds its ID list if needed, walks
                // from `cursor`, hydrates each artwork, drops the unusable ones.
                Log.d("REPO", "calling ${source.key} cursor=$cursor")
                val started: Long = System.nanoTime()
                val page: PageResult? = withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MS.milliseconds)
                {
                    source.provider.fetchPage(cursor, size, source.department, scope.era)
                }
                val ms: Long = (System.nanoTime() - started) / 1_000_000
                if (page == null) {
                    Log.d("REPO", "${source.key} timed out after ${ms}ms")
                    failures.add("${source.key}: timed out")
                    continue
                }
                Log.d(
                    "REPO",
                    "${source.key} took ${ms}ms, ${page.items.size} items, next=${page.next}"
                )
                if (page.status == PageStatus.FAILED) {
                    val reason: String = page.failureReason ?: "failed"
                    Log.d("REPO", "${source.key} failed: $reason")
                    failures.add("${source.key}: $reason")
                    continue
                }
                // If page.next is null, this writes the exhaustion marker, and
                // sourcesFor leaves this department out from now on.
                //
                // One transaction because a crash between the writes leaves damage
                // nothing ever repairs:
                //   cursor moved, artworks not inserted -> that range is never
                //     requested again; the feed just silently lacks those works
                //   artworks inserted, details not -> those pages open a blank
                //     detail screen forever, since nothing refetches what's already
                //     in Room
                //
                // Empty items with status OK is normal (the mapper rejected the whole
                // batch) and the cursor still has to move — those IDs would be
                // rejected again next time.
                database.withTransaction {
                    if (page.items.isNotEmpty()) {
                        insert(page.items, page.details, scope)
                    }
                    cursorDao.put(ProviderCursor(cursorKey, page.next))
                }
                // DataStore, not Room, so it can't join the transaction. Only advance
                // the turn when the source actually delivered something.
                if (page.items.isNotEmpty()) {
                    turnSource.setTurn((index + 1) % sources.size, scope.listKey)
                    return LoadOutcome.Loaded
                }
            }
            if (failures.isNotEmpty()) {
                return LoadOutcome.Failed(failures.joinToString("; "))
            }
            return LoadOutcome.Exhausted
        }
    }

    override suspend fun count(section: Section, era: Era?): Int {
        val scope = ListScope(section, era)
        return placementDao.count(scope.listKey)
    }

    override fun getFavorites(section: Section?): Flow<List<Artwork>> {
        return favoritesDao.getFavorites(section?.id).map { rows: List<ArtworkEntity> ->
            val result : MutableList<Artwork> = ArrayList()
            for(row in rows)
            {
                result.add(ArtworkMapper.toDomain(row))
            }
            result
        }
    }

    override fun getFavoritesIds(): Flow<Set<String>> {
        return favoritesDao.observeFavoriteIds().map { it.toSet() }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        /**
         * if isFavorite true, insert. else, remove.
         */
        if(isFavorite)
        {
            val favoriteArtwork = FavoriteEntity(id, System.currentTimeMillis())
            favoritesDao.insert(favoriteArtwork)
        }
        else
        {
            favoritesDao.remove(id)
        }
    }

    override suspend fun favoritesCount(): Int {
        return favoritesDao.count()
    }

    override suspend fun rememberAspectRatio(id: String, ratio: Float) {
        artworkDao.setAspectRatio(id, ratio)
    }


    override suspend fun getFavoriteById(id: String): Artwork? {
        val favorite = favoritesDao.byId(id) ?: return null
        val entity = artworkDao.byId(id) ?: return null
        return ArtworkMapper.toDomain(entity)
    }

    /**
     * The one section the timeline shows for each era.
     *
     * Each section here must contain the era in Era.erasFor(section).
     * Otherwise the timeline fills a list the era grid of that section
     * never shows.
     *
     * No else branch: a new Era does not compile until it gets a section.
     */
    private fun timelineSectionFor(era: Era): Section {
        return when (era) {
            Era.PREHISTORIC, Era.BC_3000, Era.BC_2000 -> Section.EGYPT_NEAR_EAST
            Era.BC_1000, Era.BC_500, Era.AD_0 -> Section.GREEK_ROMAN
            Era.AD_500 -> Section.AFRICA_OCEANIA_AMERICAS
            Era.AD_1000 -> Section.ASIA
            Era.AD_1200 -> Section.MEDIEVAL
            Era.Y1400 -> Section.EUROPEAN
            Era.Y1500 -> Section.ISLAMIC
            Era.Y1600 -> Section.EUROPEAN
            Era.Y1700 -> Section.ASIA
            Era.Y1800, Era.Y1900 -> Section.EUROPEAN
            Era.Y2000 -> Section.ASIA
        }
    }

    /**
     * Fills every era's list up to TIMELINE_WORKS_PER_ERA works, era by era.
     *
     * An era that already holds enough works is skipped without a network
     * call. The rest go through loadMore, so the works land in the normal
     * era list ("european@1600s") and the era grid later continues from them.
     *
     * No mutex here: loadMore locks it itself, and Kotlin's Mutex is not
     * reentrant, so locking it here too would freeze forever.
     *
     * A failed era does not stop the others. Every failure reason is kept
     * and returned together at the end.
     */
    override suspend fun loadTimeline(): LoadOutcome {
        val failures: MutableList<String> = ArrayList()
        for (era in Era.entries) {
            val section: Section = timelineSectionFor(era)
            val existing: Int = count(section, era)
            if (existing >= TIMELINE_WORKS_PER_ERA) {
                continue
            }
            val outcome: LoadOutcome = loadMore(section, TIMELINE_WORKS_PER_ERA, era)
            if (outcome is LoadOutcome.Failed) {
                failures.add(outcome.reason)
            }
        }
        if (failures.isNotEmpty()) {
            return LoadOutcome.Failed(failures.joinToString("; "))
        }
        return LoadOutcome.Loaded
    }

    /**
     * The first TIMELINE_WORKS_PER_ERA works of every era's list, eras in
     * timeline order, works in list order inside an era. An era with no
     * works adds nothing.
     *
     * One Flow per era, combined, so the timeline updates when any era's
     * list changes.
     */
    override fun timelineStops(): Flow<List<TimelineStop>> {
        val perEra: MutableList<Flow<List<TimelineStop>>> = ArrayList()
        for (era in Era.entries) {
            val section: Section = timelineSectionFor(era)
            val stops: Flow<List<TimelineStop>> = artworks(section, era).map { artworks: List<Artwork> ->
                val result: MutableList<TimelineStop> = ArrayList()
                for (artwork in artworks) {
                    if (result.size >= TIMELINE_WORKS_PER_ERA) {
                        break
                    }
                    result.add(TimelineStop(artwork, section, era))
                }
                result
            }
            perEra.add(stops)
        }
        return combine(perEra) { lists: Array<List<TimelineStop>> ->
            val result: MutableList<TimelineStop> = ArrayList()
            for (list in lists) {
                result.addAll(list)
            }
            result
        }
    }

}