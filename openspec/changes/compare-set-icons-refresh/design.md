## Context

Die Show-Seite hat seit dem letzten Redesign ein custom Set-Dropdown mit SVG-Icons (Scryfall `icon_svg_uri`). Die Compare-Seite nutzt weiterhin Select2, ohne Icons. Es existiert bereits ein `POST /statistics/refresh-sets` Endpoint und ein `POST /api/cache/clear` Endpoint, aber kein direkter "Refresh Sets"-Button auf Show/Compare.

In Produktion werden noch Token-Sets angezeigt, weil der MongoDB-Cache (`scryfall-sets` Collection) vor dem Hinzufuegen des `setType`-Filters befuellt wurde. Ein `getAllSets(true)` wuerde die DB neu befuellen und der Filter greift dann korrekt.

## Goals / Non-Goals

**Goals:**
- Compare-Seite erhaelt identisches Set-Dropdown wie Show-Seite (custom, mit Icons)
- "Refresh Sets"-Button auf der Show-Seite, der die Set-Liste aus Scryfall neu laedt
- Token-Set-Problem in Produktion loesbar ohne DB-Zugang

**Non-Goals:**
- Aenderung der Set-Filterlogik im Backend (funktioniert korrekt, nur Cache ist veraltet)
- Redesign weiterer UI-Elemente auf Compare
- Automatischer Refresh-Schedule fuer Sets (nightly price-update existiert bereits)

## Decisions

### 1. Custom Dropdown statt Select2 auf Compare
**Rationale:** Identische UX auf beiden Seiten. Der Show-Page-Code (CSS, HTML-Struktur, JS) kann 1:1 uebernommen werden. Select2 ist dann auf Compare nicht mehr noetig.
**Alternative:** Select2 mit Custom Formatter fuer Icons – abgelehnt, da unnoetige Abhaengigkeit und andere Optik.

### 2. Refresh-Button nur auf Show-Seite
**Rationale:** Show ist die meistgenutzte Seite und dort faellt ein fehlendes/falsches Set zuerst auf. Statistics hat bereits einen Button ("Refresh Set Counts"). Ein dritter Ort waere verwirrend.
**Position:** Neben den existierenden "Clear Cache"-Buttons, da thematisch zusammengehoerend.

### 3. Eigener Endpoint `POST /api/sets/refresh`
**Rationale:** Der existierende `/statistics/refresh-sets` macht einen Redirect auf die Statistics-Seite. Fuer Show brauchen wir einen Redirect zurueck auf `/show`. Ein separater API-Endpoint ist sauberer und kann von beiden Seiten genutzt werden.
**Alternative:** Bestehenden Endpoint anpassen – abgelehnt, da Seiteneffekte auf Statistics-Flow.

### 4. Select2-Abhaengigkeit bleibt auf Compare
**Rationale:** Select2 wird eventuell fuer andere Selects auf der Compare-Seite benoetigt (z.B. User-Compare-Dropdown). Entfernung nur wenn sicher keine andere Nutzung existiert. Zu pruefen bei Implementierung.

## Risks / Trade-offs

- [Doppelter Code] CSS/JS fuer das custom Dropdown existiert dann auf zwei Seiten → Akzeptabel, da Thymeleaf-Templates eigenstaendig sind und kein gemeinsames Component-System existiert
- [Select2 Removal] Falls Select2 noch anderweitig genutzt wird, kann es nicht entfernt werden → Pruefen bei Implementierung
- [Produktions-Cache] Refresh loescht und ersetzt alle Sets → Waehrend des Refresh (wenige Sekunden) koennte die Set-Liste leer sein → Akzeptabel bei manuellem Trigger
