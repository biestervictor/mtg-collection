# Spec: Card Search (`/search`)

## EXISTING Requirements

### Requirement: Cross-Set Card Search

The search page finds cards across all sets, not limited to a single set dropdown selection.

#### Scenario: Search by name

- **WHEN** a user submits a search query `?q=<term>&user=<user>`
- **THEN** all Scryfall cards whose name contains `<term>` (case-insensitive) are returned
- **AND** each result is enriched with the user's ownership data (quantity, foil quantity)

#### Scenario: Search by collector number

- **WHEN** a query matches exactly a known collector number format
- **THEN** cards with that exact collector number (across all sets) are returned

#### Scenario: Case-insensitive matching

- **WHEN** a search term is submitted in any combination of upper/lowercase
- **THEN** matching is performed case-insensitively

#### Scenario: No results

- **WHEN** a search term matches no cards
- **THEN** an empty result set is displayed with an appropriate message

---

### Requirement: Duplicate Card Aggregation

#### Scenario: Multiple printings of the same card name

- **WHEN** a card name appears in multiple sets
- **THEN** each printing is shown as a separate result entry
- **AND** the user's per-printing ownership data is shown for each entry
