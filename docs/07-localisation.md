# Localisation

## Status

**The engineering work is done. The translations are not, deliberately.**

All 130 user-facing strings live in `res/values/strings.xml` files, one per module.
There are **zero hardcoded UI strings** left, verified by lint with `HardcodedText`
promoted to an error. Adding a language is now a matter of dropping in a
`values-<lang>/strings.xml` — no code changes at all.

## Why there are no Hindi, Tamil, Telugu or Bengali translations yet

The roadmap called for `en + hi + ta + te + bn`. I have not produced them, and
that is a considered decision rather than an omission.

Machine-generated translations in this specific app would be worse than English:

- The app handles **payments, OTPs and identity documents**. A mistranslated
  string next to someone's bank balance or Aadhaar number damages trust in a way
  that is very hard to recover from.
- Several strings carry the product's core promise — *"Shelfie has no internet
  permission… it physically cannot upload anything"*. If that sentence is even
  slightly wrong in translation, it becomes a **false claim about data handling**,
  which is both a trust failure and a Play policy risk.
- Plural rules differ per language. Getting them wrong reads as broken grammar to
  a native speaker, and it is the sort of error that makes an app feel cheap.

So the correct next step is a native speaker or a professional service, not
generated output. The structure is ready for them.

## How to add a language

1. Create `values-hi/strings.xml` (or `-ta`, `-te`, `-bn`) in **each** module that
   has strings — see the table below.
2. Copy the `<string>` and `<plurals>` entries and translate the values only.
   Never change a `name`.
3. Preserve every positional argument: `%1$d`, `%1$s`, `%2$s`. Reordering them in
   the translated sentence is fine and expected; dropping one crashes at runtime.
4. Provide **all plural categories the target language needs**. Hindi and Bengali
   use `one`/`other`; Tamil and Telugu likewise. Do not assume English's two forms
   are universal.
5. Re-enable the check: remove `MissingTranslation` from the `disable` list in
   `AndroidApplicationConventionPlugin`, and lint will then enforce completeness.

## Where the strings live

| Module | Strings | Notes |
|---|---|---|
| `core/designsystem` | Category names, action labels, index status, Limited Mode banner, accessibility descriptions | Shared across every screen |
| `feature/onboarding` | The three onboarding screens | **Highest-stakes copy** — includes the privacy claim |
| `feature/shelf` | Empty states, date headers | |
| `feature/search` | Placeholder, empty and no-result states | |
| `feature/cleanup` | Group titles, delete confirmation, recovery note | Storage figures are formatted, not translated |
| `feature/settings` | All settings rows, purchase copy | |
| `feature/detail` | Detail sheet headings | |
| `app` | App name, widget, tile, share feedback | App name should **not** be translated |

## Things that are already locale-aware without translation

- **Dates** — `ShelfDateFormatter` resolves `Locale.getDefault()` per call rather
  than caching it, so changing language at runtime is handled. "Today" and
  "Yesterday" are a `DateLabel` type resolved to strings by the UI, not
  pre-baked English.
- **Storage sizes** — `ByteFormat` formats with the active locale, so decimal
  separators follow the user's convention.
- **Prices** — taken as `formattedPrice` straight from Play Billing, which is
  already localised and currency-correct. Never hardcode a price.
- **Billing errors** — Play supplies these already localised, so they pass
  through as-is rather than being mapped to our own strings.

## Text expansion

Hindi and Tamil commonly run 20–30% longer than English. The layouts use `wrap`
and weight-based sizing rather than fixed widths, but this is unverified on a
device. When testing a translation, check:

- Onboarding buttons and the purchase CTA (longest single strings)
- Category filter chips (horizontal scroll, so overflow is safe but truncation looks poor)
- Cleanup card subtitles, which combine a count and a size

Also test at **200% font scale**, which is a separate axis from translation and
where clipping usually appears first.
