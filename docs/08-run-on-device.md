# Running Shelfie on a real phone

Everything in this project is compile-verified and unit-tested. **Nothing has
ever launched.** This document gets it onto a device and tells you what to check
first, in priority order.

Verified facts about the artifacts:

| | |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, **~64 MB**, already signed (v2 scheme) |
| Debug package name | **`com.shelfie.app.debug`** — note the suffix, it matters for `adb` |
| Release package name | `com.shelfie.app` |
| Minimum Android | **8.0 (API 26)** |
| Target | API 36 |

> The debug APK is 64 MB because it is universal (all four CPU architectures) plus
> debug tooling. That is **not** what users download — Play delivers ~7.3 MB. Don't
> be alarmed by it.

---

## Option A — Android Studio (easiest, recommended)

1. Install **Android Studio Otter 3 Feature Drop or newer**. Earlier versions do
   not understand AGP 9 and will refuse to sync.
2. `File → Open` → select the repository root.
3. Let it sync. It will install the SDK components it needs.
4. Connect your phone by USB with **USB debugging** enabled
   (Settings → About phone → tap *Build number* 7 times → Developer options → USB debugging).
5. Pick your device in the toolbar dropdown and press **Run** (▶).

That's it. Skip to [What to test first](#what-to-test-first).

---

## Option B — Command line

### 1. Prerequisites

**JDK 21.** AGP 9 needs 17 or newer; this project was built and verified on 21.

```bash
java -version   # expect 21.x
```

**Android SDK** with these three components:

| Component | Version |
|---|---|
| Platform | `android-37.0` |
| Build tools | `37.0.0` |
| Platform tools | latest (this is where `adb` lives) |

> **Gotcha that cost me time:** Android SDK platforms are now *minor*-versioned.
> The package is `platforms;android-37.0`, **not** `android-37`. Also, older
> `cmdline-tools` cannot read the current repository format and will report
> "Failed to find package". If you hit that, update command-line tools first.

Installing them with `sdkmanager`:

```bash
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

### 2. Point the build at your SDK

```bash
cd /path/to/Screenshot-Second-Brain
echo "sdk.dir=/absolute/path/to/Android/sdk" > local.properties
```

`local.properties` is gitignored — it is machine-specific and must not be committed.

### 3. Build

```bash
./gradlew :app:assembleDebug
```

First run downloads Gradle 9.6.1 and the dependencies. Expect several minutes.

### 4. Install

With the phone connected and USB debugging on:

```bash
adb devices                 # confirm your phone is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.shelfie.app.debug -c android.intent.category.LAUNCHER 1
```

**No USB cable?** Copy `app-debug.apk` to the phone however you like (Drive,
email, cable transfer) and tap it in a file manager. You'll need to allow
"install unknown apps" for that app. The APK is already signed, so nothing else
is required.

### 5. Watch the logs

Keep this running in a second terminal while you test:

```bash
adb logcat --pid=$(adb shell pidof -s com.shelfie.app.debug) \
  | grep -iE 'shelfie|StrictMode|FATAL|AndroidRuntime|WorkManager|MediaStore'
```

`StrictMode` is enabled in debug builds and will log main-thread disk I/O and
leaked streams. Those are warnings, not crashes, but each one is a real bug worth
fixing.

---

## What to test first

Ordered by **risk × cost of finding out late**. Do them in this order.

### 1. Does it launch at all?
The riskiest single moment in the project. A mistake in the Hilt graph, Room
schema, or WorkManager's on-demand initialisation shows up here as an instant
crash. If it opens to the onboarding screen, a lot of untested wiring just proved
itself.

### 2. Onboarding → grant full access
Three screens, then the system permission dialog. Check the **trust screen appears
before** the dialog, and that granting lands you on the shelf.

### 3. The 10-second claim — actually time it
**This is the product's whole competitive thesis.** Do it on a phone with a real
backlog (ideally 1,000+ screenshots).

Start a stopwatch when you tap *Allow access*. Stop it when tiles with readable
text appear.

- **Under ~10s** → the three-tier design works.
- **Much longer** → Tier 1 is misconfigured. Look at
  `IndexTierPolicy.IMMEDIATE_BATCH` (currently 60) and confirm the foreground pass
  isn't waiting on the full library.

Then check the device isn't hot and the battery isn't visibly draining. If it is,
the Tier 3 idle+charging constraints aren't holding.

### 4. Is the OCR and categorisation any good?
Open the shelf and just look. Do payment screenshots say *Payments* with the right
amount? Do OTP screenshots show the code?

This is where **your** screenshots will disagree with my synthetic test corpus.
Note anything misfiled — that feedback is what makes the classifier real. The
signal tables live in `core/classify/.../CategorySignals.kt`.

### 5. Limited Mode (Play compliance)
Reinstall, and on the permission screen tap **"Pick screenshots manually
instead"**. Pick a few images. They must be indexed and searchable, and everything
must keep working.

Then **force-stop the app and reopen it.** The picked screenshots must still show
their images. If they're blank grey boxes, the local-copy mechanism is broken —
this is the specific failure mode I built `ThumbnailStore` to prevent, and it only
appears after a process restart.

### 6. Partial access (Android 14+ only)
On the permission dialog choose **"Select photos…"** instead of Allow all. The app
should enter Limited Mode showing only the selected images — not treat it as a
denial.

### 7. Revoke permission while the app is running
Open the app, then in Settings → Apps → Shelfie → Permissions, revoke photo
access. Return to the app. It must degrade gracefully, **not crash**. This path
has never executed and stale-permission crashes are a classic.

### 8. Delete flow
Cleanup → pick a group → select a couple → Delete. A **system** confirmation
dialog should appear.

Then test the important half: **decline it.** The screenshots must come back. If
declining still deletes them, that's the worst class of bug this app could have.

### 9. Widget and Quick Settings tile
Long-press the home screen → Widgets → Shelfie. It should show a count and open
Search when tapped. Add the tile from the notification-shade edit screen.

### 10. Blur and duplicate detection quality
Cleanup → *Blurry or unreadable*. Look at what it offers to delete.

**This threshold is an educated guess, not a measurement.** It is:

```
core/classify/src/main/kotlin/com/shelfie/core/classify/ImageQuality.kt
    BLUR_VARIANCE_THRESHOLD = 60.0
```

- Offering to delete **perfectly readable** screenshots → threshold too **high**, lower it.
- Missing **obviously blurry** ones → too **low**, raise it.

Tune in steps of ~15 and rebuild. Getting this wrong is dangerous in the
delete-things direction, so err low.

---

## Once it runs: generate the Baseline Profile

**The release build currently ships without one**, giving up roughly 20–30% cold
start improvement — on exactly the budget hardware this app targets. This needs a
connected device and takes a few minutes:

```bash
./gradlew :app:generateReleaseBaselineProfile
```

It writes into `app/src/release/generated/baselineProfiles/`. **Commit that file** —
it becomes part of the build.

Then measure what you gained:

```bash
./gradlew :benchmark:connectedBenchmarkBenchmarkAndroidTest
```

This reports cold start (budget: **P90 under 500ms**) and frame timing (budget:
**zero frozen frames**). Prefer a cheap physical phone; a fast emulator reports
numbers your users will never see.

---

## What you still can't test this way

**Billing.** Two reasons:

1. Debug builds are `com.shelfie.app.debug`, but a Play Console product is tied to
   `com.shelfie.app`. The suffix alone breaks it.
2. Play Billing only works for an app **installed by the Play Store**.

To test purchases you need to:
1. Create a **release keystore** (and back it up — losing it means you can never
   update the app).
2. Build a signed AAB: `./gradlew :app:bundleRelease`
3. Upload to Play Console **internal testing**.
4. Create a one-time product with the id **`shelfie_full_version`** — this must
   match `ShelfieProducts.FULL_VERSION` exactly.
5. Add yourself as a **licence tester** so purchases are free.
6. Install via the internal-testing link.

Until then the app runs at the free tier and Settings shows "Purchases aren't
available on this device right now", which is the correct, non-broken fallback.

---

## Troubleshooting

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
A different signature is already installed. `adb uninstall com.shelfie.app.debug` first.

**`SDK location not found`**
Missing or wrong `local.properties`. Use an absolute path.

**`Failed to find package platforms;android-37`**
Use `platforms;android-37.0` — platforms are minor-versioned now.

**Android Studio refuses to sync / "unsupported AGP"**
Needs Otter 3 Feature Drop or newer for AGP 9.

**`Unsupported class file major version`**
Gradle is running on the wrong JDK. Set `JAVA_HOME` to a JDK 21 install.

**App installs but the shelf stays empty**
Check the permission was actually granted, then look for `MediaStore` lines in
logcat. If your device stores screenshots somewhere unusual, the folder heuristics
in `core/media/.../ScreenshotHeuristics.kt` may need another path keyword — 14
tests cover the known vendor layouts, but there are always more.

**Widget shows no count**
It updates every 30 minutes or on interaction. Remove and re-add it to force a
refresh.
