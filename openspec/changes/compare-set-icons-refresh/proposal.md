## Why

Die Compare-Seite (`/compare`) nutzt ein einfaches Select2-Dropdown ohne Set-Logos fuer die Set-Auswahl. Die Show-Seite (`/show`) hat dagegen ein custom Dropdown mit Scryfall SVG-Icons neben jedem Set. Diese Inkonsistenz verwirrt Nutzer. Ausserdem zeigt die Produktion noch Token-Sets im Dropdown, weil der Set-Cache in MongoDB vor dem Filter-Update befuellt wurde und kein einfacher Refresh-Mechanismus auf der Show/Compare-Seite existiert.

## What Changes

- Compare-Seite erhaelt das identische custom Set-Dropdown wie die Show-Seite (mit SVG-Icons, gleicher Filterlogik, gleicher Darstellung `setCode - name`)
- Select2-Abhaengigkeit auf der Compare-Seite wird durch das custom Dropdown ersetzt
- Ein "Refresh Sets"-Button wird auf der Show-Seite eingefuegt, der `getAllSets(true)` triggert und damit den MongoDB-Cache fuer Set-Metadaten erneuert (loest das Token-Set-Problem in Produktion)

## Capabilities

### New Capabilities
- `set-refresh-button`: Sichtbarer Button auf der Show-Seite zum Erneuern der Set-Liste aus der Scryfall API

### Modified Capabilities

## Impact

- `src/main/resources/templates/compare.html` – Set-Dropdown wird komplett ersetzt (Select2 → custom)
- `src/main/resources/templates/show.html` – Neuer "Refresh Sets"-Button
- `src/main/java/com/mtg/collection/controller/CollectionController.java` – Neuer Endpoint fuer Set-Refresh
- Select2 CSS/JS-Includes auf der Compare-Seite koennen entfernt werden, sofern sie nur fuer die Set-Auswahl genutzt werden
- Kein DB-Schema-Aenderung, keine neuen Abhaengigkeiten
