# Spec: Sell Suggestions (`/sell-suggestions`)

## EXISTING Requirements

### Requirement: Sellable Card Identification

A card is considered sellable when the user owns more copies than needed for any deck and the market price exceeds the minimum threshold.

#### Scenario: Sell suggestion criteria

- **WHEN** the sell suggestions page is loaded for a user
- **THEN** only cards meeting ALL of the following criteria are listed:
  1. Total quantity (regular + foil) > 1
  2. Not used in any of the user's saved decks
  3. `priceRegular` (or `priceFoil` if regular is null/zero) ≥ €0.50

#### Scenario: Sorting

- **WHEN** sell suggestions are displayed
- **THEN** they are sorted by total potential revenue descending (`pricePerCard × sellableQty`)

---

### Requirement: Cardmarket Links

#### Scenario: Localised purchase links

- **WHEN** a Scryfall card has a `purchaseLink` pointing to cardmarket.com
- **THEN** the link is localised to the German Cardmarket domain (`cardmarket.com/de/...`)
- **AND** a seller country filter (`sellerCountry=7`) is appended to the URL

---

### Requirement: Revenue Estimate

#### Scenario: Total revenue display

- **WHEN** sell suggestions are shown
- **THEN** a total potential revenue figure is displayed (sum of all `pricePerCard × sellableQty`)

---

### Requirement: Manual Refresh

#### Scenario: Force-refresh sell suggestions

- **WHEN** a POST is sent to `/sell-suggestions/refresh?user=<user>`
- **THEN** the cached sell suggestions for that user are invalidated and recomputed
- **AND** the user is redirected back to the sell suggestions page with a confirmation flash message
