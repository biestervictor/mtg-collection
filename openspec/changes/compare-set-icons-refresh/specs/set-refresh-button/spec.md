## ADDED Requirements

### Requirement: Refresh Sets Button auf Show-Seite
Die Show-Seite MUSS einen Button "Refresh Sets" enthalten, der die Set-Liste aus der Scryfall API neu laedt und den MongoDB-Cache fuer Sets aktualisiert.

#### Scenario: Benutzer klickt Refresh Sets
- **WHEN** ein Benutzer den "Refresh Sets"-Button klickt
- **THEN** wird `getAllSets(true)` aufgerufen (force refresh)
- **THEN** wird der Benutzer zurueck auf `/show` weitergeleitet
- **THEN** zeigt das Set-Dropdown die aktualisierte Liste (ohne Token/Promo-Sets)

#### Scenario: Refresh entfernt veraltete Token-Sets aus dem Dropdown
- **WHEN** der Set-Cache Token-Sets enthaelt und ein Refresh durchgefuehrt wird
- **THEN** werden Token-Sets (`setType=token`) und Promo-Sets (`setType=promo`) im Dropdown nicht mehr angezeigt

### Requirement: Refresh Sets Endpoint
Es MUSS ein `POST /api/sets/refresh` Endpoint existieren, der die Sets aus Scryfall neu laedt.

#### Scenario: Erfolgreicher Refresh
- **WHEN** ein POST-Request an `/api/sets/refresh` gesendet wird
- **THEN** ruft der Server `scryfallService.getAllSets(true)` auf
- **THEN** wird ein Redirect auf die aufrufende Seite zurueck geschickt

### Requirement: Compare-Seite Set-Dropdown identisch zu Show
Die Compare-Seite MUSS das gleiche custom Set-Dropdown verwenden wie die Show-Seite, inklusive Scryfall SVG-Icons neben jedem Set-Eintrag.

#### Scenario: Set-Dropdown mit Icons auf Compare
- **WHEN** ein Benutzer die Compare-Seite oeffnet
- **THEN** zeigt das Set-Dropdown fuer jedes Set ein SVG-Icon (aus `ScryfallSet.icon`)
- **THEN** ist das Dropdown durchsuchbar (Textfilter auf setCode und Name)
- **THEN** zeigt das aktuell ausgewaehlte Set sein Icon inline an

#### Scenario: Gleiches Format wie Show-Seite
- **WHEN** ein Benutzer Sets im Compare-Dropdown sieht
- **THEN** ist das Format `setCode - Name` (z.B. "tdm - Tarkir: Dragonstorm")
- **THEN** haben die Eintraege die gleiche optische Darstellung wie auf der Show-Seite

#### Scenario: Vorauswahl wird korrekt restauriert
- **WHEN** ein Compare-Formular mit vorausgewaehltem Set geladen wird
- **THEN** zeigt das Dropdown den ausgewaehlten Set mit Icon und Label an
