# Google Play Compliance Pack

Everything here is verified against Google's own documentation as of July 2026. Links are to primary sources.

---

## 1. Hard deadlines — act on these first

| Deadline | Requirement | Status |
|---|---|---|
| **Aug 31, 2026** | New apps **and** updates must target **Android 16 / API 36** | Set in Phase 0.2 |
| **Sept 30, 2026** | **Android developer verification** registration — miss it and apps face global removal from Play | ⚠️ Do this now, it's paperwork |
| Oct 27, 2026 | New Play Console pre-review checks go live (flag permission policy issues pre-submission) | Use them |
| Already in force (May 28, 2025) | Photo & Video Permissions policy fully enforced — non-compliant apps subject to removal | See §2 |
| Future (API 37) | Android 17 removes the orientation/resizability opt-out on large screens (sw > 600dp) | Phase 6.5 |

Sources: [target SDK policy](https://quasa.io/media/google-play-s-target-sdk-policy-a-gradual-cleanup-that-opens-the-door-for-modern-apps), [developer verification](https://android-developers.googleblog.com/2026/06/android-developer-verification.html), [Play policy updates Apr 2026](https://android-developers.googleblog.com/2026/04/giving-users-clearer-choice-and-everyone-a-safer-more-trusted-app-ecosystem.html), [adaptive requirements](https://android-developers.googleblog.com/2026/05/android-adaptive-development-ecosystem.html)

---

## 2. The critical one: Photo & Video Permissions policy

**This is the single biggest launch risk. Read it carefully.**

### What the policy actually says
From [Understanding Restricted Permissions with minimum scope alternatives](https://support.google.com/googleplay/android-developer/answer/14115180) *(paraphrased — content rephrased for compliance with licensing restrictions)*:

- Apps targeting Android 13+ may request `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` **only if system pickers such as the Android Photo Picker are not sufficient to deliver core functionality**.
- Apps qualify when their **core function involves managing and maintaining all of a user's photos or videos** — Google names "gallery apps" as the example — or where the picker is technically insufficient for the feature.
- Having a **custom picker does not automatically qualify** you. A declaration in Play Console is still required.
- Full compliance has been **mandatory since May 28, 2025**, and non-compliant apps are subject to removal.
- If your app doesn't have a qualifying use case, the permissions **must be removed from the manifest**.
- Under the Restricted Permissions policy you **must make reasonable effort to accommodate users who decline broad access** — including offering a transactional path via a system picker, or a gracefully modified experience.

### Our position — why we qualify

Shelfie's core function is continuous, automatic indexing of **every** screenshot as it is created, so the whole library is searchable. This is a management function over the user's entire screenshot collection — directly analogous to the gallery-app case Google names.

The Photo Picker is **technically insufficient** for three specific reasons:

1. **The picker is user-initiated and one-shot.** Our core value is that indexing happens *automatically* when a screenshot is taken, with no user action. The picker cannot deliver an automatic trigger.
2. **The picker cannot support background reconciliation.** We must detect screenshots created while the app is closed. The picker only returns items during a foreground session.
3. **The value is completeness.** A search index that covers only manually re-selected images cannot answer "find the receipt I saved months ago" — the primary user need. Requiring a user to hand-pick thousands of images defeats the product.

### Declaration form answers (draft — paste into Play Console)

**Which user-facing feature requires broad access?**
> Automatic organisation and full-text search across the user's complete screenshot library. When the user takes a screenshot, Shelfie reads its text on-device and files it into a searchable category so it can be found later. The app also detects screenshots created while it is closed and indexes them during idle periods.

**Why is the Android Photo Picker not sufficient?**
> The Photo Picker requires an explicit, foreground, per-session user selection. Shelfie's core function is automatic, continuous indexing of newly created screenshots with no user interaction, including while the app is not running — which the picker cannot provide. Our value proposition is a complete, always-current searchable index of the user's entire screenshot library; a picker-based flow would require the user to manually re-select thousands of images and would not capture new screenshots automatically. All processing is performed on-device and the app declares no INTERNET permission, so no media ever leaves the device.

### Mandatory mitigation: Limited Mode
Because the policy requires accommodating users who decline, **Limited Mode is not optional** — it's a compliance requirement and it's specced in Phase 3.3.

- Deny broad access → app remains fully functional over images the user selects via the Photo Picker
- Every feature (search, categories, actions, cleanup) works on that subset
- Persistent, non-nagging banner: `Limited Mode — Shelfie can only see 12 screenshots you picked. Add more / Allow full access`
- Handle Android 14+ `READ_MEDIA_VISUAL_USER_SELECTED` (partial grant) as a first-class state

### Fallback plan if the declaration is rejected
Ship Limited Mode as the *only* mode, and reposition as "pick a batch, get them sorted." Weaker product, but it launches. Build Limited Mode well and this isn't fatal — which is exactly why it's in Phase 3 and not bolted on at the end.

---

## 3. Data Safety form — exact answers

Because there is no `INTERNET` permission, this section is unusually clean. **Answer honestly; Play audits this against your actual APK behaviour.**

| Question | Answer |
|---|---|
| Does your app collect or share **any** required user data types? | **No** |
| Is all user data processed **only on-device**? | **Yes** |
| Is data **transferred off the device**? | **No** |
| Is data **encrypted in transit**? | N/A — no transit |
| Does your app provide a way to request data deletion? | **Yes** — Settings → clear index / uninstall removes everything |
| Data types collected | **None** |
| Data types shared | **None** |
| Third-party SDKs collecting data | **None** |
| Independent security review | Optional — declare only if actually done |

**Photos handling declaration:** the app *accesses* photos, but processes them locally and neither collects nor transmits them. The derived text index is stored in the app's private sandbox storage.

**Do not add Firebase Analytics or Crashlytics later without updating this form.** Adding either silently makes the Data Safety declaration false, which is a policy violation and a removal risk. If you ever need analytics, update the form *in the same release*.

---

## 4. Other policies that apply

| Policy | Requirement | Our compliance |
|---|---|---|
| [User Data](https://support.google.com/googleplay/android-developer/answer/10144311) | Privacy policy URL in Console **and** accessible in-app | See `legal/privacy-policy.md` |
| Permissions & APIs | Request minimum necessary; no permission without a use | 3 permissions only, all justified |
| Deceptive Behaviour | Store listing claims must be true | "No internet permission" is verifiable in the manifest |
| Subscriptions / Billing | Pricing must be clear, no dark patterns | One-time product, no trials, no auto-renew |
| Families | If not targeting children, set content rating accordingly | Rate as everyone; don't opt into Families programme |
| Ads | Declare presence of ads accurately | **No ads** — declare none |
| Account deletion | Required only if accounts exist | N/A — no accounts, state this explicitly |
| Play Integrity | Optional | Skip — would require network |
| [Age Signals API](https://android-developers.googleblog.com/2026/07/google-play-age-signals-api-safer-experiences.html) | Optional, new in 2026 | Not needed; no age-varied content |

---

## 5. Store listing compliance rules

- **Title ≤ 30 chars.** `Shelfie: Screenshot Finder` = 26. ✅
- **No keyword stuffing** in title/short description — grounds for rejection.
- **No "#1", "Best", "Top"** or unverifiable superlatives.
- **Screenshots must show the actual app.** No mockups implying features that don't exist, no fake device frames with invented UI.
- **Don't reference Google trademarks** ("works like Pixel Screenshots" → rejection risk).
- **Don't claim "AI" for features that only run on some devices** without qualifying the device requirement.
- Short description ≤ 80 chars.

---

## 6. Pre-submission checklist

**Technical**
- [ ] `targetSdk = 36`, `compileSdk = 36`
- [ ] Signed release AAB (not APK) with upload key backed up **twice**
- [ ] R8 full mode on; release build smoke-tested (proguard breakage often only shows in release)
- [ ] No `INTERNET` permission in the merged manifest — verify with `./gradlew :app:processReleaseManifest` and read the output
- [ ] Baseline Profile present in the release artifact
- [ ] Tested on Android 10, 13, 14, 15, 16
- [ ] Tested on a 4GB budget device with 5,000+ screenshots
- [ ] Permission denied, partial-grant, and revoked-mid-session paths all verified

**Console**
- [ ] Developer verification registered *(deadline Sept 30, 2026)*
- [ ] Photo & video broad-access declaration submitted
- [ ] Data Safety form completed per §3
- [ ] Privacy policy URL live over HTTPS and reachable
- [ ] Content rating questionnaire done
- [ ] Target audience set (18+ or 13+, not children)
- [ ] Ads declaration: none
- [ ] One-time product created and activated in Billing
- [ ] All listing assets uploaded per brand spec

**Legal**
- [ ] Trademark search cleared for the final name
- [ ] Privacy policy reviewed by a lawyer *(see the disclaimer in the policy file)*
- [ ] India **DPDP Act 2023** obligations reviewed if distributing in India

---

## 7. Ongoing obligations after launch

- Watch **Play Console vitals** — ANR and crash rate are your only telemetry, since there's no analytics SDK
- Respond to reviews, especially any reporting missed screenshots or slow indexing
- Re-target the new API level each year (Aug 31 for new/updated apps)
- Re-affirm the Data Safety form annually
- Any new permission or SDK requires a Data Safety update **in the same release**
