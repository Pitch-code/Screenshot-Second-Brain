# Product & UI Specification

Design language: **Material 3 Expressive** — the current Android design system, introduced at I/O 2025 and shipping with Android 16. It adds a spring-based motion physics system, bolder type, and wider shape variety. Requires Material Components **1.14.0+** for the `Material3Expressive` themes.
Sources: [Material 3 Expressive launch](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/), [Compose motion physics](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html), [Material Components getting started](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md)

---

## Part A — Product definition

### The one sentence
Shelfie makes every screenshot on your phone searchable and actionable, entirely on-device, without a login or a subscription.

### The three jobs it does
1. **Find** — "where's that UPI receipt / wifi password / ticket QR" → one search, found in seconds
2. **Act** — each screenshot carries its next step: open link, copy OTP, add to calendar, dial number, share
3. **Clear** — reclaim storage by removing indexed clutter safely, in bulk

### Explicit non-goals for v1
Cutting these is what keeps the app fast and reviewable. Feature bloat is a documented top-5 churn driver ([Airbridge](https://airbridge.io/blog/why-subscription-churn-happens-top-5-cancellation-reasons-forecast-for-mobile-apps-in-2026)).

- No cloud sync, no account, no login
- No image editing / markup / collage
- No video handling
- No social sharing feed
- No general photo management (screenshots only — keeps the permission story tight)
- No chat/LLM interface

### Category taxonomy
Categories must be **derived from the user's actual content**, not a fixed list. The #1 substantive complaint in this category is that preset categories don't match what people screenshot.

Ship these detectors, and **only surface a category once it has ≥3 matches**:

| Category | Detection signals |
|---|---|
| Payments & Receipts | UPI ref no, `₹`/`$` + amount patterns, "paid to", "transaction successful", bank names |
| OTP & Codes | 4–8 digit isolated numerics + "OTP\|code\|verification", short validity phrasing |
| Tickets & Bookings | PNR, booking ID, seat/gate/platform, date+time pairs, airline/rail/cinema keywords |
| Wi-Fi & Passwords | "password", "SSID", "network", key-like strings |
| Products & Shopping | price + "add to cart\|buy now\|delivery", marketplace names |
| Chats & Conversations | timestamp column patterns, bubble-layout heuristics, "typing…", "online" |
| Documents & IDs | Aadhaar/PAN/DL/passport-shaped patterns, "valid until", "date of birth" |
| Recipes & Food | ingredient lists, "cup\|tbsp\|tsp\|grams", "preheat\|cook\|serves" |
| Places & Maps | "km away", "ETA", "directions", address-shaped strings |
| Study & Notes | question numbering, "chapter\|lesson\|marks\|syllabus" |
| Contacts | phone-number patterns + name proximity |
| Everything else | fallback bucket, never labelled "Uncategorised" — call it **"Not sorted yet"** |

Plus: **user-editable rules.** "Any screenshot containing `Zerodha` → Investments." This single feature answers complaint #2 and no competitor has it.

### Monetisation
- **Free forever:** search, categories, actions, up to **150 indexed screenshots** (rolling, newest)
- **Shelfie Full — one payment, ₹399 / $4.99:** unlimited index, custom rules, bulk cleanup, widget, export
- **No ads. No subscription. No trial timer.**
- The pricing *is* the marketing. Store listing leads with "One payment. No subscription."
- Use Play Billing **one-time product**; nothing else. No consumables, no currency.

---

## Part B — Information architecture

Four destinations. A fifth would be a mistake.

```
┌─ Shelf (home)      newest-first feed, category chips, index status
├─ Search            full-text, filters, natural-language on capable devices
├─ Cleanup           duplicates, blurry, old & never-opened, bulk delete
└─ Settings          permissions, rules, appearance, purchase, privacy
```

Navigation: `NavigationBar` on compact width, `NavigationRail` at ≥600dp (required for adaptive compliance under API 37 — see architecture doc).

---

## Part C — Screen specs

### 1. Onboarding — 3 screens, no account, ~15 seconds

**Screen 1 — the problem, mirrored back**
Full-bleed amber. Large expressive type:
> **You have 4,000 screenshots.**
> You can't find any of them.

Subline: `Shelfie fixes that in about a minute.`
Single button: `Show me`

*Why:* leads with the user's own reality, not our feature list. If we can read the count before permission (we can't reliably), skip the number and say "hundreds of screenshots".

**Screen 2 — the trust screen (before any system dialog)**
> **Everything happens on your phone.**
> Shelfie reads the text in your screenshots so you can search them. That reading happens here, on this device.
>
> **Shelfie has no internet permission.** It physically cannot upload anything. You can verify that in Settings → Apps → Shelfie → Permissions.

Button: `Continue`
Secondary text link: `Read the privacy policy`

*Why:* the strongest asset we have, stated before we ask for anything. Naming the verification path converts a claim into a checkable fact.

**Screen 3 — permission rationale, then the system dialog**
> **Shelfie needs to see your screenshots**
> That's the whole job. Without access it can only work on screenshots you hand over one at a time.

Primary: `Allow access` → triggers `READ_MEDIA_IMAGES`
Secondary: `Pick manually instead` → **Limited Mode** (mandatory fallback, see compliance doc)

Then land directly on Shelf. **Never show a paywall in onboarding.**

### 2. Shelf (home)

```
┌──────────────────────────────────────┐
│  Shelfie                      ⚙      │
│  ┌────────────────────────────────┐  │
│  │ 🔍  Search 1,240 screenshots   │  │   ← docked search bar, always visible
│  └────────────────────────────────┘  │
│                                      │
│  ▸ Indexing older screenshots        │   ← status strip, ONLY when work pending
│    1,240 of 5,300 · resumes charging │      dismissible, never a blocking bar
│                                      │
│  [All] [Payments] [OTP] [Tickets] …  │   ← chips, horizontally scrollable
│                                      │
│  TODAY                               │
│  ┌────────┐ ┌────────┐ ┌────────┐   │
│  │ thumb  │ │ thumb  │ │ thumb  │   │
│  │ ₹1,240 │ │ OTP    │ │ PNR    │   │   ← category badge + extracted key value
│  │ ⚡Pay   │ │ ⚡Copy  │ │ ⚡Cal   │   │   ← the ACTION, on the tile
│  └────────┘ └────────┘ └────────┘   │
│  YESTERDAY                           │
│  …                                   │
└──────────────────────────────────────┘
```

Rules:
- **Newest first, always.** Date-grouped sticky headers.
- Every tile shows the **single most useful extracted value**, not a filename. This is what makes the index feel alive rather than a gallery clone.
- Every tile shows **one action chip** — this directly answers "the index has no downstream value".
- The status strip is **informational, never blocking.** The grid is fully usable during indexing.
- 3-column grid at compact, 5 at medium, 7 at expanded.

### 3. Search

- Query as you type, debounced 200ms, over FTS index
- Results ranked: exact phrase > all terms > any term, then recency
- **Highlight the matched substring** in the result subtitle — proves *why* it matched, builds trust in the index fast
- Filter row: category, date range, has-link, has-amount, has-number
- Empty result state offers `Search older screenshots first` → bumps the backlog job priority for that query. Turns a dead end into an action.
- On AICore-capable devices only, add a `Ask in plain words` affordance (see architecture doc, §GenAI tier)

### 4. Detail sheet

Bottom sheet, not a new screen — keeps context and feels faster.
- Image, pinch-zoomable
- **Extracted text, selectable**, with detected entities as chips (amounts, dates, links, phone numbers, codes)
- Action row: Open link · Copy code · Add to calendar · Dial · Share · Set reminder
- Category row with a `Change` affordance → offers `Always sort <sender/keyword> here` → creates a user rule inline. Best moment to capture a rule is right when the user notices it's wrong.
- Destructive `Delete` at the bottom, never adjacent to primary actions

### 5. Cleanup

Users came to reclaim storage; this is the second-strongest retention hook after search.
- Cards: `Exact duplicates (24 · 61 MB)`, `Blurry / unreadable (8)`, `Older than 6 months, never opened (312 · 780 MB)`
- Always **preview before delete**, always show **MB reclaimed**, always require explicit confirm
- Move to an in-app 30-day **Recently deleted** holding area rather than hard-deleting. One accidental bulk delete produces a 1-star review that lasts forever.

### 6. Settings
Access & permissions (with a live Limited Mode banner) · My rules · Appearance · Shelfie Full · Privacy policy · Export my data · Recently deleted · About

---

## Part D — Visual system

| Token | Value |
|---|---|
| Theme | `Theme.Material3Expressive.DayNight`, **dark-first** |
| Dynamic colour | On by default (Android 12+), with brand amber as fallback seed |
| Corner radius | 20dp cards, 28dp sheets, 16dp chips — Expressive favours larger, varied radii |
| Type | Display 36sp / Title 22sp / Body **16sp minimum** / Label 14sp |
| Motion | Spring-based (`MaterialTheme.motionScheme`), never fixed-duration tweens |
| Touch targets | 48dp minimum, all primary actions in bottom third |
| Edge-to-edge | Mandatory (enforced API 35+). Use `enableEdgeToEdge()`, consume insets properly |
| Predictive back | Enabled and tested — required for a modern feel and Play quality signals |

### Accessibility, treated as non-optional
- Every icon-only control has `contentDescription`
- Full TalkBack pass on Shelf, Search, Detail
- Support text scaling to 200% without clipping — test at max font size
- Never encode meaning in colour alone (category badges carry text or an icon too)
- Respect "reduce motion": fall back to fades

### Habit hooks — how it becomes daily without notifications
1. **Screenshot taken → silent, instant index.** The app earns its place invisibly.
2. **Home-screen widget:** search box + last 3 screenshots. Re-entry without opening the app.
3. **Quick Settings tile:** "Search Shelfie".
4. **Share-sheet target:** share any image into Shelfie.
5. **Notifications: maximum one per week**, and only for genuine value ("312 old screenshots — 780 MB — clear them?"). Never "we miss you". Off by default is acceptable; opt-in during Cleanup.
