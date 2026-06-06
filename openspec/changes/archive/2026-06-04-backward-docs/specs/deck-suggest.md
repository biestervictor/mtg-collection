# Spec: Deck Suggestions (`/deck-suggest`)

## EXISTING Requirements

### Requirement: Meta-Deck Data

Meta-deck lists are scraped from MTGGoldfish and cached in MongoDB.

#### Scenario: Daily cache

- **WHEN** meta-decks for a format are requested and the cached data was fetched today
- **THEN** the cached data is returned without scraping
- **WHEN** the cached data is from a previous day
- **THEN** MTGGoldfish is scraped for the top 15 decks of that format
- **AND** the results are saved to MongoDB

#### Scenario: Scrape failure fallback

- **WHEN** scraping MTGGoldfish fails for all archetypes
- **THEN** the existing stale cache is returned rather than an error

#### Scenario: Supported formats

- **WHEN** a format is requested
- **THEN** valid formats are: `commander`, `modern`, `pioneer`, `standard`, `legacy`

---

### Requirement: Collection Matching

#### Scenario: Completion calculation

- **WHEN** a user views deck suggestions for a format
- **THEN** each meta-deck is compared against the user's collection
- **AND** `completionPercent` = ownedUniqueCards / totalUniqueCards × 100 (rounded)
- **AND** basic lands are excluded from the missing-card count
- **AND** missing cards are listed with the minimum price across all printings the user does not own

#### Scenario: Sorting

- **WHEN** deck suggestions are displayed
- **THEN** suggestions are sorted by fewest missing unique cards ascending, then by highest completion percent descending

---

### Requirement: Deck Detail

#### Scenario: Full deck card list

- **WHEN** a user navigates to `/deck-suggest/detail?format=<f>&slug=<s>&user=<u>`
- **THEN** the deck's mainboard is displayed grouped into: Commander (if applicable), Mainboard, Basic Lands
- **AND** each card shows: name, owned quantity, required quantity, thumbnail, price
- **AND** missing cards are visually highlighted

---

### Requirement: Force Refresh

#### Scenario: Manual meta-deck refresh

- **WHEN** a POST is sent to `/api/meta-decks/refresh?format=<f>`
- **THEN** MTGGoldfish is scraped regardless of cache age
- **AND** the response includes `{format, count}` where `count` is the number of decks scraped
