## Why

Die Compare-Seite zeigt aktuell nur ein Set gleichzeitig und stellt die Diff statisch dar. User, die ein Tausch-Geschäft planen, müssen mehrere Sets nacheinander aufrufen, Karten manuell abgleichen und die Preise im Kopf summieren. Ein Trade Wizard, der über mehrere Sets hinweg arbeitet und automatisch faire Tausch-Vorschläge auf Preisbasis berechnet, reduziert diesen manuellen Aufwand erheblich.

## What Changes

- **Multiselect für Set-Auswahl** auf der Compare-Seite — ersetzt das bisherige Single-Set-Dropdown
- Ausgewählte Sets werden als entfernbare Tags (Chips) angezeigt und können einzeln per Klick entfernt werden
- **Pro Set eine eigene collapsable Ergebnis-Sektion** mit der bekannten Diff-Darstellung (onlyUser/onlyCompare, Treatment-Divider, Token/Promo)
- Bei Auswahl von genau einem Set verhält sich die Seite wie bisher (Backwards-Kompatibilität)
- **Neuer "Trade Wizard"-Button** öffnet eine zusätzliche Sektion über allen Set-Sektionen, die einen automatisch berechneten Trade vorschlägt
- **Trade-Algorithmus**: Default Greedy Pair-Matching (1:1 Karten ähnlichen Preises mit Toleranz), Toggle "Bundle-Modus" aktiviert Karmarkar-Karp Heuristik (n:m Karten so dass beide Summen ähnlich sind)
- **Preisquelle**: Scryfall EUR (`prices.eur` für Normal, `prices.eur_foil` für Foil)
- Trade-Pool berücksichtigt nur **tradeable** Karten (User ≥2, Compare-User =0 — und umgekehrt) über **alle ausgewählten Sets** hinweg
- Wizard-Ergebnis zeigt: User-bietet-Liste, User-bekommt-Liste, Summen beider Seiten, Differenz, Fairness-Indikator
- Einstellbare Toleranz (default ±15%) und Mindestkartenwert (default €0.50)

## Capabilities

### New Capabilities
- `compare-multi-set`: Mehrere Sets gleichzeitig auf der Compare-Seite auswählen, als Tags anzeigen, einzeln entfernen, pro Set collapsable Ergebnis-Sektion
- `trade-wizard`: Automatischer Trade-Vorschlag auf Preisbasis (Greedy Pair + Bundle-Heuristik), pool-übergreifend über alle ausgewählten Sets, Fairness-Bewertung

### Modified Capabilities
<!-- keine - openspec/specs/ ist leer, alle Capabilities sind neu -->

## Impact

**Affected code:**
- `CollectionController.java` — `compareCollection()` muss `List<String> sets` statt `String set` akzeptieren, pro Set eine Sub-Result-Map zurückgeben; neuer Endpoint `POST /compare/trade-wizard` (JSON) für Algorithmus-Aufrufe
- `compare.html` — Multi-Select-Component (Chips), Loop über Sets mit collapsable Bootstrap-Accordions, neue Wizard-Sektion mit JS-Interaktion
- Neue Service-Klasse `TradeWizardService` mit Greedy- und Karmarkar-Karp-Implementierung
- Neue DTO/Records: `TradeOffer`, `TradeSide`, `TradeResult`

**Affected URLs:**
- `GET /compare?set=tdm&...` → muss kompatibel bleiben (Single-Set als Spezialfall von Multi-Set)
- `GET /compare?sets=tdm,dmu,...` — neue Multi-Set-Form
- `POST /compare/trade-wizard` — neu

**Dependencies:**
- Keine neuen Maven-Dependencies (Algorithmen rein in Java implementierbar)
- Bestehende `ScryfallCard.prices` (EUR-Felder) werden vorausgesetzt

**Tests:**
- Neue Unit-Tests für `TradeWizardService` (Greedy + Karmarkar-Karp + Edge-Cases)
- Controller-Tests für `compareCollection()` mit Multi-Set und für neuen Wizard-Endpoint

**Performance:**
- Greedy < 10ms, Karmarkar-Karp < 100ms auf RPi 4 ARM64 für ≤200 Karten je Seite
- Kein DB-Lookup-Mehraufwand (Karten bereits geladen für Diff-Berechnung)
