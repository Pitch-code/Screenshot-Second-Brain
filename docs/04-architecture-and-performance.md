# Architecture, Performance & Crash Prevention

The entire competitive thesis is *this app is fast and doesn't crash on a cheap phone*. So performance is not a polish phase — it's the architecture.

---

## 1. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x | — |
| UI | Jetpack Compose + Material 3 Expressive (Material Components 1.14.0+) | Current design language |
| Min / Target SDK | **min 26, target 36 (Android 16)** | Target 36 is **mandatory for new apps and updates from Aug 31, 2026** ([source](https://quasa.io/media/google-play-s-target-sdk-policy-a-gradual-cleanup-that-opens-the-door-for-modern-apps)). min 26 unlocks GenAI Image Description later and covers >98% of devices |
| DI | Hilt | — |
| Async | Coroutines + Flow | — |
| DB | Room + **FTS4** virtual table | On-device full-text search |
| Paging | Paging 3 | Never load a 5,000-item grid into memory |
| Images | Coil 3 | Thumbnail-only decoding |
| Background | WorkManager | Constraint-aware backlog indexing |
| OCR | **ML Kit Text Recognition v2** (on-device, API 21+) | [docs](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) |
| GenAI (optional tier) | ML Kit GenAI via AICore / Gemini Nano | [docs](https://developers.google.com/ml-kit/genai) |
| Billing | Play Billing, one-time product | — |
| Analytics | **None** | No `INTERNET` permission |

### The manifest is the marketing
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Deliberately absent: INTERNET, ACCESS_NETWORK_STATE -->
```
Omitting `INTERNET` is a hard architectural commitment. It means no Firebase, no Crashlytics, no ad SDK, no remote config. Accept it — it is the moat. Rely on Play Console vitals (collected by the OS, not by us) for crash data.

> **Note:** if you later add the GenAI tier, verify whether AICore model provisioning on your minimum target devices requires network from *your* process. It normally doesn't — AICore is a system service — but validate on real hardware before shipping, because it would break the no-internet promise.

---

## 2. Module map

```
:app                    Application, MainActivity, nav host, DI wiring
:core:model             Pure Kotlin data classes. Zero Android imports
:core:database          Room entities, DAOs, FTS table, migrations
:core:datastore         Preferences, purchase state, rule storage
:core:designsystem      Theme, tokens, reusable composables. No feature logic
:core:media             MediaStore queries, ContentObserver, screenshot detection
:core:ocr               ML Kit wrapper behind an interface
:core:classify          Rule engine + entity extractors. Pure, heavily unit-tested
:core:genai             AICore/Gemini Nano behind interface + capability check
:feature:onboarding
:feature:shelf
:feature:search
:feature:cleanup
:feature:detail
:feature:settings
:feature:widget         Glance widget + QS tile
:benchmark              Macrobenchmark + Baseline Profile generator
```

**Hard rule: no `:feature:*` module may depend on another `:feature:*`.** Shared code moves down into `:core:*`. This is what prevents the duplicated-work problem across the roadmap.

`:core:classify` being pure Kotlin with no Android dependency is deliberate — it's the highest-risk logic and must be testable on the JVM in milliseconds.

---

## 3. The indexing pipeline — where competitors die

### Design principle
**Time-to-first-value under 10 seconds, regardless of library size.**

### Three-tier scheduling

```
TIER 1 — FOREGROUND, IMMEDIATE (target: <10s)
  Query MediaStore for the 60 newest screenshots
  OCR + classify them inline, emit to UI as each completes
  User sees a populated, searchable shelf almost at once

TIER 2 — EXPEDITED WORKER (target: a few minutes)
  Next ~500 newest
  WorkManager expedited work, cancellable, progress surfaced in status strip

TIER 3 — DEFERRED BACKLOG (hours/days, invisible)
  Everything older
  Constraints: requiresDeviceIdle(true) + requiresCharging(true) + requiresBatteryNotLow(true)
  Chunked into batches of 50 with checkpointing, so it resumes cleanly
```

Tier 3's constraints are the single most important line of code in the app. They make the difference between "this app organised my screenshots" and "this app melted my phone".

### Detecting new screenshots
- Register a `ContentObserver` on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.
- **Do not trust the observer alone** — it is unreliable across OEM skins and is not delivered while the process is dead. Every observer callback is a *hint*, not a source of truth.
- Source of truth: a **`generation_id` / `date_added` watermark** persisted in DataStore. On every app start, worker run, and observer callback, query for `date_added > watermark` and reconcile. This makes missed events self-healing.
- Identify screenshots by `RELATIVE_PATH` containing `Screenshots` **plus** an `is_screenshot`-style heuristic (dimensions matching display metrics), because OEMs use different folder names (`Screenshots`, `ScreenCapture`, `Screenshot`).
- Add a periodic `WorkManager` reconcile job (every 6h, idle+charging) as the final safety net.

### Per-image OCR — the crash-prevention detail
```
1. Read bounds only (inJustDecodeBounds) — never decode full size first
2. Compute inSampleSize so the longest edge lands ~1440px
   (below ~1000px OCR accuracy drops; above ~2000px is wasted memory)
3. Decode with RGB_565 where alpha isn't needed — halves memory
4. Feed InputImage to ML Kit
5. recycle() immediately, single-image-in-flight semaphore
6. Persist text + entities; NEVER cache full bitmaps
```
A 1440p screenshot at ARGB_8888 is ~11MB. Ten in flight on a 3GB device is an OOM crash. **Downsample before decode, one at a time, always.**

### Classification: deterministic core, AI as garnish
ML Kit GenAI runs on Gemini Nano via AICore, which exists only on higher-end hardware (Pixel 9/10, Galaxy S-class). Our target market is budget devices.

Therefore:
- **Core classifier = regex + weighted keyword scoring over OCR text, in `:core:classify`.** Pure Kotlin, deterministic, instant, works on every device, unit-testable.
- **User rules layer on top**, and always win over built-in detectors.
- **GenAI tier is strictly additive**, behind a capability check, feature-flagged off by default:
  - natural-language search
  - one-line summaries for long screenshots
  - `Image Description` for screenshots with little or no text (requires API 26+)
- Every GenAI call needs a non-AI fallback path. **Never let a feature exist only on flagships** — and never advertise it on the store listing unless you qualify the device requirement.

---

## 4. Performance budgets — enforced in CI, not aspirational

| Metric | Budget | How |
|---|---|---|
| Cold start (P90, 4GB device) | **< 500ms** | Baseline Profiles, no work in `Application.onCreate`, lazy Hilt graph |
| Time to populated shelf | **< 10s** on 5,000-image library | Tier 1 pipeline |
| Frozen frames | **0** | Macrobenchmark on Shelf scroll |
| Jank (P90 frame) | < 16ms | Paging 3 + stable keys + `Modifier.animateItem` |
| APK download size | **< 15MB** | R8 full mode, unbundled ML Kit model, no Firebase |
| Memory (P90) | < 180MB | Downsample-before-decode, single in-flight |
| Battery | No "excessive background" flag in Play vitals | Tier 3 idle+charging constraints |

Implementation notes:
- **Ship Baseline Profiles.** Generate via `:benchmark` module. Typically 20–30% faster cold start for free.
- **R8 full mode** on, with `-keep` rules verified for ML Kit and Room.
- Use the **unbundled** (Play-services) ML Kit text recognizer to keep APK small; handle the model-not-yet-downloaded state gracefully. If your audience is heavily offline-first, reconsider and bundle the Latin recognizer — but measure the size delta first.
- **Test on a real 4GB budget device.** An emulator will hide every problem this document is about.

---

## 5. Crash-prevention checklist

Ranked by likelihood in this specific app:

1. **OOM on bitmap decode** → downsample before decode, `RGB_565`, one in flight, `recycle()`
2. **ANR from MediaStore query on main thread** → all cursor work on `Dispatchers.IO`, always
3. **`SecurityException` after permission revoked mid-session** → re-check permission at every access point; never cache the granted state
4. **Stale `MediaStore` IDs** (user deleted the file elsewhere) → treat every file read as fallible; prune orphaned rows on reconcile
5. **`RecoverableSecurityException` on delete (Android 10+)** → use `MediaStore.createDeleteRequest`, handle the user declining
6. **Room migration failures** → explicit `Migration` classes with tested paths; never `fallbackToDestructiveMigration` in production (it silently wipes user data)
7. **ML Kit model unavailable / download pending** → surface "preparing" state, retry with backoff, never crash
8. **Worker exceeding its execution window** → chunk to 50, checkpoint, return `Result.retry()`
9. **AICore absent or model unloadable** → capability check first, silent fallback
10. **Cursor leaks** → `use { }` on every cursor, no exceptions
11. **Partial media access (Android 14+)** → handle `READ_MEDIA_VISUAL_USER_SELECTED` as a first-class state, not an error
12. **Config change / process death during indexing** → all progress in Room, never in memory

### Testing gate before every release
- Unit tests on `:core:classify` with a fixture corpus of real screenshot text (aim for 200+ samples)
- Room migration tests
- Macrobenchmark: cold start + Shelf scroll
- Manual: 5,000-image library on a 4GB device, permission-denied path, permission-revoked-mid-session path, airplane mode, storage-full
- StrictMode enabled in debug, with disk/network violations failing loudly
