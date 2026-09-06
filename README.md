# Museumly

Native Android. Kotlin, Jetpack Compose. **Not** KMP — Android-only on purpose, so the
platform work (Hilt, Retrofit, image decode budget, gesture physics) stays the focus.

Package: `com.kg.museumly`

A vertical reels-style feed of CC0 artworks aggregated from multiple museum open-access
APIs. Design north star: **it should feel like travelling through collections, not
querying a database.**

---

## THE RULE FOR THIS PROJECT

**No data package until the UI is proven.**

World Wonders was aborted *after* the entire data layer was implemented, because the
content shape (16:9 webcam frames) did not fit the UI shape (portrait Reels container).
That mismatch was visible on day one. It was not discovered until day N, because the data
package was built first and the screen was built last. The cost was the whole project.

Phase 0 passed on 2026-08-23. The data layer that followed was built to serve a screen
that already existed. Keep it that way for every new surface.

---

## Current state

Two providers live, round-robining 20 records at a time.

| Provider | Status | Shape | CC0 corpus |
|---|---|---|---|
| **Met** | complete | search returns IDs only → N+1 hydration | 471 (European Paintings only) |
| **Cleveland** | complete | `skip`/`limit`, full records in one call | 41,511 |

The Cleveland integration is what validated the `ArtworkProvider` seam. Two APIs with
opposite shapes — one paginated with complete records, one an unpaginated ID list needing
a call per artwork — normalise behind one interface. Adding the second provider required
a single `@Binds` line and touched nothing above the seam.

---

## Architecture

```
domain/ArtworkProvider          ← the seam. fetchPage(cursor, size) → PageResult
data/remote/met/MetProvider
data/remote/cleveland/ClevelandProvider
data/ArtworkRepositoryImpl      ← round robin, cursors, Room writes
data/local/                     ← Room entities, DAOs
```

**Providers are `@IntoSet` multibound.** A new provider is one `@Binds` in
`ProviderModule`. Nothing else changes.

**Composite primary key** — `"met:436535"`, `"cleveland:160729"`. IDs collide across
providers; both museums use six-digit ranges. `Artwork.providerId` derives from the prefix.

**Per-provider cursors.** `ProviderCursorDao` keyed by `provider.id`. Each provider's
position is independent, so interleaving cannot corrupt either. A null cursor means
exhausted, and the repository skips that provider permanently.

**Round robin.** `loadMore` starts at a remembered `turn` index and stops after the first
provider that returns items. `turn` advances only on success, so an exhausted provider
doesn't waste a round. Providers are sorted by `id` before rotating — `@IntoSet` gives no
ordering guarantee, so without the sort "provider 0" is arbitrary.

**Feed ordering is settled: stable, append-only, no shuffle.** The corpus only grows
forward. Frontier is a single high-water-mark `Int` in DataStore (`FeedPositionSource`);
cold start opens two items behind it. Explicitly rejected: resume-where-left-off,
seen-flags, hiding. Backward traversal stays intact.

**`setFrontier` ignores any value lower than what is stored**, and does the compare inside
`dataStore.edit` so the read-then-write is transactional. Without this the display offset
gets written back as the frontier: open at `stored - 2`, `LaunchedEffect` fires
`onPageChanged` on first composition, and the frontier regresses by 2 on every cold start
even if the user never scrolls. A frontier only ever advances — enforce it, don't just name
it that.

**Position numbering is guarded by a mutex.** `insert` reads `maxPosition()` then writes.
Two concurrent writers would both read 19 and both write 20..39. SQLite handles
simultaneous writes; it does not handle the arithmetic between them.

**Mapper as gatekeeper.** Records failing quality checks return `null` silently. Upstream
callers never see invalid records. Note this is per-record rejection, not error handling —
a mapper returning null says nothing about whether the fetch worked.

### Failure vs exhaustion — the distinction the whole stack now carries

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

The repository aggregates to `LoadOutcome` (`LOADED` / `EXHAUSTED` / `FAILED`) and
**skips the cursor write entirely on `FAILED`.** That line is the structural fix for the
cursor-poisoning trap — see below.

`TailState` is a sealed interface (`Idle` / `Loading` / `Failed(message)` / `Exhausted`),
one field on `ScrollUiState`. It replaced three independent booleans that could represent
32 combinations of which about five were real. The bug that forced the change: "exhausted"
and "never attempted" produced identical field values, so a cold start with data already in
Room rendered the terminal page and could not be escaped. `Idle` means "ready to fetch" —
never asked, or last attempt succeeded. Both want the same behaviour at the sentinel.

Prefer a sealed type over booleans wherever states are mutually exclusive. Booleans require
you to have anticipated every combination; a sealed `when` is checked by the compiler.

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

---

## Provider notes

### Met

- Only `hasImages=true` is used. `isHighlight` and `isOnView` were removed — together they
  left **21** objects in European Paintings; `hasImages` alone gives **471**. `isOnView`
  also proved volatile, dropping significantly within one hour of testing.
- `q=*` is a genuine match-all. Never swap it for a letter.
- **N+1 is intentional and hidden behind the seam.** `/search` returns every matching ID in
  one response; each artwork needs its own `/objects/{id}` call. A page of 20 costs 21
  requests.
- `aspectRatio` is always null — the Met gives no pixel dimensions. The UI handles this via
  `BoxWithConstraints` falling back to `containerRatio`. This is correct, not a gap.
- Currently hardcoded to `departmentId=11` (European Paintings).

### Cleveland

- **`orderby` is broken on `/artworks`.** Every documented syntax returns `total: 0` —
  bare fieldname, `-id`, `"id ASC"`, `"accession_number ASC"`. Not a `cc0` interaction; it
  fails without `cc0` too. Omitted entirely.
- **Default order is undocumented but deterministic.** Repeated identical queries return
  identical sequences, and `skip` offsets line up across calls. Paging depends on this.
  Nothing guarantees it survives the daily 5am index rebuild — if records start going
  missing, check here first.
- Image dimensions arrive as **quoted strings** (`"width": "956"`). Declaring `Int` in the
  DTO throws at parse time. `String?` in the DTO, `toFloatOrNull()` in the mapper.
- Guard division: `width / 0f` is `Float.POSITIVE_INFINITY`, no exception. Infinity always
  exceeds `containerRatio`, so the image renders at zero height — a blank page with nothing
  in logcat. Return null on zero or negative.
- `creators` can be `[]` for unattributed works. Not an error.
- `creators[0].description` is `"Song Xu (Chinese, 1525-c. 1606)"` — name, nationality, and
  dates in one string. Trimmed at the first `(` to match the Met's format.
- **`dimensions` is unmappable.** Keys vary per object: `sheet`, `framed`, `unframed`,
  `platemark`. No fixed shape. Ignore it — `images.web.width`/`height` is what you want.
- `description` is frequently null. `tombstone` is always populated and is the fallback.
- Detail fetched via `/artworks/{id}` rather than growing the `Artwork` domain model.
- `fetchPage` advances `skip` by raw `dtos.size`, **never** accepted count. Advancing by
  accepted count re-reads rejected records forever.
- A null response breaks without marking exhausted, so the page stays retryable. An empty
  `data` array is the only exhaustion signal.

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
restores app data on reinstall by default, so a reinstall does *not* clear it — see the
backup rules below.

**Pager construction guard.** `rememberPagerState` reads `initialPage` exactly once and
clamps to 0 against an empty list permanently. The pager must not be constructed until
`initialPage != null` AND `artworks` is non-empty.

**`LaunchedEffect` timing.** Initial load lives in `ViewModel.init` via `repository.count()`,
not a `LaunchedEffect` — the latter fires before data exists and caused a spurious fetch
every launch.

**Overlap guard is `Job?.isActive`, not a `Boolean` flag.**

**Auto Backup survives reinstall.** On by default since API 23, so uninstalling and
reinstalling restores DataStore and Room from the cloud — the frontier and any poisoned
cursor come back with it. Excluded via `fullBackupContent` (API 23–30) and
`dataExtractionRules` (API 31+); **both files are needed**, each with its own manifest
attribute, or the range you didn't declare still backs up. Every byte here is re-fetchable
public data, so none of it is worth backing up.

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
| UI | Compose |
| Images | Coil 3 |
| Network | Retrofit + kotlinx.serialization |
| Local | Room (composite string PK) |
| Prefs | DataStore |
| DI | Hilt (`@IntoSet` multibinding for providers) |
| Async | Coroutines + Flow |

`Json` config: `ignoreUnknownKeys = true`, `coerceInputValues = true`. Single shared
instance — **do not** enable `JsonNamingStrategy.SnakeCase`, it would silently break
`MetDto` (the Met's JSON is already camelCase) while fixing Cleveland's.

Single shared `OkHttpClient`. One `Retrofit` per provider, built by a private helper in
`NetworkModule`, since only the base URL differs. No qualifiers needed — `Retrofit` is
never exposed as a binding, only the API interfaces are.

Dependency discipline: everything in `gradle/libs.versions.toml`, never inline in
`build.gradle.kts`, never a duplicated version number.

---

## Deferred, in order

1. **The frame question** — live, and it blocks the detail page. See Open questions.
2. **Met throughput.** Met produces at ~0.4s/artwork (N+1, sequential); a fast scroll
   consumes at ~0.5s/page. It barely keeps up, and any hiccup inverts it. Cleveland is
   ~0.11s/artwork and has 4× headroom, so the feed alternates comfortable and stalling —
   worse than consistently mediocre, because the user can't form a model of it. The fix is
   parallel fan-out (`Semaphore(8)` + `async`, ~8s → ~1.5s per 20), which would put Met
   ahead of the scroll rate. **Deliberately shelved** — implement when ready, don't paste.
   Note raising the prefetch threshold instead is tuning to one network; and shrinking the
   page size doesn't help Met at all (per-artwork cost is fixed) while quadrupling
   Cleveland's request count.
3. **Department expansion** — `MetProvider` hardcodes `departmentId=11`. Now designable
   against real data from both providers rather than guessed. Note the taxonomies don't
   overlap cleanly: Cleveland has "Chinese Art" and "Islamic Art", the Met has "Fashion"
   and "Musical Instruments".
4. **Detail page.** Cleveland is data-rich: `tombstone` always populated, plus `technique`,
   `measurements`, `culture`, `url`, and `description` / `did_you_know` where they exist
   (null rate never probed). Met is thinner — `objectDate`, `medium`, `dimensions`,
   `creditLine`, `culture`, `period`, `objectURL` — with no interpretive prose at all.
   Design for catalogue data across both and treat Cleveland's prose as enrichment; a
   detail page built around "the story of this work" would be empty on every Met record.
   Fetch via `/artworks/{id}` and `/objects/{id}` rather than growing the `Artwork` model.
5. **Mixed-provider failure is silent.** If Met fails and Cleveland succeeds, the outcome is
   `LOADED`, no error, the page fills — correct for the user, invisible for debugging. The
   repository already computes `anyFailed`; it just doesn't survive the `LOADED` return.
6. **Row-count capping** — Room rows are tiny (~500B). Coil's disk cache is the real
   storage consumer and is self-managing.
7. **Cursor/insert ordering** — the cursor advances before `insert` runs. If `insert`
   throws, the cursor has moved past records that never landed. Permanent hole, no signal.
8. **Mutex scope.** The repository lock is held across the entire network fetch — seconds of
   I/O guarding arithmetic that takes microseconds. Narrowing it is the enabler for
   concurrent provider fetches or a background pipeline. Not free: `turn` is shared mutable
   state and each cursor is a read-modify-write, so it needs per-provider serialisation
   rather than one global lock.

Also unaddressed: `record_type` (`cover`/`part`/`component`/`object`) means a Cleveland
portfolio can appear as 40+ near-identical records. Whether that floods a department is
unknown until there's real scroll data.

---

## Open questions

**The frame question — live, and everything on the screen waits on it.**

A device test with a fixed constrained box (`fillMaxWidth(0.6f)`, `fillMaxHeight(0.4f)`,
visible border) against each page taking the artwork's own shape: **the fixed frame reads
better.** Works hang at different sizes but the wall doesn't move, and the constancy is what
makes it a room rather than a feed. That's the north star turning into a layout decision.

- [ ] Fixed box with the image floating inside, box adapting to each work's ratio, or fixed
  outer bounds with an adapting box within? Only the third makes `aspectRatio`
  load-bearing — and only Cleveland provides it, so Met works would sit in a fixed box
  while Cleveland works get fitted frames. Whether that asymmetry is visible is unknown.
- [ ] Caption anchored to the screen or to the frame? Currently the screen. Anchored to the
  frame it becomes a wall label; on the screen it stays a Reels caption. Small change,
  changes everything about how it reads.
- [ ] Does the page counter stay? "23 / 60" tells the user they're in a growing list, which
  is precisely the search-engine feeling this app rejects. Museums don't number the wall.
- [ ] Does tapping into a work navigate somewhere? If yes, the frame decision should account
  for a shared-element transition.

Other open questions:

- [ ] Does "finite and curated" mean a hand-picked set, or a generated finite subset?
- [ ] Is provider attribution a feature or debug scaffolding? Currently a raw `"cleveland"`
  string in the corner. Attribution is something museums care about, and "which museum
  am I in" is arguably part of travelling — but a raw id isn't the answer either way.
- [ ] Is Europeana worth its mixed-licensing complexity for European coverage?
- [ ] Does Met ever return `artistDisplayName: ""` rather than absent? `captionLine` treats
  null correctly but a blank string would render `" · 1550"`. Cleveland's path is clean
  (mapper returns null for `creators: []`).

---

## Changelog

- **2026-09-06** — **Error propagation and state modelling.** Added `PageStatus` to
  `PageResult` and `LoadOutcome` from the repository, so failure and exhaustion stopped
  being the same empty page. Repository now skips the cursor write on `FAILED`, which
  structurally prevents cursor poisoning; `loadIds` returns nullable so a dead network and
  an empty department are distinguishable. Added the sentinel page (`pageCount + 1`) — there
  was previously nowhere to render loading or failure, which is why the feed appeared to
  freeze. Replaced `isLoadingMore` / `isInitialLoad` / `error` with a sealed `TailState`
  after "exhausted" and "never attempted" turned out to be indistinguishable and stranded
  the user on a permanent terminal page. Fixed `setFrontier` to reject values below the
  stored high-water mark (frontier was regressing by 2 on every cold start), the caption
  printing `"null · 1550-1650"` for unattributed works, and excluded DataStore and Room from
  Auto Backup.
- **2026-09-01** — **Cleveland provider complete; seam validated.** Two providers now
  round-robin 20 records each. Repository switched from drain-in-order to rotation with a
  sorted provider list and per-provider cursors. Found and worked around: `orderby` broken
  on Cleveland's artworks endpoint in every documented syntax; default order undocumented
  but deterministic; cursor poisoning on failed first fetch. Added provider id to the feed
  for verification.
- **2026-08-23** — **Phase 0 passed.** UI proven: images load, aspect ratio works, pager
  feels right. First spike used AIC image URLs and rendered black boxes — cause was
  Cloudflare bot protection on AIC's IIIF CDN, not app code. Swapped to the Met's
  `images.metmuseum.org`, verified 200 via curl first. AIC ruled out; Cleveland promoted to
  provider #2. Added the CDN-verification rule.
- **2026-08-23** — Project restarted after World Wonders aborted (UI/content aspect-ratio
  mismatch, discovered post-data-layer). README written, Phase 0 spike defined as the only
  authorised work, licensing verified across five providers.