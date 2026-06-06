# Spec: Set Comparison (`/compare`)

## EXISTING Requirements

### Requirement: Two-User Diff View

The compare page shows which cards one user has that the other does not, for a selected set.

#### Scenario: Diff calculation

- **WHEN** a user navigates to `/compare?set=<code>&user=<user>&compareUser=<other>`
- **THEN** the left column shows cards in `user`'s collection that are NOT in `compareUser`'s collection (identified by collector number)
- **AND** the right column shows cards in `compareUser`'s collection that are NOT in `user`'s collection
- **AND** cards present in both collections do not appear in either column

---

### Requirement: Tradable Filter

#### Scenario: Show only tradable cards

- **WHEN** `onlyTradableUser=true`
- **THEN** the left column is further filtered to cards where `user` owns quantity > 1
- **WHEN** `onlyTradableCompare=true`
- **THEN** the right column is further filtered to cards where `compareUser` owns quantity > 1

#### Scenario: Auto-submit on toggle change

- **WHEN** the user toggles the tradable checkbox
- **THEN** the form is submitted automatically without requiring a separate submit action

---

### Requirement: View Mode Toggle

#### Scenario: View mode switching

- **WHEN** `viewMode=list`
- **THEN** cards are displayed in a compact list format
- **WHEN** `viewMode=grid` (default)
- **THEN** cards are displayed as a card-image grid

#### Scenario: Auto-submit on view mode change

- **WHEN** the user changes the view mode selector
- **THEN** the form is submitted automatically

---

### Requirement: Set Dropdown

#### Scenario: Set selection on compare page

- **WHEN** the compare page is rendered
- **THEN** the set dropdown contains the same filtered set list as the show page (no token/promo sets)
