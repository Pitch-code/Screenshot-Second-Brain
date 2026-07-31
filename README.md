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

**Phase 0 (Foundation) is complete and verified building.**

| Check | Result |
|---|---|
| `:app:assembleDebug` | passing |
| `:app:assembleRelease` (R8 full mode + resource shrinking) | passing |
| `lintVitalRelease` | passing, 0 errors |
| Unit tests | 7 passing (`:core:model`) |
| Release APK size (unsigned) | **1.1 MB** — budget is 15 MB |
| `INTERNET` permission in shipped APK | **absent**, verified with `aapt2 dump permissions` |
| compileSdk / targetSdk | 37 / 36 — meets the Aug 31 2026 Play requirement |

### Toolchain

- AGP 9.3.1, Gradle 9.6.1, Kotlin 2.4.10, KSP 2.3.10, Hilt 2.60.1
- JDK 21, Android SDK Platform 37.0, Build Tools 37.0.0
- AGP 9 provides **built-in Kotlin support** — do not apply `org.jetbrains.kotlin.android`

### Building locally

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK
./gradlew test                      # unit tests
./gradlew :app:assembleRelease      # R8 + lint vital
```

### Verifying the privacy claim

The strongest asset in this project is that the app has no network permission at all.
It is worth re-checking on every release, because a single transitive dependency can reintroduce it:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

`android.permission.INTERNET` must not appear. The manifest also carries an explicit
`tools:node="remove"` for it, which strips the permission even if a library declares it.

### What exists so far

- 9 Gradle modules wired through 7 local convention plugins, so SDK levels, Java level,
  Compose and Hilt setup are declared exactly once
- Room schema with an **FTS4** search table, the three-tier index work queue, soft-delete
  for the 30-day recovery window, and enum-name-based type converters
- `ShelfiePreferences` holding the MediaStore watermark that makes screenshot detection self-healing
- Material 3 Expressive theme, dark-first, dynamic colour, brand palette
- Adaptive shell: NavigationBar under 600dp, NavigationRail above, four destinations navigating
- Vector launcher icon with foreground, background and **monochrome** layers

### Not built yet

Phase 1 onward: MediaStore querying, ContentObserver, ML Kit OCR, the classifier,
the real Shelf/Search/Cleanup UI, permissions flow, Limited Mode, billing, widget.
See `docs/05-roadmap-and-tasks.md`.
