# Spec: Price Watch (`/price-watch`)

## EXISTING Requirements

### Requirement: Daily Price Snapshots

#### Scenario: Snapshot creation

- **WHEN** the nightly scheduler runs at 03:00
- **THEN** for every `UserCard` with a known Scryfall `priceRegular` or `priceFoil`
- **AND** a `PriceHistory` document is upserted with `_id = setCode_cn_date`
- **AND** documents older than 90 days are pruned

#### Scenario: Idempotency

- **WHEN** the snapshot job is triggered twice on the same calendar day
- **THEN** the second run is a no-op (existing documents are updated in place, not duplicated)

---

### Requirement: Winners and Losers

#### Scenario: Top price movers

- **WHEN** the price-watch page is loaded
- **THEN** the top N cards with the largest positive EUR price change (comparing the two most recent distinct snapshot dates) are shown as "winners"
- **AND** the top N cards with the largest negative EUR price change are shown as "losers"
- **AND** cards with `priceRegular` below the minimum threshold (default €0.50) are excluded

---

### Requirement: Per-Set Price Summaries

#### Scenario: Set accordion

- **WHEN** the price-watch page is loaded
- **THEN** sets with notable price movers are shown in collapsible accordion sections
- **AND** each section lists the top winners and losers within that set

---

### Requirement: Card Price History Chart

#### Scenario: Per-card history modal

- **WHEN** a user clicks on a card in the price-watch page
- **THEN** a modal opens showing a Chart.js line chart of `priceRegular` over the last 90 days
- **AND** the data is fetched from `/api/price-watch/history?setCode=<code>&cn=<cn>`

---

### Requirement: Full Price Update

#### Scenario: Trigger full update

- **WHEN** a POST is sent to `/api/prices/update`
- **THEN** Scryfall prices are refreshed for all sets owned by any user
- **AND** `UserCard.price` and `priceUpdatedAt` are updated where the price has changed by more than €0.01
- **AND** a history snapshot is taken
- **AND** the response includes `{totalUpdated, perUser, snapped}`

#### Scenario: Single card refresh

- **WHEN** a POST is sent to `/api/prices/refresh-card?setCode=<code>&collectorNumber=<cn>`
- **THEN** the card is re-fetched from the Scryfall API
- **AND** the updated `priceRegular`, `priceFoil`, and `purchaseLink` are returned
