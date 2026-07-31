# Brand: Name, Psychology, Logo Spec

## The naming problem

The category is full of descriptive names: SnapSense, SnapSort, SmartShots, Screenshot Organizer, Screenshot Organizer: Finder, Screeny. They are interchangeable and forgettable — a user cannot recall which one they installed.

Two psychological constraints matter here:

1. **Avoid "Organizer" / "Manager" in the brand word.** Those words describe *work the user must do*. The emotional state of someone with 4,000 screenshots is low-grade guilt. A name that implies more chores increases friction. Keep those words in the *subtitle* for ASO, never in the brand.
2. **The name must be recallable after hearing it once.** In utilities, word-of-mouth is the cheapest channel, and it only works if the name survives one verbal mention.

---

## Recommendation: **Shelfie**

**Play Store title:** `Shelfie: Screenshot Finder` (28 chars — under the 30-char limit)

### Why this wins

| Criterion | How it scores |
|---|---|
| Recall after one exposure | **Very high.** Rhymes with "selfie" — a pre-installed mnemonic hook in every phone user's head |
| Explains the product | A shelf is where you put things *so you can find them later*. The metaphor is the entire product |
| Emotional tone | Playful and light, not clerical. Reduces the guilt association |
| Spelling | Phonetic, 7 letters, no ambiguity for Indian or global users |
| Verbal shareability | "Just get Shelfie" is easy to say and impossible to mis-hear |
| Marketing | Puns write themselves: "Take a Shelfie." Instantly usable for Reels/Shorts creative |
| ASO | Brand is distinctive (rankable), keywords live in the subtitle |

### Risk you must clear before committing
There have been **"Shelfie" apps for book-collection photos** (largely defunct). Before you buy anything:
- Search Play Store for the exact string
- Check trademark registries: [Indian IP search](https://tmrsearch.ipindia.gov.in/tmrpublicsearch/), [USPTO TESS](https://tmsearch.uspto.gov/), [EUIPO](https://euipo.europa.eu/eSearch/)
- Check `shelfie.app`, `getshelfie.com` availability

**I cannot verify trademark availability reliably — you must do this before spending on branding.** It's a 30-minute job that prevents a forced rename later.

---

## Alternates (ranked, in case Shelfie is blocked)

**2. Snapshelf** — `Snapshelf: Screenshot Search`
Safest all-round pick. Contains "snap" (ASO discoverability) plus the shelf metaphor. Fully self-explanatory, zero ambiguity, almost certainly available. Less memorable than Shelfie, but no downside.

**3. Sift** — `Sift: Find Any Screenshot`
Minimal and confident. "Sift" is exactly what the product does — separate signal from clutter. One syllable, premium feel. Weakness: a common English word, so trademark protection is weak and ASO is harder.

**4. Kept** — `Kept: Your Screenshot Memory`
Warmest emotionally. Past tense implies the work is already done — psychologically the strongest promise of relief. Weakness: abstract, doesn't self-describe.

**Avoid:** anything starting with "Snap" *alone* (three direct competitors already do), anything with "AI" in the name (2026 users are desensitised, and it dates the app), and anything over 9 letters.

---

## Package ID

```
com.shelfie.app
```

Set this **before first upload — it is permanent and unchangeable on Play.** Do not use a placeholder like `com.example.*`; Play will reject it. If you own a domain, prefer reverse-domain form (`app.shelfie.android`).

---

## Logo specification

### Concept: the shelf that holds a screenshot

A single horizontal shelf line with one rounded rectangle (a screenshot) resting on it, and the negative space above forming an implied **S**. Reads as a shelf at large sizes and as a bold mark at 48px.

```
   ╭───────────╮
   │  ▁▁▁▁▁▁   │      rounded rect = the screenshot,
   │ ▕      ▏   │      sitting on a thick shelf line,
   │ ▕      ▏   │      slight upward tilt = "lifted, sorted"
   │ ▔▔▔▔▔▔▔▔▔ │      ← shelf (thick, full-bleed to edges)
   ╰───────────╯
```

### Why this and not a magnifying glass or folder
- **Magnifying glass** = every search app ever. Zero recall.
- **Folder** = filing work. Wrong emotion.
- **Camera/scissors** = says "take a screenshot", not "find one".
- A **shelf** is unclaimed in this category and it encodes the brand name, so icon and name reinforce each other.

### Colour

| Token | Hex | Use |
|---|---|---|
| Brand Deep | `#12122A` | Icon background, dark theme surface |
| Brand Signal | `#FFD24A` | The screenshot tile, primary accent, FAB |
| Brand Shelf | `#F5F5FA` | Shelf line, icon foreground on dark |

**Rationale for amber `#FFD24A`:** the category is saturated with blue and purple (default Material). Amber/yellow is the highest-attention hue at small sizes, survives Play Store thumbnail compression, and is visually distinct in a search results row. It also reads as "highlighted / marked important", which matches the product.

### Deliverables and hard requirements

| Asset | Spec | Notes |
|---|---|---|
| Play Store icon | **512 × 512 px, 32-bit PNG, no alpha, no rounded corners** | Play adds the mask itself. Rounding it yourself causes visible double-rounding |
| Adaptive icon | Foreground + background XML/vector, 108 × 108 dp canvas | Keep all detail inside the central **66 dp** safe circle |
| Monochrome layer | Single-path vector | Required for Android 13+ themed icons. Skipping this makes your icon look broken on themed home screens |
| Notification icon | 24 × 24 dp, white, transparent | Silhouette only |
| Feature graphic | **1024 × 500 px** | No text near edges; Play crops it on some surfaces |
| Screenshots | Min 2, 1080 × 1920 or higher | See `store/listing-copy.md` for the exact sequence |

### Icon do-not list
- No text or letters inside the icon (illegible at 48px, and Play discourages it)
- No gradients with more than two stops (banding after compression)
- No thin strokes below 4dp at 108dp scale (they vanish when downscaled)
- Do not put a screenshot *of the app* in the icon

---

## Voice

Plain, short, slightly dry. Never exclamation marks. Never "Awesome!" or "Oops!".

- Empty state: `Nothing here yet. Take a screenshot and it'll land on the shelf.`
- Permission rationale: `Shelfie reads your screenshots to make them searchable. It happens on your phone. Nothing is uploaded — Shelfie has no internet permission at all.`
- Error: `Couldn't read that one. Tap to retry.`
- Paywall: `One payment. Yours forever. No subscription.`
