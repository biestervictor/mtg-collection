# Spec: Statistics (`/statistics`)

## EXISTING Requirements

### Requirement: Per-User Statistics

#### Scenario: Single-user stats page

- **WHEN** a user navigates to `/statistics?user=<user>`
- **THEN** the page shows statistics computed for that user's collection:
  - Total card count (summed quantities)
  - Total collection value in EUR
  - Most expensive cards (top 100 by `priceRegular`)
  - Top 30 sets by card count
  - Top 30 sets by total value
  - Set completion tiers (see below)
  - Daily value change (vs. yesterday's snapshot)
  - Top price winners and losers (absolute EUR change)

#### Scenario: All-users summary

- **WHEN** a user navigates to `/statistics` without a `user` parameter
- **THEN** aggregated statistics across all users are shown

---

### Requirement: Set Completion Tiers

Sets are classified into completion bands based on how many distinct collector numbers the user owns.

#### Scenario: Completion tier classification

- **WHEN** a user owns ≥100% of a set's distinct collector numbers
- **THEN** the set appears in the "Complete" tier
- **WHEN** a user owns ≥90% of a set's distinct collector numbers
- **THEN** the set appears in the "90%+" tier
- **AND** analogous tiers exist for 80%, 70%, 60%, 50%
- **AND** each tier has three sub-views: standard-only, special-frames-only, all artworks

#### Scenario: Token set exclusion

- **WHEN** computing set completion
- **THEN** sets whose code starts with `"t"` followed by a known set code are excluded from completion statistics

---

### Requirement: Report Caching

#### Scenario: Lazy report computation

- **WHEN** the statistics page is loaded and no cached report exists for the user
- **THEN** the report is computed on-demand and cached in memory

#### Scenario: Cache hit

- **WHEN** the statistics page is loaded and a cached report exists
- **THEN** the cached result is returned without recomputing

#### Scenario: Force refresh

- **WHEN** a POST is sent to `/statistics/refresh-reports?user=<user>`
- **THEN** the report cache for that user is invalidated and recomputed
- **AND** the user is redirected back to the statistics page

---

### Requirement: Set Metadata Refresh

#### Scenario: Force-refresh sets from Scryfall API

- **WHEN** a POST is sent to `/statistics/refresh-sets`
- **THEN** all set metadata is re-fetched from the Scryfall API and saved to MongoDB
- **AND** the user is redirected back to the statistics page

---

### Requirement: Missing Cards Modal

#### Scenario: Missing cards AJAX endpoint

- **WHEN** a GET is sent to `/statistics/missing-cards?set=<code>&user=<user>`
- **THEN** a JSON object is returned with:
  - `standard`: list of missing standard-frame cards (name, cn, rarity, thumbnail, price, purchase link)
  - `special`: list of missing special-frame cards
