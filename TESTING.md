# Testing

Museumly had zero tests before this suite. This document is the plan (written
first, per the brief), updated to reflect what actually got built, plus
everything you need to run and extend the suite.

Jump straight to [How to read this suite](#how-to-read-this-suite) if you
want the fastest path in, or the [test class table](#test-classes) for the
full map.

---

## Spec corrections

The original brief was written from memory/README and doesn't fully match
the code as it exists today. Fixing this before writing tests, instead of
after, avoided pinning behavior that was never real.

**3 of the 4 "known bugs" listed in the brief are not reproducible:**

| # | Described bug | Status |
|---|---|---|
| 1 | Frontier upper clamp missing in `ScrollViewModel` init | **Confirmed.** `ScrollViewModel.kt:87-94` only clamps `start < 0`; nothing clamps to the existing artwork count. Kept as the one `@Ignore`'d test — see [Known bugs](#known-bugs). |
| 2 | Cursor advances before `insert()`; an insert throw leaves a permanent hole | **Not reproducible.** `ArtworkRepositoryImpl.kt:199-204` wraps `insert()` and `cursorDao.put()` in one `database.withTransaction {}`, insert first. A throw rolls back both. Written as a regression-guard test instead (`ArtworkRepositoryImplTest`, the atomicity test) — see below. |
| 3 | `failureKind` overwritten by the last of multiple provider failures | **Not reproducible.** No `failureKind` field or `ErrorKindMapper` class exists anywhere in the codebase. The repository collects every failure into a list and returns `failures.joinToString("; ")` — all messages survive. Written as a regression-guard test instead. |
| 4 | `state.artworks[page]` throws on an out-of-range page | **Not reproducible.** Every access site uses `getOrNull` (`ArtworkReelsScreen.kt:117`, `ScrollViewModel.kt:202`). Per your instruction, dropped — `getOrNull` is too trivial to warrant its own test. |

**`ErrorKindMapper` doesn't exist.** There's no NETWORK/UNKNOWN classification
component anywhere. Each provider classifies failures inline via
`catch (e: HttpException) / catch (e: IOException) / catch (e: Exception)`,
plus the shared `ApiResult.Failed.worthRetrying()` extension (`HttpException`
code 429/5xx only). `ApiResultHelperTest` covers `worthRetrying()` directly;
the provider tests cover the inline classification through real HTTP
responses via MockWebServer.

**Concurrency width is 2, not 4, and the repository doesn't fan out at all.**
`MetProvider.fetchPage` batches object-detail calls 2 at a time
(`allIds.subList(i, minOf(i + 2, allIds.size))`), not 4. `ArtworkRepositoryImpl.loadMore`
calls providers strictly sequentially in its round-robin loop — there's no
fan-out across providers anywhere. `MetProviderTest`'s order-preservation
test targets the real width-2 batch.

**README.md contradicted the current code in one place, and confirmed it in
another — fixed the doc (both in the same commit as this test suite):**

- README's "Deferred, in order" item 7 ("the cursor advances before insert
  runs... permanent hole, no signal") described the same bug as brief item 2
  above, and was equally stale — the transaction wrapping already prevents
  it. Moved the resolution note into the existing "Cursor poisoning" trap
  section (it's the same bug class, closed by the same kind of fix) and
  removed the item from the Deferred list, renumbering what was item 8 to 7.
- README's item 5 ("Mixed-provider failure is silent... the repository
  already computes `anyFailed`; it just doesn't survive the `LOADED` return")
  **is accurate** and is a real, still-open gap, distinct from brief bug #3:
  if Met fails and Cleveland then succeeds in the same `loadMore()` call, the
  outcome is `Loaded` with no trace of the Met failure. The
  all-failures-joined test in `ArtworkRepositoryImplTest` only covers the
  case where *every* provider fails in the same call — it does not cover
  this silent-partial-failure case. Now pinned as a second `@Ignore`'d
  known-bug test — see [Known bugs](#known-bugs).

---

## Known bugs

Two `@Ignore`'d tests.

### 1. `ScrollViewModelTest.kt` — frontier upper clamp

```
frontier restore clamps to the existing artwork count, not just to zero
```

**Bug:** `ScrollViewModel.kt`'s `init` block computes `start = stored - 2`
and clamps only `if (start < 0) start = 0`. If the persisted frontier
(`FeedPositionSource`) is larger than what's currently available — e.g. the
DB was rebuilt, or this session's cache doesn't yet contain that many rows —
`initialPage` can exceed `artworks.size`, which `ArtworkReelsScreen.kt`
hands straight to `rememberPagerState(initialPage = ...)`.

**To un-ignore:** once `ScrollViewModel.kt`'s init block also clamps `start`
to the artwork count actually available (e.g. `.coerceAtMost(count - 1)`
using the same `repository.count()` result already being read in the block
below it), remove the `@Ignore` annotation. The test already asserts the
correct behavior — it should just start passing.

### 2. `ArtworkRepositoryImplTest.kt` — silent partial-failure

```
a provider failure is not silently lost when a later provider succeeds in the same call
```

**Bug:** this is README.md's "Deferred, in order" item 5, "Mixed-provider
failure is silent" — confirmed accurate against the current code, not one of
the four bugs the original brief flagged. Inside `ArtworkRepositoryImpl.loadMore()`'s
round-robin loop, a failure is appended to the local `failures` list, but as
soon as *any* provider in the same call succeeds, the function returns the
bare `LoadOutcome.Loaded` singleton immediately — `failures` is discarded
with it. If Met fails and Cleveland then succeeds in the same call, the
caller sees a plain, healthy `Loaded` with zero trace that Met failed.

**To un-ignore:** requires an actual design decision, not just a one-line
fix — `LoadOutcome.Loaded` is a `data object` today and can't carry data
without becoming a `data class`. The test only asserts the property that
`outcome` must not equal the bare `Loaded` singleton when a failure occurred
earlier in the same call (`assertNotEquals(LoadOutcome.Loaded, outcome)`),
deliberately not prescribing the exact fix shape (a field on `Loaded`, a
separate `LoadOutcome` case, a side-channel log — your call). Once
`loadMore()` surfaces the failure some way, update the assertion to check
for that shape specifically and remove the `@Ignore`.

---

## Production-code seams

None. A `CoroutineDispatcher` seam was added to `NetworkMonitorImpl.kt` at
one point (plus, because Dagger doesn't skip a constructor parameter just
for having a Kotlin default, a matching `@Provides` binding in
`NetworkMonitorModule.kt`), on the theory that it might be needed for a
`NetworkMonitorImplTest`. No such test was ultimately written — simulating
`ConnectivityManager` callbacks deterministically via Robolectric shadows is
real, version-sensitive effort, and `NetworkMonitorImpl` wasn't one of the
seven priority areas — so the seam had no caller. Speculative seams that
nothing uses are exactly what "smallest possible seam" rules out; reverted
both files in full.

`ArtworkRepositoryImpl` and both providers have no hardcoded dispatchers to
begin with — they get off the main thread via Retrofit/Room's own suspend
machinery — so there was nothing to inject there either.

---

## Test classes

| Class | Protects | Type |
|---|---|---|
| `ClevelandMapperTest` | Aspect-ratio parsing from quoted strings (never Infinity/NaN), CC0/has-image gatekeeper, unattributed creators, print-not-full high-res URL | unit |
| `MetMapperTest` | Public-domain/has-image gatekeeper, `aspectRatio` always null, blank-vs-null title/artist, `primaryImage`-based high-res URL | unit |
| `ApiResultHelperTest` | `worthRetrying()` classification (429/5xx retryable, 404/other 4xx/non-HTTP not) | unit |
| `MetProviderTest` | `loadIds` caches only on success, isolated vs. consecutive (3+) object failures, cursor never advances on total failure, width-2 batch preserves input order | unit (MockWebServer, Robolectric) |
| `ClevelandProviderTest` | Empty `data` array is the only exhaustion signal, a failed/null response leaves the cursor unmoved (never exhausted), skip advances by raw record count not accepted count, transient 5xx retries once | unit (MockWebServer, Robolectric) |
| `ArtworkDaoTest` | Feed order survives round-tripping through Room (position, not insertion order), composite `provider:id` keys don't collide, `maxPosition()`'s `-1` empty-table default | DAO (Robolectric) |
| `ProviderCursorDaoTest` | Cursor rows are keyed by `providerId`, independent of each other; `REPLACE` overwrites correctly | DAO (Robolectric) |
| `ArtworkRepositoryImplTest` | Sort-by-id rotation (Set has no order), turn advances only on real success, an already-exhausted provider is skipped without counting as a failure, a failed fetch never writes the exhaustion marker, insert+cursor write is one transaction (atomicity), all-failures-joined (not last-wins), silent-partial-failure bug (`@Ignore`'d) | unit + DAO (Robolectric, real in-memory Room, `FakeArtworkProvider`) |
| `ScrollViewModelTest` | Initial load happens in `init`, TailState machine (Idle→Loading→Idle/Failed), overlap guard drops a second concurrent `loadMore()`, frontier upper-clamp bug (`@Ignore`'d) | unit (fakes, coroutines-test, Turbine, Robolectric) |
| `GalleryNoticeTest` | Retry action invokes its callback; disabled while busy | UI (Robolectric Compose) |
| `DetailScreenTest` | Not-found state renders the notice, not a crash on `data == null` | UI (Robolectric Compose) |
| `ArtworkReelsScreenPageCounterTest` | Page counter hidden on the sentinel page | UI (Robolectric Compose) |

54 tests across 12 classes (two `@Ignore`'d).

### Test doubles: fakes, not MockK, for the types this app owns

Correction: an earlier version of this document said MockK became the
suite's default. That was wrong and contradicted the brief, which asks for
hand-written fakes first, MockK only where a fake would be absurd.
`ArtworkProvider`, `ArtworkRepository`, `NetworkMonitor`, and
`ArtworkPrefetcher` are all now hand-written fakes in `testutil/`:

- **`FakeArtworkProvider`** — queues `PageResult`s FIFO, tracks `callCount`.
  Used in `ArtworkRepositoryImplTest` in place of a mocked `ArtworkProvider`.
- **`FakeArtworkRepository`** — a real backing `MutableStateFlow<List<Artwork>>`
  for `artworks()`, plus settable `loadMoreOutcome`/`loadMoreDelayMs`/`loadMoreThrows`
  and a `loadMoreCallCount` field. Used in `ScrollViewModelTest`.
- **`FakeNetworkMonitor`** / **`FakeArtworkPrefetcher`** — trivial one-member
  fakes for `ScrollViewModelTest`'s other two collaborators. These weren't
  named in the fakes-conversion request but were converted anyway: both are
  simple enough that there was no concrete reason to keep them mocked, and
  "MockK only where you can name a concrete reason" cuts against leaving an
  easy case mocked just because it wasn't called out by name.

**Still MockK, with a stated reason each:**

- **`FeedPositionSource`** (`ScrollViewModelTest`) and **`ProviderTurnSource`**
  (`ArtworkRepositoryImplTest`) — both are `final` Kotlin classes whose
  `@Inject constructor` takes a real `@ApplicationContext context: Context`.
  A hand-written fake would have to subclass and satisfy that constructor —
  with a real or mocked `Context` — just to compile, which reintroduces
  exactly the Android dependency a mock sidesteps entirely. Mocking the
  class directly (MockK handles `final` Kotlin classes without any extra
  setup) is strictly less code and no less honest.

**Real instance, not a double at all:** `ArtworkRepositoryImplTest`'s
`seedSource` parameter is a genuine `SeedSource()` — the class takes no
dependencies and nothing in `loadMore()` calls it (`seedIfEmpty()` is
commented out in production), so faking or mocking it would be pure
ceremony.

**The one place a real collaborator was mandatory, not a fake:**
`ArtworkRepositoryImplTest` uses a real in-memory Room database (Robolectric)
because `database.withTransaction {}` is an extension function on
`RoomDatabase` — it cannot be meaningfully mocked or faked, and the
atomicity test specifically needs real transactional rollback behavior. A
`ThrowingInsertArtworkDao` (Kotlin interface delegation over the real DAO,
overriding only `insertAll`) stands in for "insert fails partway through" in
that one test — not a general-purpose fake, a one-test saboteur.

---

## Dependencies added

All in `gradle/libs.versions.toml`, referenced via the catalog:

| Dependency | Version | Why |
|---|---|---|
| `kotlinx-coroutines-test` | 1.10.2 (matches `kotlinxCoroutines`) | `runTest`, `TestDispatcher`, virtual-time `delay()` skipping |
| `okhttp3:mockwebserver` | 4.12.0 (matches `okhttp`) | Real HTTP layer for provider tests |
| `turbine` | 1.2.0 | Flow/StateFlow assertions in `ScrollViewModelTest` |
| `robolectric` | 4.15.1 | In-memory Room + Compose UI tests + `android.util.Log.d` call sites running under `./gradlew test` instead of a device |
| `mockk` | 1.13.13 | Mocking two final classes with real-`Context` constructors (see [Test doubles](#test-classes) above) |

Also added `testImplementation(libs.androidx.compose.ui.test.junit4)` and
`testImplementation(libs.androidx.junit)` (both already in the catalog for
`androidTest`, now also wired into `testImplementation` for Robolectric-based
Compose tests), and one `android.testOptions.unitTests` flag in
`app/build.gradle.kts`:

- `isIncludeAndroidResources = true` — required for Robolectric to resolve
  real themes/resources (Room's SQLite driver, Compose Material3 theming).

**`isReturnDefaultValues` was tried and removed.** It's suite-wide and masks
*any* unmocked Android SDK call, not just the ones a test actually exercises
— too blunt for what it was covering. Removed it and ran the suite to find
exactly what broke: 11 tests, in exactly 3 classes (`MetProviderTest`,
`ClevelandProviderTest`, `ScrollViewModelTest`), all with the identical
stack trace bottoming out in `android.util.Log.d(Log.java)` — `MetProvider`,
`ClevelandProvider`, and `ScrollViewModel` all call `Log.d(...)` directly. A
handful of call sites, not pervasive, so per-class Robolectric was the
better fix: all three now carry `@RunWith(RobolectricTestRunner::class)` +
`@Config(sdk = [34])`, same as the DAO/repository/Compose tests. Real
`Log.d` calls now execute against Robolectric's shadow instead of either
throwing or being silently no-op'd suite-wide.

Removed `app/src/test/java/com/kg/museumly/ExampleUnitTest.kt` (the stock
`assertEquals(4, 2 + 2)` boilerplate) — superseded entirely.

---

## Suite audit

The plan estimated ~28-30 tests; 55 got written before this audit. Went
back through every test asking "would this catch a real regression, or
document a rule a future reader wouldn't otherwise know" — the same bar the
brief set — rather than just accepting the count.

**Cut 1: `MetMapperTest`'s `` `id is prefixed with the met provider id` ``.**
It asserted `"met:" + dto.objectID` — string concatenation, no branching, no
edge case. The invariant it looked like it was protecting (Met and
Cleveland ids can't collide in Room) is already tested where it actually
matters: `ArtworkDaoTest`'s `` `composite provider-prefixed ids do not collide between providers` ``
inserts both a `met:123` and a `cleveland:123` row and checks Room kept them
distinct. That's the test that would actually fail if someone broke the
prefixing; this one was just restating the same fact one layer up, with no
logic of its own. Deleted rather than kept for "coverage."

**Kept everything else**, including near-looking pairs that turned out not
to be redundant on a closer read:

- `MetMapperTest`'s missing-vs-blank `primaryImageSmall` tests both hit the
  same `isNullOrBlank()` call today, but they guard against different
  regressions — someone narrowing that check to `== null` only would pass
  the missing-case test and fail the blank-case one. Same reasoning applies
  to `blank primaryImage maps to null high res url` alongside the
  non-blank case: they're the two sides of one `takeIf` boundary, not a
  duplicate.
- `ClevelandMapperTest`'s zero-height and non-numeric/missing-height tests
  hit two different guard clauses in `aspectRatioOf` (`toFloatOrNull() ?: return null`
  vs. `width <= 0f || height <= 0f`), not the same one twice.
- `ArtworkRepositoryImplTest`'s "turn advances only on success" /
  "turn does not advance when a page comes back OK but empty" and
  "an already-exhausted provider is skipped" / "a failed fetch never
  persists the exhaustion marker" are each two tests on opposite branches
  of one `if`, or on opposite ends of the same bug class (read-side vs.
  write-side of cursor poisoning) — complementary, not duplicated.
- The DAO tests that look like "just Room doing its job"
  (`ProviderCursorDaoTest`'s `REPLACE` test, `ArtworkDaoTest`'s
  `maxPosition()` default) each pin a specific annotation/query choice
  (`OnConflictStrategy.REPLACE`, `COALESCE(MAX(position), -1)`) that the
  source code itself calls out with a comment as deliberate and
  non-obvious — changing either would break every future `loadMore()` call
  or the first-ever insert, silently.

54 tests remain after the one cut (see the [Known bugs](#known-bugs) section
for why the count went up by one elsewhere — the new silent-partial-failure
test — before this audit brought it back down).

---

## How to run

```
# everything
./gradlew test

# one class
./gradlew :app:testDebugUnitTest --tests "com.kg.museumly.data.ArtworkRepositoryImplTest"

# one test
./gradlew :app:testDebugUnitTest --tests "com.kg.museumly.data.ArtworkRepositoryImplTest.turn advances only on success"
```

(`./gradlew test` is an alias that runs `testDebugUnitTest` and
`testReleaseUnitTest`; use the `:app:testDebugUnitTest` form for `--tests`
filtering on a single variant.)

---

## How to read this suite

Start with these three — between them they cover every test-double approach
used elsewhere in the suite:

1. **`ClevelandProviderTest`** (MockWebServer) — the most concrete starting
   point. Real HTTP requests hit a local `MockWebServer`, and the assertions
   are on real `PageResult` values coming back through real JSON parsing.
   Read `` `skip advances by raw dtos size, never by accepted count` `` first
   — it's a good example of a test that would fail immediately against a
   plausible-looking "simplification" (advancing skip by the accepted-item
   count instead) without a comment explaining why that's wrong.

2. **`ArtworkRepositoryImplTest`** (fakes + real Room) — read
   `` `insert and cursor write are one transaction, so a failed insert leaves no cursor behind` ``.
   It's the one test in the suite that deliberately breaks a collaborator
   (`ThrowingInsertArtworkDao`) to prove a guarantee holds under failure,
   and it's the clearest example of "use the real thing where the real
   thing's behavior is what's under test, fake everything else."

3. **`ScrollViewModelTest`** (ViewModel + Turbine) — read
   `` `loadMore transitions tail from idle to loading to idle on success` ``.
   Shows the `MainDispatcherRule` + `UnconfinedTestDispatcher` +
   `Turbine.test {}` pattern for asserting an intermediate state
   (`Loading`) that only exists for the duration of a suspended call —
   without Turbine and a real suspension point (`coAnswers { delay(100); ... }`),
   this state is invisible to a test.
