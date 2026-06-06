# Spec: Collection Browsing (`/show`)

## EXISTING Requirements

### Requirement: Set Selection

The user selects a Magic: The Gathering set from a dropdown. The page displays all cards from that set, merged with the user's ownership data.

#### Scenario: Default view

- **WHEN** a user navigates to `/show?set=<setCode>&user=<user>`
- **THEN** all Scryfall cards for that set are displayed in a grid
- **AND** each card shows the user's owned quantity (regular and foil)
- **AND** cards the user does not own are shown as "missing" (greyed out)

#### Scenario: Set dropdown content

- **WHEN** the set dropdown is rendered
- **THEN** it contains only non-digital, non-token, non-promo sets (setType ≠ `token`, `promo`; `digital = false`)
- **AND** sets are ordered by release date descending

---

### Requirement: Multi-Dimensional Filtering

The card grid can be filtered along six independent axes simultaneously.

#### Scenario: Filter by ownership state

- **WHEN** `state=OWNED`
- **THEN** only cards with quantity > 0 (regular or foil) are shown
- **WHEN** `state=MISSING`
- **THEN** only cards with quantity = 0 are shown
- **WHEN** `state=TRADABLE`
- **THEN** only cards with total quantity > 1 are shown
- **WHEN** `state=ALL` (default)
- **THEN** all cards are shown

#### Scenario: Filter by rarity

- **WHEN** `rarity=MYTHIC|RARE|UNCOMMON|COMMON`
- **THEN** only cards of that rarity are shown

#### Scenario: Filter by printing

- **WHEN** `printing=FOIL`
- **THEN** only cards where the user owns at least one foil copy are shown

#### Scenario: Filter by frame style

- **WHEN** `frameStyle=showcase`
- **THEN** only cards with `frameStatus` containing `showcase` are shown
- **AND** analogous behaviour for `extendedart`, `borderless`, `retroframe`, `fullart`

#### Scenario: Search by name

- **WHEN** `search=<term>`
- **THEN** only cards whose name contains the term (case-insensitive) are shown

#### Scenario: Basic lands

- **WHEN** `showBasics=false` (default)
- **THEN** basic land cards (Plains, Island, Swamp, Mountain, Forest, Wastes) are excluded
- **WHEN** `showBasics=true`
- **THEN** basic lands are included

---

### Requirement: Treatment Group Dividers

Cards are organised into visual groups on the grid.

#### Scenario: Standard and special frame separation

- **WHEN** a set contains both standard-frame and special-frame cards
- **THEN** standard cards are rendered first
- **AND** a section divider (treatment group header) separates the special-frame cards
- **AND** each treatment group shows its name and counts (owned / total)

---

### Requirement: Token and Promo Extra Sections

#### Scenario: Token section toggle

- **WHEN** `showTokens=true`
- **THEN** cards from the token set (`"t" + setCode`) are fetched and rendered in a separate section below the main grid
- **WHEN** `showTokens=false` (default)
- **THEN** no token cards are shown and no token set lookup is performed

#### Scenario: Promo section toggle

- **WHEN** `showPromos=true`
- **THEN** cards from the promo set (`"p" + setCode`) are rendered in a separate section below the main grid
- **WHEN** `showPromos=false` (default)
- **THEN** no promo cards are shown

---

### Requirement: Missing Card Wizard

#### Scenario: Wizard modal

- **WHEN** the user clicks "Show in Wizard" for a set + user combination
- **THEN** a modal opens listing all missing cards grouped by treatment
- **AND** each card shows: name, collector number, rarity, thumbnail, prices (regular/foil), and Cardmarket purchase link
- **AND** if the other user owns a tradable copy, that user is listed as a potential trade source (`traders`)

---

### Requirement: Top Cards Sidebar

#### Scenario: Top cards by rarity

- **WHEN** the top-cards sidebar is loaded for a set
- **THEN** up to N cards per rarity (Mythic / Rare / Uncommon / Common) are returned ordered by `priceRegular` descending
- **AND** each entry includes the user's ownership data when `?user=` is provided

---

### Requirement: Cache Clear

#### Scenario: Per-set cache clear

- **WHEN** a POST is sent to `/api/cache/clear?setCode=<code>`
- **THEN** all Scryfall card documents for that set are deleted from MongoDB
- **AND** the user is redirected to `/show?set=<code>`

#### Scenario: Full cache clear

- **WHEN** a POST is sent to `/api/cache/clear` without `setCode`
- **THEN** all Scryfall card and set documents are deleted
- **AND** the user is redirected to `/show`
