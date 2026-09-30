# Cold start flash

Status: **open**. Diagnosed on 2026-10-01; the fix below has not been applied yet.

## Symptom

On a cold start (app closed from recents, then reopened) there is a visible
flash just before the first artwork appears: the screen looks empty, then the
gallery draws.

## How it was diagnosed

Measured on the `sdk_gphone64_x86_64` emulator, debug build.

1. **Temporary logs** in the `ScrollViewModel` `combine` block and in
   `AsyncImage`'s `onState`. They showed that the ViewModel delivers a complete
   first state (artworks and `initialPage` together) about 90 ms after the
   window appears. There's no intermediate `initialPage = null` frame, so the
   ViewModel isn't the cause.
2. **Rapid screenshots** (`adb exec-out screencap`, about every 200 ms)
   during a cold start.
3. **Slow motion**: the same screenshots with
   `adb shell settings put global animator_duration_scale 10`, so each
   animation stretches over several frames. Reset afterwards with `... 1`.

## What was ruled out or already fixed

| Suspect | Finding | Status |
|---|---|---|
| Heavy `init` / `enter()` in the ViewModel | Both only launch coroutines and do their work off the main thread. First state arrives about 90 ms after the window is shown. | Not the cause |
| Black frame from `initialPage == null` | Never observed in the logs. The first state always carries the page. | Not the cause |
| Landing image decoding after the page draws | The image took about 140 ms (disk read + decode). A prefetch in `enter()` brings this to about 3 ms when it wins the race, but it doesn't always win. | Improved, but not the main cause |
| Hard cut from loading screen to pager | Replaced with an `AnimatedContent` crossfade. | Done |
| **Light system splash** (`Theme.Material.Light`) turning into the black app | Near-white splash, then black app: a large brightness jump. | **Fixed**: dark theme with a black `windowBackground` |

The theme fix removed the brightness jump, but a smaller flash remains.

## Root cause of what remains

The slow-motion frames show this sequence:

1. The **system splash icon** stays up until the app draws its first frame.
2. By then **the data is already ready**: the first state arrives about 90 ms
   after the window appears, while the splash is still on screen. The
   "Hanging the work" loading screen exists for only a frame or two,
   underneath the splash, and is never actually seen.
3. The splash runs its **own exit animation** (the icon fades out).
4. Meanwhile our `AnimatedContent` crossfade starts from the loading screen,
   which is effectively black. So it acts as a **fade in from black**.

What the user sees at normal speed:

```
splash icon  →  icon fades out  →  BLACK  →  painting fades up over 300 ms
```

In a frame captured at normal speed, the screen is completely black, with no
content, no section pill and no text, between the splash and the artwork.
The crossfade doesn't smooth the cold start. It adds a black gap after the
splash, because nothing visible was on screen to fade from.

## Proposed solutions

### 1. Keep the splash until the gallery is ready (recommended)

Use the AndroidX SplashScreen library:

- Add `androidx.core:core-splashscreen`.
- In `MainActivity.onCreate`, call `installSplashScreen()` before
  `super.onCreate`, and give it `setKeepOnScreenCondition { … }`, which
  returns `true` while the first real state hasn't arrived yet.
- Condition: **`uiState.value.section == null`**. That covers DataStore plus
  one Room query (about 100 ms) and nothing slower.

Result:

- **Normal cold start:** splash → finished gallery. `AnimatedContent` doesn't
  animate the content it starts with, so there's no fade from black.
- **First install / empty cache:** the splash lifts once the section is known,
  then the loading screen shows during the real network load, and the
  crossfade into the gallery runs as intended.
- **Section switches:** unaffected.

Things to watch out for:

- The condition must stop waiting at some point. Don't make it wait for
  network data. With an empty Room and a slow network, the splash would hang
  for seconds. `section != null` has no network dependency.
- The condition needs the `ScrollViewModel`, which is created inside the nav
  graph. Either obtain it at activity level (`by viewModels()` with Hilt,
  giving the same instance only if the nav destination scopes to the activity),
  or expose a small activity-level "first frame ready" signal. Decide which
  before implementing.

### 2. Skip the crossfade for the first appearance only

Make `ReelsContent` not animate the first change out of `Loading` if the
loading screen was on screen for less than, say, 100 ms. This removes the fade
from black without touching the splash. It's less clean: it adds timing logic
to the UI and still shows the splash's own exit into a black frame.

### 3. Custom splash exit animation

`splashScreen.setOnExitAnimationListener { … }` lets the splash icon fade into
the gallery instead of into black. It's best combined with option 1. On its
own it doesn't help, because the gallery isn't drawn yet when the splash exits.

**Recommendation:** option 1, optionally followed by option 3 for polish.

## Related notes

- The splash shows the **default green Android icon**. The app has no launcher
  icon yet, which makes the splash look generic regardless of timing.
- `Choreographer: Skipped 51–61 frames!` is logged on every cold start (about
  1 s of main-thread work before the first frame). It happens while the
  splash is showing, so it isn't the flash. Recheck in a **release build**
  before investigating, since debug builds on the emulator start much slower.
- **Landing-page prefetch** (`ScrollViewModel.enter()`): decide whether to keep
  it once option 1 is in. Compare a cold start with and without it. If there's
  no visible difference, remove it, together with the `(0..2)` offset in
  `onPageChanged`, which only exists so the prefetcher doesn't cancel it.