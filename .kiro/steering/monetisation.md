# Monetisation: one purchase, no ads

## The decision

Shelfie earns money from **one thing only**: a single in-app purchase
(`shelfie_full_version`, ₹199 in India, priced per market by Play).

**There are no ads, and none are planned.** Do not add AdMob, any other ad SDK, or any
"free version shows ads" wording to the app, the store listing, or the upgrade prompt.

Decided deliberately in August 2026, after costing it out. This is not an oversight to
be helpfully corrected.

## Why

**Ads require `INTERNET`, and that permission is the product.**

The app's manifest deliberately omits `android.permission.INTERNET`, and the onboarding
screen tells the user so. That claim is unusual because it is **verifiable** — anyone can
open system settings and confirm the app has no network access. It is the reason someone
pays for a screenshot organiser rather than installing one of the free ones, and it is
the basis of the Play Data Safety declaration.

Adding an ad SDK would trade that for very little:

- Banner eCPM in India is roughly **$0.20–$0.35** per 1,000 impressions.
- A free user opening the app a few times a week generates about **₹15–20 per year**.
- One ₹199 sale nets about **₹169** after Play's 15% fee — equivalent to roughly
  **8,000 banner impressions**, or about ten years of one free user.
- AdMob does not pay out until **$100** has accumulated, so at that rate the first
  payment needs hundreds of active free users for a full year.

So ads would earn pennies while removing the argument that justifies the price. The
economics and the positioning point the same way.

**A false ads claim was also considered and rejected.** Mentioning ads in the upgrade
prompt before any ads exist would mean charging for the removal of something that is not
there — a Play deceptive-behaviour risk, and a direct contradiction of the onboarding
screen, which is the claim users would stop believing.

## What this permits

- **"No ads" is a legitimate selling point** in the store listing and the upgrade
  prompt, because it is now permanently true rather than temporarily true.
- The free tier stays limited by **screenshot count** (`IndexingQuota.FREE_INDEX_LIMIT`,
  currently 50), never by ads.

## If this is ever revisited

Reopening it means all of the following, and none of them are small:

1. Adding `INTERNET` to the manifest, which removes the verifiable claim.
2. Rewriting `docs/privacy.html` and the published privacy policy.
3. Re-submitting the Play **Data safety** form — data is then leaving the device.
4. Deleting `settings_privacy_no_network`, which is deliberately isolated as the single
   string asserting no network access.
5. Re-recording the content rating questionnaire answers.

Get real conversion data at ₹199 first. If people buy, ads only cannibalise the
purchase; if nobody buys, the price or the product is the problem, not the absence of
ads.
