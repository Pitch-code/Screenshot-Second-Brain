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
