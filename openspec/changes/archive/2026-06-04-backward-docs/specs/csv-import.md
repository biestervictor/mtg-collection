# Spec: CSV Import (`/import`)

## EXISTING Requirements

### Requirement: DragonShield Web Export Format

#### Scenario: Successful web-format import

- **WHEN** a user uploads a DragonShield web-export CSV (`format=dragonshield_web`)
- **THEN** each row is parsed for: name, set code, collector number, quantity, foil status
- **AND** the user's existing collection for all affected sets is replaced (full diff computed)
- **AND** newly added cards (not previously owned) are reported as "new cards"
- **AND** quantity-increased cards are reported as "added"
- **AND** quantity-decreased-to-zero cards are reported as "removed"
- **AND** an `ImportHistory` document is saved recording the diff

---

### Requirement: DragonShield App Export Format

#### Scenario: Successful app-format import

- **WHEN** a user uploads a DragonShield app-export CSV (`format=dragonshield_app`)
- **THEN** the same import logic applies as for the web format
- **AND** folder names in the CSV are parsed to extract deck memberships (`MB_`, `SB_`, `EB_`, `MB_CM_` prefixes)

---

### Requirement: DragonShield Inventory Export Format

#### Scenario: Successful inventory import

- **WHEN** a user uploads a DragonShield inventory-export CSV (`format=inventory`)
- **THEN** exact-duplicate rows (same folder/card/set/cn/foil) are detected and consolidated
- **AND** a warning listing all deduplicated rows is included in the result
- **AND** unknown set codes (not found in MongoDB) are detected and reported
- **AND** decks are parsed from folder names and saved to `user-decks`

#### Scenario: Unknown set code warning

- **WHEN** an import contains set codes not present in the Scryfall sets cache
- **THEN** the import proceeds with the known sets
- **AND** a list of unknown set codes is included in the import result for user review

#### Scenario: Exact-duplicate row detection

- **WHEN** the same card (folder + name + setCode + collectorNumber + foil) appears more than once in the CSV
- **THEN** quantities are summed into a single record
- **AND** a `DuplicateRowInfo` warning is emitted listing the affected card and occurrence count

---

### Requirement: Async Import

#### Scenario: Start async job

- **WHEN** a POST is sent to `/api/import/start` with a file and format
- **THEN** a UUID job ID is returned immediately
- **AND** the import runs in a background thread

#### Scenario: Poll job status

- **WHEN** a GET is sent to `/api/import/status/{jobId}`
- **THEN** the current state is returned: `RUNNING`, `DONE`, or `ERROR`
- **AND** when `DONE`, the response includes card counts, added/removed diffs, and warning lists
- **AND** when `ERROR`, the response includes an error message

---

### Requirement: Import History

#### Scenario: History listing

- **WHEN** a user navigates to `/import/history`
- **THEN** all past imports for the selected user are shown in reverse-chronological order
- **AND** each entry shows: import date, format, total/unique/added/removed counts
- **AND** each entry can be expanded to show the added/removed card details grouped by set

---

### Requirement: User Data Reset

#### Scenario: Reset user data

- **WHEN** a POST is sent to `/api/user/{user}/reset`
- **THEN** all `UserCard`, `UserDeck`, and `ImportHistory` documents for that user are deleted

---

### Requirement: Deck Rebuild

#### Scenario: Re-enrich decks

- **WHEN** a POST is sent to `/api/user/{user}/rebuild-decks`
- **THEN** all existing decks for that user are re-enriched with current Scryfall thumbnail URLs and prices
- **AND** the count of enriched decks is returned
