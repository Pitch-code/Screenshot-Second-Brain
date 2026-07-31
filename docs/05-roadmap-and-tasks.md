# Roadmap & Task Breakdown

**Anti-duplication rule:** every task below declares what it **owns**. If something isn't in a task's ownership list, that task must not create it — it consumes it from the task that owns it. Shared code always lands in a `:core:*` module, owned by exactly one task.

Effort is in ideal focused days for one experienced Android developer.

---

## Phase 0 — Foundation (3 days)
*Nothing else can start until this is done. Do not parallelise into this phase.*

| # | Task | Owns | Done when |
|---|---|---|---|
| 0.1 | Gradle project + version catalog + convention plugins | `settings.gradle.kts`, `libs.versions.toml`, `build-logic/`, all module skeletons | `./gradlew build` green on empty modules |
| 0.2 | SDK config: min 26 / **target 36**, R8 full mode, signing config | `app/build.gradle.kts`, `proguard-rules.pro`, keystore | Release build installs and launches |
| 0.3 | Design system: Material3Expressive theme, tokens, brand colours | **`:core:designsystem` entirely** | Theme preview renders light + dark |
| 0.4 | Room DB: entities, DAOs, **FTS4 table**, migration test harness | **`:core:database` entirely** | Insert + FTS query passes instrumented test |
| 0.5 | DataStore: prefs, purchase state, **MediaStore watermark**, rules storage | **`:core:datastore` entirely** | Read/write round-trip test passes |
| 0.6 | App shell: Application, MainActivity, Hilt, nav host, edge-to-edge, predictive back | `:app` | 4 empty destinations navigate, back gesture animates |

**Gate:** empty app launches in <400ms, dark theme correct, DB migrates.

---

## Phase 1 — The engine (5 days)
*This is the product. Build it before any polished UI.*

| # | Task | Owns | Done when |
|---|---|---|---|
| 1.1 | MediaStore query layer + screenshot identification heuristics (multi-OEM folder names + display-dimension match) | **`:core:media`** — all `MediaStore` access | Returns correct set on 3 different OEM devices |
| 1.2 | ContentObserver + **watermark reconcile** + 6h periodic safety-net worker | `:core:media` observer/reconcile | New screenshot appears within 3s; kill process, take 2 shots, relaunch → both appear |
| 1.3 | OCR wrapper: ML Kit v2 behind interface, **downsample-before-decode**, single-in-flight semaphore | **`:core:ocr` entirely** | 500 images processed, peak memory <180MB, zero OOM |
| 1.4 | Classifier: regex + weighted keyword scoring, entity extractors (amount, date, OTP, URL, phone, PNR) | **`:core:classify` entirely** — pure Kotlin | ≥85% correct on a 200-sample fixture corpus |
| 1.5 | Rule engine: user rules stored, evaluated, always override built-ins | `:core:classify` rule evaluation | User rule beats built-in detector in unit test |
| 1.6 | **Three-tier scheduler**: Tier 1 foreground / Tier 2 expedited / Tier 3 idle+charging, chunked + checkpointed | `:core:media` scheduling, WorkManager setup | 5,000-image library: shelf populated <10s, backlog resumes after reboot |

**Gate — the make-or-break test:** on a 4GB budget phone with 5,000 existing screenshots, a searchable shelf appears in under 10 seconds and the device does not get hot. If this fails, stop and fix it. This is the entire competitive advantage.

---

## Phase 2 — Core UI (5 days)

| # | Task | Owns | Done when |
|---|---|---|---|
| 2.1 | Shelf screen: Paging 3 grid, date headers, category chips, non-blocking status strip | **`:feature:shelf`** | Scrolls 5,000 items with zero frozen frames |
| 2.2 | Tile component: thumbnail + extracted key value + one action chip | `:core:designsystem` tile composable | Renders all 12 category variants correctly |
| 2.3 | Search: debounced FTS query, ranking, **match highlighting**, filters | **`:feature:search`** | Results <150ms on 5,000 rows |
| 2.4 | Detail bottom sheet: zoom, selectable text, entity chips, action row, inline "always sort here" | **`:feature:detail`** | Every action fires correct intent |
| 2.5 | Action intent layer: open URL, copy, dial, calendar insert, share, reminder | `:core:designsystem` action handlers | All handled, all with graceful no-handler fallback |

**Gate:** a real user finds a specific screenshot from memory in under 15 seconds, unprompted.

---

## Phase 3 — Permissions, compliance & fallback (3 days)
*Do not defer this. A rejected permission declaration blocks launch entirely.*

| # | Task | Owns | Done when |
|---|---|---|---|
| 3.1 | Onboarding: 3 screens incl. trust screen before any system dialog | **`:feature:onboarding`** | Completes in <15s, no account, no paywall |
| 3.2 | Permission state machine: granted / **partial (`READ_MEDIA_VISUAL_USER_SELECTED`)** / denied / revoked-mid-session | `:core:media` permission layer | All 4 states handled, zero `SecurityException` |
| 3.3 | **Limited Mode** — full app function over photo-picker-selected images only | `:feature:onboarding` + shelf empty-state variant | App is genuinely useful with broad access denied |
| 3.4 | Data Safety form + broad-access declaration submitted | Play Console entries | Declaration submitted, see `06-play-store-compliance.md` |
| 3.5 | Privacy policy published at a public HTTPS URL | Hosted `legal/privacy-policy.md` | URL loads, linked in-app and in Console |

**Gate:** Limited Mode is not a dead end — it's a working app. This is both a policy requirement and a genuine UX win.

---

## Phase 4 — Cleanup & retention (3 days)

| # | Task | Owns | Done when |
|---|---|---|---|
| 4.1 | Duplicate + blur detection (perceptual hash, Laplacian variance) | `:core:classify` quality analysis | Correctly groups known duplicate set |
| 4.2 | Cleanup screen: cards, preview-before-delete, MB reclaimed, `createDeleteRequest` | **`:feature:cleanup`** | Bulk delete works on Android 10–16 incl. user-declines path |
| 4.3 | 30-day Recently Deleted holding area | `:feature:cleanup` | Restore works; auto-purges at 30 days |
| 4.4 | Glance widget (search box + last 3) + Quick Settings tile | **`:feature:widget`** | Widget updates within 5s of new screenshot |
| 4.5 | Share-sheet intent filter for images | `:app` manifest | Sharing an image into Shelfie indexes it |

---

## Phase 5 — Monetisation & settings (2 days)

| # | Task | Owns | Done when |
|---|---|---|---|
| 5.1 | Play Billing one-time product, purchase + restore, offline entitlement cache | `:core:datastore` purchase state, `:app` billing client | Purchase, reinstall, restore all work |
| 5.2 | Free-tier gate at 150 indexed, non-punitive upgrade prompt | `:feature:shelf` gate UI | Gate never blocks search of already-indexed items |
| 5.3 | Settings screen: permissions status, my rules editor, appearance, export, about | **`:feature:settings`** | Rule created in settings takes effect immediately |

---

## Phase 6 — Performance hardening (3 days)
*Explicitly scheduled, not "if there's time". This phase is the product's reputation.*

| # | Task | Owns | Done when |
|---|---|---|---|
| 6.1 | `:benchmark` module: macrobenchmark cold start + Shelf scroll | **`:benchmark` entirely** | Baseline numbers recorded |
| 6.2 | **Baseline Profile** generation + wiring | `:benchmark` profile output | Cold start improves ≥20% |
| 6.3 | Budget enforcement pass against §4 of architecture doc | — | Every budget met or explicitly waived in writing |
| 6.4 | Crash checklist sweep — all 12 items from architecture doc §5 | — | Each item has a test or documented mitigation |
| 6.5 | Adaptive layouts: NavigationRail ≥600dp, no orientation lock | `:app` nav + `:feature:shelf` grid | Correct on tablet + foldable; ready for API 37 |
| 6.6 | Accessibility pass: TalkBack, 200% text, contentDescriptions | all `:feature:*` | Full TalkBack traversal of Shelf/Search/Detail |
| 6.7 | Localisation extraction: en + hi + ta + te + bn | `:app` string resources | No hardcoded strings remain |

---

## Phase 7 — Launch (3 days)

| # | Task | Owns | Done when |
|---|---|---|---|
| 7.1 | Icon + adaptive + monochrome + feature graphic per brand spec | `store/assets/` | All specs in `02-brand-name-logo.md` met |
| 7.2 | Store listing copy + screenshots | `store/listing-copy.md` assets | Uploaded |
| 7.3 | **Android developer verification** registration | Play Console | Complete — deadline Sept 30, 2026 |
| 7.4 | Closed test, 20 testers, 5,000+ screenshot libraries | — | 14 days, zero P0 crashes |
| 7.5 | Open test → staged production rollout 5% → 20% → 50% → 100% | — | Crash-free rate >99.5% at each gate |

---

## Timeline

**27 focused days ≈ 6–7 calendar weeks part-time.**

```
Week 1    Phase 0 + start Phase 1
Week 2    Phase 1 complete  ← GATE: 10-second test on budget device
Week 3    Phase 2
Week 4    Phase 3  ← submit permission declaration EARLY, review takes time
Week 5    Phase 4 + 5
Week 6    Phase 6
Week 7    Phase 7 + closed testing
```

### Critical path warnings
1. **Target API 36 is mandatory from Aug 31, 2026.** Set it in Phase 0.2. Don't discover this at submission.
2. **Developer verification closes Sept 30, 2026** — do 7.3 *now*, in parallel with Phase 0. It's paperwork, not code, and missing it means removal from Play.
3. **Submit the broad-access declaration in Phase 3, not Phase 7.** If it's rejected you need weeks to appeal or redesign around Limited Mode.
4. **The Phase 1 gate is a hard stop.** Everything after it is worthless if the 10-second test fails.

---

## Sequencing rules that prevent rework

- Build **engine before UI**. Building UI first means rebuilding it once the data shape settles.
- Build **Limited Mode in Phase 3, not as a patch**. Retrofitting a permission-denied path into a finished app touches every screen.
- Build the **classifier fixture corpus first** (collect 200 real screenshots before writing 1.4). Without it you're tuning blind.
- **Never** let a `:feature:*` module import another. The moment you want to, the shared piece belongs in `:core:*`.
- Write the **privacy policy in Phase 3**, when the data flows are settled and still fresh — not from memory at launch.
