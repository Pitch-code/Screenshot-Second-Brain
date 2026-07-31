# Shelfie — Build & Launch Plan

Android app that makes every screenshot on your phone searchable and actionable, entirely on-device, with no account and no subscription.

**Status:** planning complete, no code written yet.

## Read in this order

| Doc | What's in it |
|---|---|
| [`docs/01-competitive-analysis.md`](docs/01-competitive-analysis.md) | The 10 live competitors, Google's own Pixel Screenshots, review-derived weaknesses, and where the real opening is |
| [`docs/02-brand-name-logo.md`](docs/02-brand-name-logo.md) | Name rationale, alternates, package ID, logo spec, colour, asset requirements, voice |
| [`docs/03-product-and-ui-spec.md`](docs/03-product-and-ui-spec.md) | Feature scope, non-goals, category taxonomy, screen-by-screen UI, Material 3 Expressive system, habit hooks |
| [`docs/04-architecture-and-performance.md`](docs/04-architecture-and-performance.md) | Stack, module map, three-tier indexing pipeline, performance budgets, 12-item crash checklist |
| [`docs/05-roadmap-and-tasks.md`](docs/05-roadmap-and-tasks.md) | 8 phases, 40 tasks with explicit ownership, gates, timeline, critical path |
| [`docs/06-play-store-compliance.md`](docs/06-play-store-compliance.md) | Deadlines, the photo-permission declaration, Data Safety answers, pre-submission checklist |
| [`legal/privacy-policy.md`](legal/privacy-policy.md) | Publishable policy template *(needs legal review)* |
| [`store/listing-copy.md`](store/listing-copy.md) | Title, descriptions, screenshot sequence, ASO targets |

## The five decisions that define this app

1. **Target the phones Google ignored.** Pixel Screenshots is Pixel 9+ only. Everything here is built for a ₹10,000 device.
2. **Win on time-to-first-value, not on AI.** Searchable shelf in under 10 seconds on a 5,000-screenshot library. This is where every competitor fails.
3. **Ship with no `INTERNET` permission.** Turns "we're private" from a claim into a fact the user can verify. This is the moat.
4. **One-time payment, no subscription, no ads, no login.**
5. **Deterministic classifier as the core; on-device GenAI only as a bonus tier** on capable hardware.

## Three things to do before writing any code

1. **Trademark-check the name** — Play Store, [Indian IP](https://tmrsearch.ipindia.gov.in/tmrpublicsearch/), [USPTO](https://tmsearch.uspto.gov/), domain. I could not verify this.
2. **Register Android developer verification** — deadline **Sept 30, 2026**, or apps face removal from Play. Pure paperwork, do it now.
3. **Collect ~200 real screenshots** as a classifier fixture corpus. Task 1.4 cannot be tuned without it.

## Two hard deadlines

- **Aug 31, 2026** — new apps and updates must target Android 16 / API 36
- **Sept 30, 2026** — developer verification registration closes


---

## Build status

**Phase 0 (Foundation) and Phase 1 (Indexing engine) are complete and verified building.**

| Check | Result |
|---|---|
| `:app:assembleDebug` | passing |
| `:app:assembleRelease` (R8 full mode + resource shrinking) | passing |
| `:app:bundleRelease` (AAB) | passing |
| `lintVitalRelease` | passing, 0 errors |
| Unit tests | **72 passing**, 0 failures |
| Download size, arm64-v8a | **6.9 MB** (budget 15 MB) |
| Download size, armeabi-v7a | **6.0 MB** |
| `INTERNET` permission in shipped APK | **absent**, verified with `aapt2 dump permissions` |
| compileSdk / targetSdk | 37 / 36 — meets the Aug 31 2026 Play requirement |

Test breakdown: `:core:classify` 44, `:core:media` 14, `:core:model` 7, `:core:ocr` 7.

> **On APK size:** a *universal* release APK is ~43 MB, because the bundled ML Kit
> model ships a 6–11 MB native library for each of four ABIs. Play delivers only
> one, so real download is ~6–7 MB. Always measure with `bundletool get-size`, not
> by looking at the universal APK.

### Toolchain

- AGP 9.3.1, Gradle 9.6.1, Kotlin 2.4.10, KSP 2.3.10, Hilt 2.60.1
- JDK 21, Android SDK Platform 37.0, Build Tools 37.0.0
- AGP 9 provides **built-in Kotlin support** — do not apply `org.jetbrains.kotlin.android`

### Building locally

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK
./gradlew test                      # all unit tests
./gradlew :app:assembleRelease      # R8 + lint vital
./gradlew :app:bundleRelease        # AAB for Play
```

### Verifying the privacy claim

The core guarantee is that the app has no `INTERNET` permission, so it cannot
upload anything. Re-check on every release — one transitive dependency can
reintroduce it:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

`android.permission.INTERNET` must not appear. The manifest also carries an
explicit `tools:node="remove"` for it, which strips it even if a library declares it.

Four permissions **are** merged in from WorkManager and are disclosed in the
privacy policy: `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE`. None can transmit data without `INTERNET`.

Do **not** tell users to confirm this in Android's Settings → Permissions screen.
That screen lists only runtime permissions, so `INTERNET` never appears there
either way.

### What exists so far

**Phase 0 — foundation**
- 9 → 12 Gradle modules wired through 7 local convention plugins, so SDK levels,
  Java level, Compose and Hilt setup are declared exactly once
- Room schema with an **FTS4** search table, soft delete for the 30-day recovery
  window, and enum-name-based type converters
- Material 3 Expressive theme, dark-first, dynamic colour
- Adaptive shell: NavigationBar under 600dp, NavigationRail above
- Vector launcher icon with foreground, background and **monochrome** layers

**Phase 1 — indexing engine**
- `:core:classify` — pure-Kotlin entity extraction (amounts, OTPs, UPI refs, PNRs,
  phones, dates, Wi-Fi passwords) and a weighted category scorer, India-weighted.
  User rules always override the built-in scorer. 44 tests.
- `:core:ocr` — ML Kit bundled Latin recogniser with bounds-only decode,
  power-of-two downsampling to ~1440px, `RGB_565`, and a single-permit semaphore
  so only one bitmap is ever in flight. This is the OOM-crash prevention.
- `:core:media` — MediaStore discovery with multi-OEM screenshot heuristics,
  a persisted watermark that makes detection self-healing when ContentObserver
  events are dropped, orphan pruning, and the **three-tier scheduler**:
  Tier 1 (60 newest, foreground), Tier 2 (500, background),
  Tier 3 (backlog, chunked, **idle + charging only**).

### Not built yet

Phase 2 onward: the real Shelf grid and Search UI, detail sheet and actions,
onboarding and permission flow, **Limited Mode** (required by Play policy),
Cleanup, billing, widget. Feature screens still render a shared placeholder.
See `docs/05-roadmap-and-tasks.md`.

### Not yet verified

Nothing has run on a physical device or emulator — only build, lint and unit
tests. Specifically unverified: real OCR accuracy on real screenshots, the
sub-10-second Tier 1 target, actual memory ceiling, and whether the
idle+charging constraints behave as intended across OEM skins.
