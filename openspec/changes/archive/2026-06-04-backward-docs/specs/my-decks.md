# Spec: My Decks (`/my-decks`)

## EXISTING Requirements

### Requirement: Deck Parsing from DragonShield Folder Names

DragonShield CSV exports encode deck membership via folder name prefixes. The application parses these to reconstruct physical decks.

#### Scenario: Folder prefix conventions

- **WHEN** a card's folder name starts with `MB_`
- **THEN** the card is added to the mainboard of the deck named after the remainder of the folder string
- **WHEN** a folder name starts with `SB_`
- **THEN** the card is added to the sideboard
- **WHEN** a folder name starts with `EB_`
- **THEN** the card is added to the extra board
- **WHEN** a folder name starts with `MB_CM_`
- **THEN** the card is the commander of the deck and is added to the mainboard with the `isCommander` flag

#### Scenario: Unknown/null folder names

- **WHEN** a card's folder name is null, empty, or does not match any known prefix
- **THEN** the card is not assigned to any deck

---

### Requirement: Deck Scryfall Enrichment

#### Scenario: Thumbnail and price enrichment

- **WHEN** a deck is saved
- **THEN** each `DeckCard` is enriched with: `thumbnailUrl`, `imageUrl`, `price` (foil if foil=true, else regular) from the Scryfall cache

#### Scenario: Fallback image URLs

- **WHEN** a DeckCard has no stored `thumbnailUrl`
- **THEN** the URL is computed as `https://api.scryfall.com/cards/{setCode}/{collectorNumber}?format=image&version=small`
- **AND** analogously for `imageUrl` using `version=normal`

---

### Requirement: Deck List View

#### Scenario: My decks page

- **WHEN** a user navigates to `/my-decks?user=<user>`
- **THEN** all saved decks for that user are shown
- **AND** each deck card displays: name, commander name, mainboard card count, total value, cover art (most expensive card's image)

#### Scenario: Cover art selection

- **WHEN** rendering the deck cover
- **THEN** the card with the highest `priceRegular` in the mainboard is used as cover art
- **WHEN** no card has a price
- **THEN** no cover art is shown

---

### Requirement: Deck Detail View

#### Scenario: Deck detail page

- **WHEN** a user navigates to `/my-decks/detail?id=<id>&user=<user>`
- **THEN** the full deck is displayed with mainboard, sideboard, and extra board sections
- **AND** each card shows: name, quantity, foil status, thumbnail, price
- **AND** the commander card is visually highlighted in the mainboard

---

### Requirement: Deck Deduplication

#### Scenario: Duplicate card keys within a deck

- **WHEN** the same card (setCode + collectorNumber) appears in the same board type multiple times during import
- **THEN** quantities are summed into a single `DeckCard` entry
