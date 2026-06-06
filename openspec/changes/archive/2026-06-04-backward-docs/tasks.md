# Tasks: Backward Documentation

> This is a documentation-only change. All tasks below record what was produced retroactively.
> No code was changed. All items are marked complete.

## 1. Codebase Exploration

- [x] 1.1 Enumerate all controllers and their HTTP endpoints
- [x] 1.2 Enumerate all services and their key methods
- [x] 1.3 Enumerate all MongoDB document models and fields
- [x] 1.4 Enumerate all Thymeleaf templates and the pages they render
- [x] 1.5 Enumerate all repositories and notable query methods
- [x] 1.6 Enumerate all test classes and what they cover (~337 test methods across 23 classes)
- [x] 1.7 Review `application.properties` and `pom.xml` for stack and config details

## 2. Planning Artifacts

- [x] 2.1 Write `proposal.md` — why backward documentation is needed, what capabilities are documented
- [x] 2.2 Write `specs/collection-browsing.md` — `/show` page with filtering, treatment groups, tokens/promos, wizard, top cards, cache clear
- [x] 2.3 Write `specs/set-comparison.md` — `/compare` diff view, tradable filter, view mode toggle
- [x] 2.4 Write `specs/csv-import.md` — DragonShield web/app/inventory formats, async import, history, reset, deck rebuild
- [x] 2.5 Write `specs/statistics.md` — per-user stats, set completion tiers, report caching, set refresh, missing cards modal
- [x] 2.6 Write `specs/sell-suggestions.md` — sellable card criteria, Cardmarket link localisation, revenue estimate, manual refresh
- [x] 2.7 Write `specs/price-watch.md` — daily snapshots, winners/losers, set summaries, price history chart, full price update API
- [x] 2.8 Write `specs/card-search.md` — cross-set search by name and collector number, deduplication
- [x] 2.9 Write `specs/deck-suggest.md` — meta-deck scraping, collection matching, completion calculation, deck detail, force refresh
- [x] 2.10 Write `specs/my-decks.md` — folder prefix convention, Scryfall enrichment, deck list/detail views, deduplication
- [x] 2.11 Write `specs/auth-and-user-mapping.md` — Entra ID OAuth2, email mapping, prod-lock, global model attributes, CSRF rules
- [x] 2.12 Write `specs/nightly-automation.md` — three-step nightly pipeline, Scryfall rate limiting
- [x] 2.13 Write `design.md` — stack decisions, data model decisions, key patterns (report cache, async import, treatment groups, token/promo, prod-lock), deployment architecture, CI/CD pipeline

## 3. Cost Tracking

- [x] 3.1 Update `COSTS.md` with session row for this backward-documentation change
