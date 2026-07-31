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

**Phases 0–4 complete and verified building:** foundation, indexing engine, Shelf/Search/Detail UI, permissions with Limited Mode, and Cleanup with the widget, tile and share target.

| Check | Result |
|---|---|
| `:app:assembleDebug` | passing |
| `:app:assembleRelease` (R8 full mode + resource shrinking) | passing |
| `:app:bundleRelease` (AAB) | passing |
| `lintVitalRelease` | passing, 0 errors |
| Unit tests | **144 passing**, 0 failures |
| Download size, arm64-v8a | **7.2 MB** (budget 15 MB) |
| Room migration v1→v2 | auto-generated, verified additive |
| Download size, armeabi-v7a | **6.3 MB** |
| `WRITE_EXTERNAL_STORAGE` | absent — deletion uses `createDeleteRequest` |
| `INTERNET` permission in shipped APK | **absent**, verified with `aapt2 dump permissions` |
| compileSdk / targetSdk | 37 / 36 — meets the Aug 31 2026 Play requirement |

Test breakdown: `:core:classify` 59, `:core:model` 44, `:core:media` 14, `:core:datastore` 8, `:feature:shelf` 8, `:core:ocr` 7, `:feature:onboarding` 4.

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

**Phase 2 — Shelf, Search and Detail UI**
- Paged shelf grid with **date separators inserted over the paged stream**
  (`insertSeparators`), so grouping never loads the whole library. Adaptive
  columns, so it widens on tablets and foldables.
- Tiles show the **extracted value** (amount, OTP, PNR) rather than a filename,
  plus **one action chip** — so the index has an obvious next step instead of
  being a dead end.
- Category filter chips built from the user's *actual* library, only surfacing a
  category once it has enough matches.
- Search with a 200ms debounce and **match highlighting**, so the user can see
  *why* a result matched. Query tokenisation is shared between the FTS query and
  the highlighter via `:core:model`, so they can never disagree.
- Detail bottom sheet rendered as an **overlay**, keeping the shelf visible
  behind it: selectable OCR text, tap-to-copy entity chips, and an action row.
- Action layer producing real intents (open link, copy, dial, share, calendar
  insert with a best-effort parsed date), every one guarded so a device with no
  dialler or browser shows a message rather than crashing.
- Non-blocking, dismissible index status strip — never a modal progress dialog.
- User sorting rules persisted, created inline from the detail sheet.

**Phase 3 — permissions, onboarding and Limited Mode**
- Three-screen onboarding: problem, then **trust before any system dialog**, then
  the permission rationale. No account, no paywall, roughly fifteen seconds.
- Permission state machine covering all four states — full, **partial**
  (`READ_MEDIA_VISUAL_USER_SELECTED` on Android 14+), denied, and
  revoked-mid-session. Access is re-read on resume, never cached.
- **Limited Mode**, required by Play's Photo and Video Permissions policy: the app
  stays fully functional over photo-picker-selected images. Search, categories and
  actions behave identically.
- Picker imports are **copied into app-private storage before indexing**, because
  photo-picker grants expire with the process and cannot be made persistable.
  Without the local copy those tiles would be blank on the next launch.
- Schema v2 via Room **auto-migration**, verified purely additive: two columns
  added, no table recreate, no data loss.

**Phase 4 — Cleanup, widget and share target**
- **Duplicate detection** via a 64-bit dHash perceptual hash, with near-duplicate
  merging by Hamming distance so re-compressed copies are caught. The oldest of
  each group is always kept and never offered for deletion.
- **Blur detection** via Laplacian variance. Both algorithms are pure functions in
  `:core:classify` with 15 tests; the Android side decodes at ~64px, so analysis
  is nearly free and runs during indexing.
- Cleanup screen with **preview-before-delete**, honest reclaimable-storage figures
  in binary units matching Android's own storage settings, and select-all.
- Two-stage deletion: soft delete into the **30-day Recently Deleted** window
  first, then `MediaStore.createDeleteRequest` for the user's system confirmation.
  Declining the dialog restores the rows, so cancelling really cancels.
- **No `WRITE_EXTERNAL_STORAGE`.** On Android 10 and below, deleting other apps'
  media would require it, so those devices get index-only cleanup and the UI says
  so plainly rather than silently failing.
- **Home-screen widget** and **Quick Settings tile**, both deep-linking into
  Search — re-entry points that cost no notification. Built with RemoteViews, so
  no new dependency.
- **Share-sheet target** for images, which doubles as a way to add screenshots in
  Limited Mode without opening the picker.

### Not built yet

Phase 5 onward: Play Billing with the one-time unlock, the free-tier gate, the
Settings screen with a rule editor and Recently Deleted UI, the category picker in
the detail sheet, Baseline Profiles, and the accessibility and localisation passes.
Settings still renders a placeholder. See `docs/05-roadmap-and-tasks.md`.

**Not code, but blocking release:** the broad photo-access declaration and the
Data Safety form must be submitted in Play Console, and the privacy policy hosted
at a public HTTPS URL. Ready-to-paste content is in
`docs/06-play-store-compliance.md` and `legal/privacy-policy.md`.

### Not yet verified

Nothing has run on a physical device or emulator — only build, lint and unit
tests. Specifically unverified: real OCR accuracy on real screenshots, the
sub-10-second Tier 1 target, the actual memory ceiling, grid scroll performance
against a 5,000-item library, and whether the idle+charging constraints behave
as intended across OEM skins.

No Compose UI tests yet, so the screens are compile-verified only. The 118 unit
tests cover pure logic: classification, entity extraction, search tokenisation
and highlighting, date parsing, OEM path heuristics, subsample maths and rule
encoding.
