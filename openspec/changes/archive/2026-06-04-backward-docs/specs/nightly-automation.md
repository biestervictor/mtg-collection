# Spec: Nightly Automation

## EXISTING Requirements

### Requirement: Nightly Price Update Pipeline

The application runs a three-step nightly pipeline to keep prices and reports current.

#### Scenario: Step 1 — Scryfall price refresh (00:02)

- **WHEN** the cron job fires at 00:02 server time
- **THEN** Scryfall EUR prices are fetched for all sets that appear in any user's `UserCard` collection
- **AND** `UserCard.price` and `priceUpdatedAt` are updated where the new price differs by more than €0.01
- **AND** no update is written when the price has not materially changed

#### Scenario: Step 2 — Price history snapshot (03:00)

- **WHEN** the cron job fires at 03:00 server time
- **THEN** a daily price snapshot is written for every `UserCard` with a known Scryfall price
- **AND** the snapshot document `_id` is `setCode_collectorNumber_date` (idempotent upsert)
- **AND** `PriceHistory` documents older than 90 days are deleted

#### Scenario: Step 3 — Report pre-computation (03:30)

- **WHEN** the cron job fires at 03:30 server time
- **THEN** statistics and sell suggestions are recomputed for all users
- **AND** the results are stored in the in-memory `ReportCacheService`
- **AND** if computation fails for one user, it does not prevent computation for other users

---

### Requirement: Rate Limiting for Scryfall API

#### Scenario: API rate limit compliance

- **WHEN** targeted price updates iterate over multiple sets
- **THEN** a 150ms delay is inserted between each set's API request
- **AND** requests are never sent faster than this minimum interval
