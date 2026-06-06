# Design: MTG Collection Manager — Architecture and Key Decisions

## Context

The MTG Collection Manager is a private web application for two users (Victor and Andre) to track their Magic: The Gathering card collections. It started as a Node.js project (`mtg_old`) and was rewritten in Spring Boot to gain a richer ecosystem and better long-term maintainability. This document records the key architectural decisions made during and after that rewrite.

Current version: v0.1.89

---

## Goals / Non-Goals

**Goals:**
- Track card ownership per user (quantity, foil, price)
- Import collections from DragonShield CSV exports (multiple formats)
- Show set completion across standard and special-frame treatments
- Compare two users' collections to find trade opportunities
- Track price history and surface notable movers
- Suggest meta-decks the user is close to completing
- Manage decks parsed from the DragonShield folder convention

**Non-Goals:**
- Support for more than two named users (hard-coded; not a general SaaS)
- Real-time price streaming
- Trading/transaction management
- Mobile-native app (web-only)

---

## Stack Decisions

### Decision 1: Spring Boot 3 + Thymeleaf (Server-Side Rendering)

Server-side rendering with Thymeleaf was chosen over a SPA (React/Vue) because:
- The UI is data-heavy but not highly interactive — most interactions are form submits and page navigations
- SSR eliminates a separate frontend build pipeline and keeps deployment simple
- Thymeleaf integrates tightly with Spring Security's CSRF protection
- The only interactive pieces (price chart modal, async import progress, compare auto-submit) are handled with jQuery/vanilla JS

### Decision 2: MongoDB

MongoDB was chosen as the primary data store because:
- Scryfall card data is deeply nested JSON — mapping it to relational tables would require many joins
- Card sets, decks, price history all have varying shapes that benefit from schema flexibility
- The embedded-document model (DeckCard inside UserDeck, MetaDeckCard inside MetaDeck) avoids costly joins for read-heavy pages
- Spring Session MongoDB provides session persistence without a separate Redis deployment

### Decision 3: Microsoft Entra ID (Azure AD) OAuth2/OIDC

Entra ID was chosen for authentication because:
- Both users already have Microsoft accounts
- It eliminates password management entirely
- Spring Security's OAuth2 client integration handles all token lifecycle management
- OIDC discovery is explicitly configured (not auto-discovered) to avoid startup-time network calls on the Raspberry Pi cluster

### Decision 4: Spring Session MongoDB (not in-memory)

HTTP sessions are persisted in MongoDB rather than held in JVM memory:
- Kubernetes pods can be restarted (image updates, crashes) without users being logged out
- The 7-day session timeout is long enough that users only need to log in rarely
- No additional infrastructure (Redis) is required

---

## Data Model Decisions

### Decision 5: Scryfall Card Deduplication by Collector Number

Scryfall's `unique=prints` parameter returns multiple printings per card name within a set (e.g., regular + foil treatment variants). When merging new API data with existing MongoDB documents, deduplication is done by `(setCode, collectorNumber)` rather than by Scryfall UUID, because:
- A set/cn combination uniquely identifies a card face within the context of a user's collection
- DragonShield exports use set code + collector number as the natural key
- This avoids drift when Scryfall occasionally retires and recreates card UUIDs

### Decision 6: UserCard Stores Quantity, Not Individual Card Instances

Each `UserCard` document stores a `quantity` integer rather than one document per physical card. This:
- Keeps the collection to O(unique cards) documents rather than O(total cards owned)
- Simplifies diff computation during import (compare quantities, not lists of instances)
- Makes aggregation queries (total count, total value) fast single-collection scans

### Decision 7: Price History Keyed on `setCode_cn_date`

`PriceHistory._id = setCode + "_" + collectorNumber + "_" + date` makes the daily snapshot job idempotent: re-running it on the same day is a safe upsert. The 90-day retention window is pruned at snapshot time to keep the collection bounded.

### Decision 8: MetaDeck Keyed on `format_slug`

`MetaDeck._id = format + "_" + slug` means each archetype within a format has a stable, human-readable key. Refreshing a format replaces documents in-place rather than delete-all-insert-all, which reduces write amplification.

---

## Key Patterns

### Pattern 1: Report Cache Service (Lazy Compute + Scheduled Warm-up)

Statistics and sell suggestions are expensive to compute (full collection scan + price joins). Two strategies are combined:
1. **Lazy compute**: On cache miss, compute on-demand and store in `ConcurrentHashMap`
2. **Scheduled warm-up**: The nightly 03:30 job pre-computes reports for all users so the first daytime page load is fast

This avoids a dedicated caching layer (Redis/Caffeine) while still being responsive.

### Pattern 2: Async Import with Job Status Polling

Large inventory CSVs can take several seconds to process. The async import pattern:
1. `POST /api/import/start` returns a UUID job ID immediately (HTTP 200)
2. The import runs in a `@Async` thread pool (`ImportAsyncRunner`)
3. The browser polls `GET /api/import/status/{jobId}` every second
4. On `DONE` or `ERROR`, the browser renders the result inline

Job state is held in-memory (`ConcurrentHashMap<String, ImportJobStatus>`). Pod restart loses in-flight jobs — acceptable given the short duration.

### Pattern 3: Treatment Groups for Card Grid Ordering

Cards in a set are classified into treatment groups to control the rendering order on the show page and the Missing Card Wizard groupings:

```
Normal → Showcase → Extended Art → Borderless → Retro Frame (1993/1997) → Full Art
```

Classification uses `ScryfallCard.frameStatus` (comma-joined Scryfall `frame_effects`), `borderColor`, `fullArt`, and `frame` (year string). The `StatisticsService.isSpecialFrame()` static method is the canonical classifier.

### Pattern 4: Token/Promo Set Separation

Scryfall uses set type `token` and `promo` for related sets. The application:
- **Stores** all sets in MongoDB (they pass the API filter)
- **Excludes** them from all dropdowns (`getAllSets` filter)
- **Exposes** them as opt-in extra sections on the show page via `showTokens` / `showPromos` parameters
- **Derives** their set codes by convention: `"t" + mainSetCode` and `"p" + mainSetCode`

### Pattern 5: Prod-Lock via MongoDB URI Detection

Production is distinguished from development by checking whether the MongoDB URI contains the dev hostname (`mongodb-service.treasury.svc.cluster.local`). This is used to:
- Prevent email→app-user mapping from being overwritten in production
- Control which environment-specific banners/warnings are shown in templates

**Known limitation**: Any non-dev environment that doesn't use this exact hostname will be treated as production. A future improvement would use an explicit `app.prod=true` property instead.

---

## Deployment Architecture

```
Internet
    │
    ▼
nginx Ingress (MicroK8s)
    │  TLS termination
    │  proxy_body_size = 0  (large CSV uploads)
    │  proxy_read_timeout = 600s
    ▼
Spring Boot Pod (ARM64, Raspberry Pi 4)
    │  Port 8080
    │  Reads AZURE_CLIENT_ID / AZURE_CLIENT_SECRET from K8s Secret
    │  (sourced from Azure Key Vault via External Secrets Operator)
    ▼
MongoDB  (192.168.178.141:27017, in-cluster service)
    │
    ├── scryfall-cards      (Scryfall card cache)
    ├── scryfall-sets       (Set metadata cache)
    ├── card-collection     (UserCard documents)
    ├── user-decks          (UserDeck documents)
    ├── import-history      (ImportHistory documents)
    ├── meta-decks          (MetaDeck documents)
    ├── price-history       (PriceHistory snapshots)
    ├── user_email_mappings (OAuth email → app user)
    └── spring:session:*    (Spring Session)
```

### CI/CD Pipeline (GitHub Actions → ArgoCD)

1. Push to `dev` branch triggers `.github/workflows/maven.yml`
2. Maven build + tests run
3. Docker image built for `linux/arm64` and pushed to `ghcr.io/biestervictor/mtg-collection`
4. `pom.xml` version is bumped and `helmcharts/values.yaml` image tag is updated (CI commit)
5. ArgoCD detects the `dev` branch change and deploys to `mtg-dev.biester.vip`
6. After manual validation, `dev` is merged to `main`
7. ArgoCD detects the `main` branch change and deploys to `mtg-kubitos.biester.vip`

---

## Dependencies of Note

| Dependency | Why |
|-----------|-----|
| `jsoup` | HTML scraping of MTGGoldfish for meta-deck lists |
| `commons-csv` | DragonShield web/app CSV parsing (RFC 4180 compliant) |
| `opencsv` | DragonShield inventory CSV parsing (positional columns, different dialect) |
| `Chart.js` (CDN) | Price history sparkline chart in price-watch modal |
| `Select2` (webjar) | Enhanced multi-select dropdowns on compare page |
| `Bootstrap Icons` | Icon set throughout the UI |
