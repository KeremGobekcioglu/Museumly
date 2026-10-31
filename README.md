# Museumly

Native Android. Kotlin, Jetpack Compose. **Not** KMP — Android-only on purpose, so the
platform work (Hilt, Retrofit, image decode budget, gesture physics) stays the focus.

Package: `com.kg.museumly` · minSdk 29 · targetSdk 36 · compileSdk 37

A vertical reels-style feed of CC0 artworks aggregated from multiple museum open-access
APIs. Design north star: **it should feel like travelling through collections, not
querying a database.**

**Targets:** testable build on **1 Dec 2026**, v1 on **31 Dec 2026**. See [Road to v1](#road-to-v1).

---

## THE RULE FOR THIS PROJECT

**No data package until the UI is proven.**

World Wonders was aborted *after* the entire data layer was implemented, because the
content shape (16:9 webcam frames) did not fit the UI shape (portrait Reels container).
That mismatch was visible on day one. It was not discovered until day N, because the data
package was built first and the screen was built last. The cost was the whole project.

Phase 0 (2026-08-23) proved the UI, and everything since was built to serve a screen that
already existed. Keep it that way for every new surface.

---

## Current state

Phase 0 is long over. The app is a multi-module build with five surfaces and a live
backend for sign-in.

| Surface | State |
|---|---|
| **Feed** | Reels pager, one list per section (7 sections), gallery look: framed work, pendant lamp, wall placard |
| **Eras** | Timeline of highlight works with a draggable era ruler; tapping through opens an **era page** (a grid of one era, filtered by section) |
| **Detail** | Walk-up transition from the frame, zoomable image (Telephoto), wall label with catalogue data |
| **Favorites** | Staggered grid, section filter, sort toggle. Stored locally in Room |
| **Auth** | Google sign-in via Credential Manager → Museumly backend → JWT. Asked once, at the first favorite |

Two museum providers, both complete:

| Provider | Shape | Notes |
|---|---|---|
| **Met** | search returns IDs only → N+1 hydration | era filter via `dateBegin`/`dateEnd`; 403/429 cooldown |
| **Cleveland** | `skip`/`limit`, full records in one call | 41,511 CC0 works in the whole corpus |

Sections map onto each museum's own departments (`departmentsFor`):

| Section | Met department ids | Cleveland departments |
|---|---|---|
| Egypt & Near East | 10, 3 | Egyptian and Ancient Near Eastern Art |
| Greek & Roman | 13 | Greek and Roman Art |
| Islamic | 14 | Islamic Art |
| Medieval | 17 | Medieval Art |
| European | 11, 12 | European Painting and Sculpture; Modern European Painting and Sculpture |
| Asia | 6 | Chinese, Japanese, Korean, Indian and Southeast Asian Art |
| Africa, Oceania & Americas | 5 | African Art; Oceania; Art of the Americas |

Per-department corpus sizes are not tracked here. Date-filter behaviour on both APIs, with
measured numbers, is in [DATE_FILTERS.md](DATE_FILTERS.md).

The Cleveland integration is what validated the `ArtworkProvider` seam. Two APIs with
opposite shapes normalise behind one interface.

---

## Modules

```
:app            Application + MainActivity, Hilt entry, Firebase App Distribution
:domain         pure Kotlin: models, repository/provider interfaces, no Android
:data           Room, DataStore, Retrofit, providers, repositories, Coil prefetch
:presentation   Compose screens, ViewModels, navigation, theme
build-logic/    convention plugins (application + library), shared Android config
```

Dependency direction is `app → presentation → domain ← data`. `presentation` never sees a
DTO or a DAO; `domain` never sees Android.

## Architecture

```
domain/ArtworkProvider          ← the seam. fetchPage(cursor, size, department, era) → PageResult
                                  departmentsFor(section) → that museum's departments
data/remote/met/MetProvider
data/remote/cleveland/ClevelandProvider
data/ArtworkRepositoryImpl      ← rotation, cursors, placements, Room writes
data/local/                     ← Room entities/DAOs, DataStore sources
data/remote/museumly/           ← our own backend (auth only so far)
```

**Providers are `@IntoSet` multibound.** A new provider is one `@Binds` in
`ProviderModule`. Nothing else changes.

**Composite primary key** — `"met:436535"`, `"cleveland:160729"`. IDs collide across
providers; both museums use six-digit ranges. `Artwork.providerId` derives from the prefix.

### Section, Era, and the list a work lives in

`Section` is our own taxonomy, shared across museums so the UI never sees a museum's
department names. `Era` is a fixed enum of 16 date ranges (declaration order **is**
timeline order; ids like `"1600s"` are permanent because they go into list and cursor
keys). `Era.erasFor(section)` is what each section's slider shows.

**Lists are separate from artworks.** The `placements` table (`artworkId`, `position`,
`listKey`) says where a work sits in which list; the `artworks` table just holds works. So
one work can be in the feed *and* in an era's list without being stored twice.

**`ListScope` builds every key.** `era == null` is the feed, keyed exactly as it was before
eras existed (`"european"`, `"met:11"`), so saved cursors and positions stayed valid. With
an era every key gains `@<era id>` (`"european@1600s"`, `"met:11@1600s"`). It is the only
place keys are built — a key built by hand elsewhere is how the feed and an era end up
sharing a cursor.

**Per-source cursors.** A source is one department of one provider. `ProviderCursorDao` is
keyed by `ListScope.cursorKey`. A null cursor means exhausted, and that source gets no slot
in that scope from then on. Exhaustion is per scope: a department finished in one era is
still live in the feed and in every other era.

**Rotation.** `sourcesFor(scope)` builds the slot list: each provider cycles its own live
departments, and providers (sorted by `id` — `@IntoSet` gives no ordering) alternate, so
each museum gets an equal share. Asia is `Chinese, met:6, Japanese, met:6, Korean, met:6,
Indian, met:6`. `load` starts at a remembered `turn`, tries each source key at most once
per call, and stops after the first one that returns items. `turn` advances only on
success. It is persisted per list key in `ProviderTurnSource` (its own DataStore file), not
held in memory — see the traps below.

**Timeline and the era page.** `loadTimeline()` fetches `TIMELINE_WORKS_PER_ERA` (3)
highlight works per era into their own list. The era page's **All** tab is
`walkRounds(lists, exhausted)` (`domain/EraRounds.kt`): one work from each section in turn,
complete rounds only, so it is append-only — a shown work never moves and a saved index
stays valid.

**Feed ordering is settled: stable, append-only, no shuffle.** The corpus only grows
forward. Frontier is a single high-water-mark `Int` in DataStore (`FeedPositionSource`;
eras have `EraPositionSource`); cold start opens two items behind it. Explicitly rejected:
resume-where-left-off, seen-flags, hiding. Backward traversal stays intact.

**`setFrontier` ignores any value lower than what is stored**, and does the compare inside
`dataStore.edit` so the read-then-write is transactional. Without this the display offset
gets written back as the frontier: open at `stored - 2`, `LaunchedEffect` fires
`onPageChanged` on first composition, and the frontier regresses by 2 on every cold start
even if the user never scrolls. A frontier only ever advances — enforce it, don't just name
it that.

**Position numbering is guarded by a mutex.** `insert` reads `maxPosition()` then writes.
Two concurrent writers would both read 19 and both write 20..39. SQLite handles
simultaneous writes; it does not handle the arithmetic between them. The lock is one global
`Mutex` held across the whole of `load` — network fetch included (see Deferred).

**One transaction per page.** Artworks, details and placements go in together
(`placements` has a foreign key on `artworks`, so order matters), and `insert()` plus
`cursorDao.put()` share one `database.withTransaction {}`. A throw rolls back both instead
of leaving the cursor ahead of records that never landed.

**Mapper as gatekeeper.** Records failing quality checks return `null` silently. Upstream
callers never see invalid records. Note this is per-record rejection, not error handling —
a mapper returning null says nothing about whether the fetch worked.

**Room** is at schema version 8; schemas are exported to `data/schemas/`. Detail data
(`ArtworkDetailEntity`) is written at fetch time alongside the artwork, so the detail
screen reads Room and never the network.

### Failure vs exhaustion — the distinction the whole stack carries

Every layer used to swallow its own errors, so an empty page from a network failure was
byte-for-byte identical to an empty page from a finished corpus. Nothing could tell them
apart, so the UI showed "You're all caught up" when the network was down.

`PageStatus` on `PageResult`:

| Status | Meaning |
|---|---|
| `OK` | records returned, more may exist |
| `EXHAUSTED` | provider genuinely reached the end of its corpus |
| `FAILED` | the call failed; says nothing about whether more exists |

Partial success is `OK`, not `FAILED` — 13 records then a timeout means keep the 13 and
resume from the correct cursor. Only a total loss is `FAILED`.

The repository aggregates to `LoadOutcome` (`Loaded` / `Exhausted` / `Failed(reason)`) and
**skips the cursor write entirely on `FAILED`.** That line is the structural fix for the
cursor-poisoning trap — see below. A provider that exceeds `PROVIDER_FETCH_TIMEOUT_MS`
(20s) counts as failed for that call.

`TailState` is a sealed interface (`Idle` / `Loading` / `Failed(message)` / `Exhausted`),
one field on `ScrollUiState`. It replaced three independent booleans that could represent
32 combinations of which about five were real. The bug that forced the change: "exhausted"
and "never attempted" produced identical field values, so a cold start with data already in
Room rendered the terminal page and could not be escaped. `Idle` means "ready to fetch" —
never asked, or last attempt succeeded. Both want the same behaviour at the sentinel.

Prefer a sealed type over booleans wherever states are mutually exclusive. Booleans require
you to have anticipated every combination; a sealed `when` is checked by the compiler.

### Auth

- Google ID token via Credential Manager (`requestGoogleIdToken`) → `POST auth/google` on
  our backend → access token (JWT), stored in its own DataStore file (`AuthTokenStore`).
- `AuthRepository.isSignedIn` is derived from the stored token **and its `exp` claim**
  (`JwtExpiry.kt`, read without verifying — the server does that). A stored but expired
  token counts as signed out. Nobody sets a "signed in" boolean by hand.
- `AuthInterceptor` is on a separate `@MuseumlyHttpClient` and attaches
  `Authorization: Bearer …` to backend requests only. Met and Cleveland calls never carry
  it. `/auth/*` paths are skipped. A 401 clears the token, but only if the stored token is
  still the one that was sent (so a fresh login isn't wiped by a late 401).
- The sign-in sheet is shown once, at the first favorite (`SignInPromptSource`); "Not now"
  is remembered.
- Backend base URL is `BuildConfig.API_BASE_URL`, from the `API_BASE_URL` env var / CI
  secret. The build fails if it is missing.

---

## Invariants — do not break these

**CC0 is baked into the `@GET` annotation literal, never a runtime parameter.**
Monetisation depends on commercial-use rights. A filter passed as an argument can be
forgotten at a call site; one inside the URL literal cannot.

- Met → `isPublicDomain == true`, checked in the mapper
- Cleveland → `?cc0` in the annotation, plus a `share_license_status` check in the mapper

**Verify the image CDN before writing any provider code.** A working JSON API does not
mean loadable images — they are different hosts with different rules. `curl` one image URL
and confirm HTTP 200 with `Content-Type: image/*` first. This rule exists because AIC cost
a full spike (see below).

**Never cache a failure.** `loadIds` caching an empty list after a failed call poisons the
provider for the whole process lifetime. Same bug class as the cursor-poisoning trap below.
Cache on success only.

**Era ids and section ids are permanent.** They are baked into list keys, cursor keys and
turn keys. Renaming one orphans saved placements and cursors.

---

## Provider notes

### Met

- `hasImages=true` only. `isHighlight` and `isOnView` were removed — together they left
  **21** objects in European Paintings; `hasImages` alone gave **471**. `isOnView` also
  proved volatile.
- `q=*` is a genuine match-all. Never swap it for a letter.
- **N+1 is intentional and hidden behind the seam.** `/search` returns every matching ID in
  one response; each artwork needs its own `/objects/{id}` call. Object calls run in
  parallel batches of 4 (`async` + `awaitAll`), kept under OkHttp's default
  `maxRequestsPerHost` of 5.
- **Search is capped at 10,000 ids** (`offset + limit`). IDs are fetched 500 at a time and
  cached per `IdQuery(departmentId, era)`, so a feed list and an era list of the same
  department never mix.
- **Era filter needs both `dateBegin` and `dateEnd`** — with only one, the Met ignores the
  filter without an error. A work only matches when its *whole* span is inside the era, so a
  work dated -600..-400 is in neither `BC_1000` nor `BC_500` (about half of Greek & Roman
  is hidden from the slider; expected, documented on `Era`). Full measurements in
  [DATE_FILTERS.md](DATE_FILTERS.md).
- **403/429 means bot protection / rate limit.** On either, `MetProvider` stops calling for
  `BLOCK_COOLDOWN` (60s) — for every department, since the block is on the app. Left alone,
  blocks cleared in ~30–35s in testing.
- `aspectRatio` is always null — the Met gives no pixel dimensions. The UI falls back to the
  frame's ratio, and a measured ratio is saved once the image loads
  (`rememberAspectRatio`).
- SSL errors seen on the emulator; not yet confirmed on a real phone.

### Cleveland

- **`orderby` is broken on `/artworks`.** Every documented syntax returns `total: 0` —
  bare fieldname, `-id`, `"id ASC"`, `"accession_number ASC"`. Not a `cc0` interaction; it
  fails without `cc0` too. Omitted entirely.
- **Default order is undocumented but deterministic.** Repeated identical queries return
  identical sequences, and `skip` offsets line up across calls. Paging depends on this.
  Nothing guarantees it survives the daily 5am index rebuild — if records start going
  missing, check here first.
- **Era filter** is `created_after` / `created_before` and checks only the *earliest* date
  (both ends inclusive; BC is negative). See [DATE_FILTERS.md](DATE_FILTERS.md).
- Image dimensions arrive as **quoted strings** (`"width": "956"`). Declaring `Int` in the
  DTO throws at parse time. `String?` in the DTO, `toFloatOrNull()` in the mapper.
- Guard division: `width / 0f` is `Float.POSITIVE_INFINITY`, no exception. Infinity always
  exceeds the container ratio, so the image renders at zero height — a blank page with
  nothing in logcat. Return null on zero or negative.
- `creators` can be `[]` for unattributed works. Not an error.
- `creators[0].description` is `"Song Xu (Chinese, 1525-c. 1606)"` — name, nationality, and
  dates in one string. Trimmed at the first `(` to match the Met's format.
- **`dimensions` is unmappable.** Keys vary per object: `sheet`, `framed`, `unframed`,
  `platemark`. No fixed shape. Ignore it — `images.web.width`/`height` is what you want.
- `description` is frequently null. `tombstone` is always populated and is the fallback.
  Text is run through `ClevelandTextCleaner`.
- `fetchPage` advances `skip` by raw `dtos.size`, **never** accepted count. Advancing by
  accepted count re-reads rejected records forever.
- A null response breaks without marking exhausted, so the page stays retryable. An empty
  `data` array is the only exhaustion signal.
- **`record_type` is not handled.** A Cleveland portfolio shows up as 40+ near-identical
  records. See Loose ends.

---

## Traps that have already cost time

**Cursor poisoning.** A failed fetch that returns an empty page writes
`ProviderCursor(id, null)` — the exhaustion marker. The repository then skips that provider
forever, across launches, even after the bug is fixed. Bitten twice, in two different
layers: once as `loadIds` caching an empty list, once as the repository writing a null
cursor after a failed `loadIds` returned `emptyList()` and made `i < allIds.size` false.

**Now structurally prevented** — the repository `continue`s past the `cursorDao.put` on
`PageStatus.FAILED`, so a failed provider's position is never touched. `loadIds` returns
`List<Int>?` with null meaning failure, so an empty department and a dead network are no
longer the same value.

Still: **clear app data before retesting a provider that failed.** And note Auto Backup
restores app data on reinstall by default, so a reinstall does *not* necessarily clear it —
see the backup trap below.

**Pager construction guard.** `rememberPagerState` reads `initialPage` exactly once and
clamps to 0 against an empty list permanently. The pager must not be constructed until
`initialPage != null` AND `artworks` is non-empty.

**`LaunchedEffect` timing.** Initial load lives in `ViewModel.init` via `repository.count()`,
not a `LaunchedEffect` — the latter fires before data exists and caused a spurious fetch
every launch.

**Overlap guard is `Job?.isActive`, not a `Boolean` flag.**

**`turn` reset on every process death.** It was a plain field on the `@Singleton`
repository — in memory only. After any cold start `turn` came back as `0` and the rotation
always retried the alphabetically-first provider (Cleveland) instead of resuming where it
left off. Fixed by `ProviderTurnSource`, the same DataStore-backed get/set-with-a-key shape
as `FeedPositionSource`, now keyed per list. Deliberately *not* excluded from Auto Backup —
`turn` needs to stay in sync with the (also backed-up) cursors.

**Auto Backup survives reinstall.** On by default since API 23, so uninstalling and
reinstalling restores app data from the cloud. Two manifest attributes, **both needed**,
each with its own file, or the range you didn't declare still backs up:
`fullBackupContent` (`backup_rules.xml`, API 23–30) and `dataExtractionRules`
(`data_extraction_rules.xml`, API 31+).

Current state of the exclusions — only DataStore files are excluded, **not the Room
database**:

| DataStore file | `backup_rules.xml` | `data_extraction_rules.xml` |
|---|---|---|
| `feed_position` | excluded | excluded |
| `era_position` | excluded | **not excluded** |
| `auth_preferences` | excluded | excluded |
| `sign_in_prompt` | excluded | excluded |

Two gaps to close: `era_position` is missing from the API 31+ file, and the Room database
(which holds the cursors, so a poisoned cursor would come back) is excluded from neither.

**`LaunchedEffect` keys, not recompositions.** `LaunchedEffect(currentPage, artworks.size)`
relaunches only when a key changes — it does *not* re-run on every recomposition. A guard
was once added to stop the prefetch "firing repeatedly" on the sentinel page; the repeated
firing didn't exist, and the guard broke a real case (a fast fling landing directly on the
sentinel, skipping the pages that would have triggered a prefetch). The trigger is
`artworks.size - currentPage <= 5` with no position guard.

**The sentinel page.** `pageCount = artworks.size + 1`. Without the extra page there is
physically nowhere to show loading, failure, or exhaustion — the pager simply refuses to
advance past the last artwork, which reads as a freeze. The sentinel renders `TailState`
directly, and `Idle` there triggers a fetch rather than claiming the corpus ended.

**AIC is blocked by Cloudflare.** `www.artic.edu/iiif/...` returns 403 with a JS challenge
to any non-browser client. Cloudflare fingerprints the TLS handshake (JA3/JA4) and HTTP/2
frame ordering, so no header tweak fixes it and OkHttp cannot present a browser handshake.
The JSON API at `api.artic.edu` is unaffected — the gate is on the image CDN only. Failure
mode is silent: Coil receives HTML instead of JPEG, decode fails, `AsyncImage` renders a
black box. Metadata and images are not separable, so this makes the provider unusable
rather than partially usable. Cleveland replaced AIC as provider #2.

---

## Data sources

| Source | Key? | Scale | Pagination | Status |
|---|---|---|---|---|
| Met | none | ~490k, ~400k CC0 | none at all | **live** |
| Cleveland | none | 41,511 CC0 with images | `skip`/`limit` | **live** |
| Met Open Access CSV | none | full Met dataset | bulk file | **planned** — moves the Met behind our backend, see Road to v1 |
| Paris Musées | free token | 14 museums, 150k+ CC0 | GraphQL | candidate — different transport is a good seam test |
| Europeana | free key | pan-European | yes | candidate — mixed per-item licensing, expect a chunk unusable |
| AIC | none | ~120k | real | **ruled out** — image CDN blocked |
| Rijksmuseum | — | — | — | **ruled out** — Linked Art / OAI-PMH harvesting, not REST |

Docs:
- Met — https://metmuseum.github.io/
- Cleveland — https://openaccess-api.clevelandart.org/

---

## Stack

| Layer | Choice |
|---|---|
| UI | Compose (BOM + `foundation` 1.12), Navigation Compose (type-safe routes) |
| Images | Coil 3, Telephoto for the zoomable detail image |
| Network | Retrofit 3 + kotlinx.serialization, OkHttp 4.12 |
| Local | Room (composite string PK), DataStore |
| DI | Hilt (`@IntoSet` multibinding for providers) |
| Async | Coroutines + Flow |
| Auth | Credential Manager + Google ID |
| Distribution | Firebase App Distribution (manual GitHub Actions workflow) |
| CI | GitHub Actions: unit tests on every push to main and every PR |

Tests are JVM-only (JUnit, Robolectric, MockK, Turbine, MockWebServer). See
[TESTING.md](TESTING.md).

`Json` config: `ignoreUnknownKeys = true`, `coerceInputValues = true`. Single shared
instance — **do not** enable `JsonNamingStrategy.SnakeCase`, it would silently break
`MetDto` (the Met's JSON is already camelCase) while fixing Cleveland's.

Two `OkHttpClient`s: the shared one (Met, Cleveland, Coil — Coil derives its own copy with
no call timeout, since images can be large) and `@MuseumlyHttpClient`, which adds
`AuthInterceptor`. One `Retrofit` per API, built by a private helper in `NetworkModule`,
since only the base URL differs. Coil's disk cache is `cacheDir/artwork_images`, capped at
256 MB.

Dependency discipline: everything in `gradle/libs.versions.toml`, never inline in
`build.gradle.kts`, never a duplicated version number.

---

## Road to v1

Auth is done as of 2026-11-01: JWT expiry check and the backend-only interceptor are
working and tested. Remaining, in order:

1. **Onboarding + profile screen.** Profile is the blocker for two deferred auth pieces:
   saving the Google name/photo, and sign-out. Needs a user class. Once it exists it
   replaces the Favorites tab, and app settings live inside it.
2. **Share card, widget, set-as-wallpaper.** Share logic comes from Yıldızname; the card
   design is new. (`ArtworkActions` already has an `onShareClick` hook, currently `null`.)
3. **Backend sync** for favorites and feed positions. Server auth is already live.
4. **Met moves to the backend.** Open Access CSV → Postgres, then a slow worker fills in
   image URLs. First step: the Flyway migration for the `artworks` table, developed locally
   before merging.
5. **Era screen redesign.** The 2-column grid hit Met rate limits and loads slowly on
   mobile data. Candidates: "rooms" or a one-at-a-time layout. Undecided.
6. **Notifications + daily pick** — a random highlighted work, to test the pipeline end to
   end.
7. **Account deletion.**

**After v1** (explicitly not now): the game, and art-text enrichment.

## Loose ends — fix before release

- **Coil disk cache growth.** ~50 MB after ~50 swipes. The cap is 256 MB, so it will
  saturate and start evicting after a few hundred swipes; decide whether that is
  acceptable or whether request sizes should come down.
- **`loadMore` has no total deadline.** Each source can take up to 20s, one after another,
  so a section with several sources can sit on the loading screen for minutes. The plan is a
  time *budget* checked before each source, not a `withTimeout` wrapper — see the TODO on
  `ArtworkRepositoryImpl.loadMore` for why the wrapper was tried and reverted.
- **Cleveland portfolios** appear as 40+ near-identical records (`record_type` is
  `cover`/`part`/`component`/`object` and is not looked at).
- **Met SSL error** on the emulator, not yet checked on a real phone.
- **Backup exclusions** are incomplete — see the Auto Backup trap above.
- **Mixed-provider failure is silent** (see Deferred #3).
- **Frontier upper clamp missing** in `ScrollViewModel` (pinned by an `@Ignore`'d test;
  see TESTING.md).

---

## Deferred

1. **Met throughput.** Object hydration now runs 4 at a time (it was strictly sequential,
   ~0.4s/artwork). Cleveland is still ~0.11s/artwork, so the feed can alternate between
   fast and slow pages. Re-measure on mobile data before changing anything further; raising
   the prefetch threshold is tuning to one network, and shrinking the page size doesn't
   help Met at all (per-artwork cost is fixed) while multiplying Cleveland's request count.
   Moving the Met behind the backend (Road to v1 #4) makes most of this moot.
2. **Row-count capping.** Room rows are tiny (~500B). Coil's disk cache is the real storage
   consumer.
3. **Mixed-provider failure is silent.** If Met fails and Cleveland succeeds, the outcome is
   `Loaded`, no error, the page fills — correct for the user, invisible for debugging. The
   repository already collects `failures`; it just doesn't survive the `Loaded` return.
   Pinned as an `@Ignore`'d regression test (`ArtworkRepositoryImplTest`, see TESTING.md's
   Known Bugs) — un-ignore once `loadMore()` surfaces it.
4. **Mutex scope.** The repository lock is held across the entire network fetch — seconds
   of I/O guarding arithmetic that takes microseconds. Narrowing it is the enabler for
   concurrent provider fetches or a background pipeline. Not free: `turn` is shared mutable
   state and each cursor is a read-modify-write, so it needs per-list serialisation rather
   than one global lock.
5. **More providers.** Paris Musées or Europeana, once the backend owns ingestion.

---

## Open questions

The gallery wall is the answer to the old "frame question": a fixed frame with the work
hung inside it (`GalleryGeometry`, `ArtworkFrame`), a pendant lamp aimed at the frame's
corners, and a placard below. What is still open:

- [ ] Does the page counter stay? The feed still shows "23 / 60", which says the user is in
  a growing list — precisely the search-engine feeling this app rejects. Museums don't
  number the wall.
- [ ] Era screen layout: grid, rooms, or one-at-a-time (Road to v1 #5).
- [ ] Does "finite and curated" mean a hand-picked set, or a generated finite subset?
- [ ] Is Europeana worth its mixed-licensing complexity for European coverage?
- [ ] Does Met ever return `artistDisplayName: ""` rather than absent? `captionLine` treats
  null correctly but a blank string would render `" · 1550"`.
- [ ] `museumName()` falls back to the raw provider id for an unknown provider. Fine today
  (two providers, both named); revisit when a third lands.

---

## Changelog

- **2026-11-01** — **README brought up to date.** Rewritten against the code: modules, eras,
  placements, detail, favorites, auth, the road to v1 and the loose ends. Found while doing
  it: backup rules exclude only DataStore files (not Room), and `era_position` is missing
  from the API 31+ rules.
- **2026-10-31** — **Auth.** Google sign-in → backend JWT; expired tokens count as signed
  out; backend-only auth interceptor; sign-in prompt at the first favorite.
- **2026-10-14** — **Era page.** Per-era grid reachable from the timeline; fix for Met 403
  on the timeline and the grid stalling on short first pages.
- **2026-10-12** — **Eras tab.** Timeline of works with a draggable era ruler.
- **2026-10-11** — **Eras data.** Era enum, date filters on both APIs, `ListScope`, and
  lists moved out of `artworks` into a `placements` table.
- **2026-10-10** — Bottom bar (Feed / Eras / Favorites) and `MainShell`; Firebase App
  Distribution set up.
- **2026-10-06 → 10-09** — Favorites: Room storage, feed and detail toggles, grid screen with
  section filter and sort.
- **2026-10-04 → 10-05** — Split into `:app`, `:domain`, `:data`, `:presentation` with
  convention plugins.
- **2026-09-06** — **Error propagation and state modelling.** Added `PageStatus` to
  `PageResult` and `LoadOutcome` from the repository, so failure and exhaustion stopped
  being the same empty page. Repository skips the cursor write on `FAILED`, which
  structurally prevents cursor poisoning; `loadIds` returns nullable. Added the sentinel
  page (`pageCount + 1`). Replaced `isLoadingMore` / `isInitialLoad` / `error` with a sealed
  `TailState`. Fixed `setFrontier` (frontier was regressing by 2 on every cold start) and
  the caption printing `"null · 1550-1650"` for unattributed works.
- **2026-09-06** — **Provider round-robin turn persisted** (`ProviderTurnSource`); `turn`
  had reset to `0` on every process death.
- **2026-09-01** — **Cleveland provider complete; seam validated.** Two providers
  round-robin 20 records each. Found and worked around: `orderby` broken on Cleveland's
  artworks endpoint; cursor poisoning on failed first fetch.
- **2026-08-23** — **Phase 0 passed.** UI proven: images load, aspect ratio works, pager
  feels right. First spike used AIC image URLs and rendered black boxes — cause was
  Cloudflare bot protection on AIC's IIIF CDN, not app code. Swapped to the Met's
  `images.metmuseum.org`, verified 200 via curl first. AIC ruled out; Cleveland promoted to
  provider #2. Added the CDN-verification rule.
- **2026-08-23** — Project restarted after World Wonders aborted (UI/content aspect-ratio
  mismatch, discovered post-data-layer). README written, Phase 0 spike defined as the only
  authorised work, licensing verified across five providers.
