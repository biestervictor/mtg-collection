## Context

Die Compare-Seite (`/compare` in `CollectionController.compareCollection()`) zeigt aktuell die Differenz zwischen zwei User-Sammlungen für **genau ein** Set. Pro Aufruf wird `set` als `@RequestParam String` gelesen, `ScryfallService.getCardsBySet(set)` lädt das Set, dann wird via `computeDiff()` (kürzlich extrahierter Helper) die Diff berechnet. Token- und Promo-Sets werden optional als Extra-Sektionen geladen.

Der bestehende Refactor (`computeDiff()` + `CompareDiff` Record) macht es einfach, die Pipeline mehrfach pro Set laufen zu lassen. Die kürzlich eingebauten Treatment-Divider und der Tradable-Filter (≥2 Stück) bleiben pro Set erhalten.

**Stakeholders:** Victor und André (die einzigen App-User). Beide nutzen die Seite auf einem Browser (Desktop + Mobile). Backend läuft auf MicroK8s ARM64 (Raspberry Pi 4, 1.5 GHz quad-core).

**Preisquelle:** `ScryfallCard.prices.eur` und `prices.eur_foil` sind bereits im DB-Modell vorhanden und werden auf der Statistics-Seite verwendet. Felder können `null` sein, dann zählt die Karte nicht für den Wizard.

**Constraint:** CSRF ist nur für `/api/**` deaktiviert. Da der Wizard-Endpoint via fetch() von JS aufgerufen wird, muss er unter `/api/compare/trade-wizard` liegen.

## Goals / Non-Goals

**Goals:**
- Compare-Seite akzeptiert 1..N Sets gleichzeitig, ist bei N=1 backwards-kompatibel (alte URLs `/compare?set=tdm` funktionieren weiter)
- Multi-Set-Chips UI: jede Auswahl wird als entfernbarer Tag dargestellt
- Pro Set eine collapsable Bootstrap-Accordion-Sektion mit unveränderter Diff-Darstellung
- Trade Wizard berechnet pool-übergreifend (alle ausgewählten Sets zusammen) faire Tausch-Vorschläge
- Zwei Algorithmen: Greedy Pair (1:1) als Default, Karmarkar-Karp (n:m Bundle) als Toggle
- Wizard-Endpoint antwortet < 200ms auf RPi 4 für realistische Pool-Größen (≤200 Karten je Seite)
- Alle bestehenden Compare-Features (Tradable-Filter, Treatment-Divider, Token/Promo-Toggles, View-Mode) bleiben pro Set funktional

**Non-Goals:**
- Kein Trade-Partner-Matching mit fremden Usern (nur Victor ↔ André)
- Keine Trade-History/Persistierung (Wizard-Ergebnis ist stateless, nicht gespeichert)
- Kein optimaler Exact-Solver (NP-hart, würde Sekunden auf RPi brauchen — Karmarkar-Karp reicht)
- Keine Drag&Drop-UI zum manuellen Editieren des Trades (Vorschlag ist read-only, User passt extern an)
- Keine Multi-User-Trades (nur 2 Sides)
- Keine Trade-Wizard auf der Show- oder Statistics-Seite (nur Compare)

## Decisions

### D1: URL-Parameter `sets` (Komma-separiert) statt mehrfacher `set`-Parameter
- **Decision:** Neuer Param `?sets=tdm,dmu,fdn`. Single-Set-URL `?set=tdm` bleibt unterstützt als Fallback (wenn `sets` fehlt, wird `set` gelesen). Controller-Logik normalisiert beide auf `List<String> sets`.
- **Why over alternatives:**
  - Mehrfache `?set=tdm&set=dmu` (Spring kann das nativ via `List<String>`) wäre URL-länger und weniger bookmark-freundlich
  - JSON-Body kommt nicht in Frage, da Compare-Seite via GET-Form aufgerufen wird
- **Trade-off:** Kommata in Set-Codes wären problematisch, aber Scryfall-Codes sind alphanumerisch (3–5 Zeichen)

### D2: Tom Select für Multi-Select-Chips
- **Decision:** Bestehende Set-Dropdown-Komponente ist Custom-HTML mit Set-Icons (aus `compare-set-icons-refresh`). Diese wird auf [Tom Select](https://tom-select.js.org/) v2 (vendored, MIT) umgestellt — unterstützt nativ Multi-Tags, Custom-HTML-Options (Set-Icons), Server-Search.
- **Why over alternatives:**
  - Select2 (jQuery-basiert) wurde bewusst aus dem Projekt entfernt (`compare-set-icons-refresh`)
  - Eigene Implementierung wäre 300+ Zeilen JS (Add/Remove-Logic, ARIA, Keyboard)
  - Choices.js wäre Alternative, aber Tom Select hat besseren Plugin-Support für Custom-Render
- **Trade-off:** Eine neue JS/CSS-Vendored-Datei (~30 KB gzipped) — akzeptabel für gewonnene Funktionalität

### D3: Pro Set eine eigene `CompareDiff` + Bootstrap-Accordion
- **Decision:** Controller baut `Map<String, CompareDiff> setResults = LinkedHashMap` (Reihenfolge = User-Auswahl-Reihenfolge). Im Template `th:each` über `setResults`, jeder Eintrag in `<div class="accordion-item">` mit Header (Set-Icon + Name + Anzahl-Badges) und Body (bestehende Diff-Darstellung).
- **Why over alternatives:**
  - Tabs wären Alternative, aber bei 5+ Sets unübersichtlich; Accordion erlaubt mehrere offene Sets gleichzeitig
  - Eigene Collapse-Implementierung wäre redundant — Bootstrap 5 Accordion ist bereits im Projekt
- **Default open/closed:** Alle Sektionen initial **collapsed** (außer wenn nur 1 Set ausgewählt → expanded), spart Scroll-Aufwand bei vielen Sets

### D4: Trade-Algorithmus — Greedy Default + Karmarkar-Karp Toggle
- **Decision:** Zwei Modi im Wizard-UI: "Pair Matching" (default) und "Bundle Matching".
  - **Greedy Pair Matching:** Sortiere beide Pools nach Preis ↓. Iteriere: nimm teuerste Karte aus Pool A, suche in Pool B die Karte mit kleinstem `|priceB - priceA|` innerhalb Toleranz (±15% von priceA). Wenn gefunden → Pair, beide entfernen. Wenn nicht → Karte A skip. Komplexität O(n²) im Worst Case, in Praxis O(n log n) bei sortierten Pools.
  - **Karmarkar-Karp (KK):** Differencing-Heuristik für Number Partitioning. Wird auf Differenzpool `A_values ∪ -B_values` angewandt, findet zwei Subset-Indizes mit minimaler Summendifferenz. Standardimplementierung mit Max-Heap, O(n log n).
- **Why over alternatives:**
  - Exhaustive Subset-Sum DP (O(n × maxValue)) wäre auf RPi 4 mit Karten bis €5000 (in Cent → 500k) und 200 Karten = 100 Mio Ops → 5–15 s, unzumutbar
  - Reines Greedy ohne Bundle-Option würde n:m-Trades verhindern (z.B. 1 teure Karte für 3 mittlere)
  - Genetic Algorithm wäre Overkill und nicht-deterministisch

### D5: Tradable-Definition für Wizard-Pool
- **Decision:** Karte qualifiziert sich für Pool A (User bietet) wenn:
  1. User hat **≥2 Stück** (entweder normal+normal, normal+foil, oder foil+foil)
  2. Compare-User hat **=0 Stück** (weder normal noch foil)
  3. `prices.eur` (für Normal) bzw. `prices.eur_foil` (für Foil) ist `≥ minValue` (default €0.50)
- **Why:** Konsistent mit bestehendem Tradable-Filter (≥2). Foil und Normal werden als separate "logische Karten" behandelt — User kann eine normale Karte tauschen und das Foil behalten.
- **Trade-off:** Sehr seltene Karten (Wert <€0.50) werden ignoriert, um Mini-Pairs zu vermeiden. Schwellenwert konfigurierbar.

### D6: Endpoint `POST /api/compare/trade-wizard` (JSON)
- **Decision:** Wizard wird **nicht** beim Page-Load berechnet, sondern bei Klick auf "Trade berechnen". Frontend sendet:
  ```json
  {"userA": "Victor", "userB": "Andre", "sets": ["tdm", "dmu"],
   "mode": "greedy", "tolerancePercent": 15, "minCardValue": 0.5}
  ```
  Backend antwortet:
  ```json
  {"pairs": [...], "totalA": 23.45, "totalB": 24.10, "diff": -0.65,
   "fairnessScore": 0.97, "skippedA": [...], "skippedB": [...]}
  ```
- **Why:** Page-Load würde Wizard immer ausführen (auch wenn nicht gewünscht). POST/JSON erlaubt schnelles Re-Run bei Toleranz-Änderung ohne Page-Reload.
- **CSRF:** `/api/**` ist CSRF-exempt → fetch() ohne Token möglich (Pattern bereits etabliert für `/api/user/map`)

### D7: Sortierung & Tie-Breaker im Greedy
- **Decision:** Sortiere primär nach Preis ↓, sekundär nach `card.name` ↑ (für Determinismus bei gleichen Preisen).
- **Why:** Reproduzierbare Ergebnisse, einfacher zu testen.

## Risks / Trade-offs

- **[Risk]** Tom Select Vendoring erhöht Bundle-Size → **Mitigation:** Lazy-load via `<script defer>` und nur auf `/compare`-Seite einbinden
- **[Risk]** Karmarkar-Karp ist Heuristik, kein Optimum — kann Bundles vorschlagen, die suboptimal sind → **Mitigation:** UI zeigt explizit "Fairness Score" (1.0 = perfekt, <0.9 = unfair); User sieht Unfairness sofort
- **[Risk]** Bei sehr großem Pool (>500 Karten je Seite) könnte KK-Heuristik schlechte Approximation liefern → **Mitigation:** Pool-Größe in DB-Realität ist 50-150 Karten; falls >300 → Warnung im UI + Pool auf Top-300 nach Wert beschränken
- **[Risk]** `prices.eur` ist `null` für viele neue Sets → **Mitigation:** Skipped-Liste im Response zeigt explizit ausgelassene Karten mit Grund; UI zeigt sie als "no price" am Ende der Sektion
- **[Risk]** Backwards-Compat: User-Bookmarks mit `?set=tdm` müssen weiterhin funktionieren → **Mitigation:** Controller liest BEIDE `sets` und `set`, normalisiert zu `List<String>`. Tests decken beide Pfade ab.
- **[Trade-off]** Wizard ignoriert Foil/Normal-Mix (eine "Karte" ist entweder Normal oder Foil im Pool) → User sieht ggf. nicht, dass eine Foil-Variante besser passen würde. Bewusst akzeptiert für Algorithmus-Einfachheit.
- **[Trade-off]** Set-Multiselect macht URL länger (`?sets=tdm,dmu,fdn,blb,otj`) → kein Problem für Browser (HTTP Limit ~8KB), aber Mobile-User müssen häufiger Sets neu setzen

## Migration Plan

1. **Backend zuerst** deployen: `compareCollection()` akzeptiert beide Param-Formen, alter Pfad bleibt → Old-UI funktioniert auf neuem Backend
2. **Frontend danach** mit Tom Select + Wizard-Sektion deployen
3. **Rollback-Strategie:** Bei kritischem Bug → Helm-Chart auf vorige Image-Tag (v0.1.95) zurückrollen via ArgoCD (Standard-Workflow)
4. Migration-Script: nicht nötig (kein DB-Schema-Change)
