# Play Store Listing

## Title (30 char max)
```
Shelfie: Screenshot Finder
```
26 characters. Brand first for recall, keyword second for ASO.

## Short description (80 char max)
```
Search any screenshot instantly. Works offline. No account. No subscription.
```
75 characters. Leads with the benefit, then the three objections it removes.

---

## Full description (4000 char max)

```
Your gallery has thousands of screenshots. You can't find any of them.

Shelfie fixes that. It reads the text inside every screenshot on your phone and
makes it searchable — so the receipt, the OTP, the wifi password, the ticket, the
recipe you saved months ago is one search away.

And it's private in a way you can actually check: Shelfie ships with no internet
permission at all. Not restricted — absent. Android won't let it open a network
connection, so your screenshots physically cannot be uploaded anywhere. No account,
no ads. The code is public, so you don't have to take our word for it.


FIND ANYTHING IN SECONDS

Type what you remember. Shelfie searches the words inside your screenshots, not
just file names. Search an amount, a name, a booking ID, a phone number, a link.


SORTED WITHOUT YOU LIFTING A FINGER

Take a screenshot and Shelfie files it automatically — Payments, OTP codes,
Tickets, Wi-Fi passwords, Products, Recipes, Documents, Places and more.

Sorted somewhere you disagree with? Tap it once and Shelfie learns your rule.
Your categories, not ours.


EVERY SCREENSHOT COMES WITH ITS NEXT STEP

Not just a picture. Shelfie pulls out what matters and offers the action:

- Copy an OTP without opening the image
- Open a link you screenshotted
- Add a booking date to your calendar
- Call a number from a screenshot
- Copy any text you can see


CLEAR OUT THE CLUTTER

Find exact duplicates, unreadable blurry captures, and old screenshots you never
opened again. See exactly how much space you'll get back before you delete
anything, and your phone asks you to confirm every deletion — nothing goes
without your say-so.


FAST, EVEN ON AN OLDER PHONE

Most organiser apps make you wait while they scan your whole gallery. Shelfie
shows you a working, searchable shelf in about ten seconds, then quietly finishes
the older ones while your phone is idle and charging.

No overheating. No all-day battery drain.


TRULY PRIVATE — AND YOU CAN CHECK

Shelfie ships without Android's internet permission. Not restricted, not
optional — the permission is simply not in the app, so the operating system will
not let it open a network connection at all.

That means your screenshots physically cannot be uploaded anywhere. There is no
server to send them to, and no way to reach one.

No account. No sign-up. No email required. Nothing to leak.


ONE PAYMENT. NOT A SUBSCRIPTION.

Shelfie is free to use for your most recent screenshots.

Unlock everything once and it's yours permanently. No monthly fee, no annual
renewal, no trial that quietly starts charging you.

No ads, ever.


WHAT SHELFIE DOESN'T DO

No account. No cloud. No ads. No notifications begging you to come back. It does
one job and stays out of your way.


Works on Android 8.0 and up. Some optional plain-language search features need a
device with Android's on-device AI support.
```

---

## Screenshot sequence

Order matters more than polish, and it now **leads with the differentiator** — a
Reddit reviewer's point: privacy is the one thing no competitor can honestly claim,
so it should be the first thing a browsing user sees, not buried at slide 5.

Ready-made caption slides are generated in `play/screenshots/` by
`tools/playstore/render.js` (1080×1920, on-brand). Recommended order:

| # | File | Caption |
|---|---|---|
| 1 | `play-1-privacy.png` | **No internet permission. At all.** |
| 2 | `play-2-search.png` | **Search the text inside your screenshots** |
| 3 | `play-3-actions.png` | **Sorted automatically — with an action on each** |
| 4 | `play-4-cleanup.png` | **Clear out duplicate and blurry screenshots** |
| 5 | `play-5-price.png` | **One payment. ₹250. No subscription.** |

These are branded caption slides, which convert far better than raw, uncaptioned
screenshots — the exact feedback that prompted them. **Still interleave 2–3 real
captures from the live app** (search results, the shelf, the detail sheet) so the
listing also shows the actual UI. Play wants to see the real app, and a mix of
caption slides + real screens is the strongest combination.

Privacy leading the sequence is the differentiator — no competitor can honestly
make that claim.

**Accuracy note:** do not tell users to verify this in Settings > Apps >
Permissions. That screen lists only runtime permissions, and `INTERNET` is a
normal permission, so it never appears there either way. The absence is real and
checkable (`adb shell dumpsys package com.pitchcode.shelfie`), but it is not something
an ordinary user can confirm in system settings. Claiming otherwise would be a
deceptive-behaviour risk.

**Specs:** min 2, up to 8. 1080×1920 or higher, 16:9 or 9:16, PNG/JPEG. Show the real app only — mockups implying non-existent features violate the Deceptive Behaviour policy.

---

## Feature graphic (1024 × 500)

Brand Deep `#12122A` background. Shelf icon left in amber `#FFD24A`. Right side, two lines of large white type:

> **Find any screenshot.**
> **Nothing leaves your phone.**

Keep all content away from the outer 80px — Play crops this asset on some surfaces.

---

## ASO keyword targets

The head terms ("screenshot organizer", "screenshot manager") are saturated by the ten competitors listed in `01-competitive-analysis.md`. Do not fight there first. Target intent-shaped long-tail queries:

**Primary:** screenshot search · search text in screenshots · find screenshot · screenshot finder
**Long-tail (better odds):** find old screenshot · search screenshot text offline · organize screenshots without internet · screenshot cleanup duplicate · extract text from screenshot offline
**Trust-intent:** offline screenshot app · no ads screenshot organizer · private gallery search

Keywords belong in the **full description**, naturally. Stuffing the title or short description is a rejection risk.

---

## Categorisation
- **Category:** Tools *(not Productivity — Tools has weaker competition for these terms and better matches user intent here)*
- **Tags:** Utilities, File management
- **Content rating:** Everyone
- **Target audience:** 18+ (or 13+). Do **not** opt into the Families programme.
- **Ads:** No
- **In-app purchases:** Yes, one-time

---

## Launch positioning notes

Lead every piece of external communication with **the verifiable privacy claim**, not the AI. Every competitor says "AI" and "private". Only we can say "the permission isn't in the app, go look".

Second message is speed on cheap hardware. Third is one-time pricing.

Never lead with "AI-powered" — in 2026 that phrase is noise, and it invites comparison to Google's Pixel Screenshots, a fight we don't want.
