## ADDED Requirements

### Requirement: Trade Wizard berechnet faire Tausch-Vorschläge
Der Trade Wizard SHALL auf Knopfdruck einen Tausch-Vorschlag zwischen zwei Usern berechnen, basierend auf Scryfall-EUR-Preisen, über alle aktuell ausgewählten Sets hinweg.

#### Scenario: Wizard-Button öffnet Wizard-Sektion
- **GIVEN** mindestens ein Set ist auf der Compare-Seite ausgewählt und beide User sind gewählt
- **WHEN** der Benutzer auf "Trade berechnen" klickt
- **THEN** der Wizard ruft `POST /api/compare/trade-wizard` mit den aktuellen Sets, beiden Usern, Mode und Toleranz auf
- **AND** das Ergebnis wird in einer ausklappbaren Wizard-Sektion oberhalb der Set-Sektionen angezeigt

#### Scenario: Wizard ohne ausgewählte Sets
- **GIVEN** keine Sets sind ausgewählt
- **WHEN** der Benutzer den Wizard-Button sieht
- **THEN** der Button ist disabled mit Tooltip "Mindestens ein Set wählen"

### Requirement: Pool-Bildung — nur tradeable Karten mit gültigem Preis
Der Wizard-Pool SHALL nur Karten enthalten, die der jeweilige User mindestens 2× besitzt UND der andere User =0× besitzt UND deren Scryfall-EUR-Preis ≥ konfigurierbarem Mindestwert ist (default €0.50).

#### Scenario: Karte ohne EUR-Preis wird ignoriert
- **GIVEN** eine Karte hat `prices.eur = null`
- **WHEN** der Wizard den Pool aufbaut
- **THEN** diese Karte wird nicht in den Pool aufgenommen
- **AND** sie erscheint in der `skipped`-Liste der Response mit Grund "no_price"

#### Scenario: Karte unterhalb Mindestwert wird ignoriert
- **GIVEN** Mindestwert ist €0.50 und eine Karte hat `prices.eur = 0.20`
- **WHEN** der Wizard den Pool aufbaut
- **THEN** diese Karte wird nicht in den Pool aufgenommen
- **AND** sie erscheint in der `skipped`-Liste mit Grund "below_min_value"

#### Scenario: Foil und Normal als separate Pool-Einträge
- **GIVEN** User Victor hat 2× normale `Lightning Bolt` (€0.50) und 1× Foil `Lightning Bolt` (€8.00), André hat 0× beide Varianten
- **WHEN** der Wizard den Pool aufbaut
- **THEN** zwei separate Pool-Einträge werden für Victor erstellt: 1× Normal (€0.50) und 1× Foil (€8.00)

#### Scenario: Multi-Set Pool-Aggregation
- **GIVEN** Sets `tdm` und `dmu` sind ausgewählt
- **WHEN** der Wizard den Pool aufbaut
- **THEN** der Pool enthält tradeable Karten aus beiden Sets zusammen
- **AND** jede Karte ist mit ihrem Set-Code attribuiert für Anzeige im Ergebnis

### Requirement: Greedy Pair Matching als Standard-Algorithmus
Im Standard-Modus SHALL der Wizard ein Greedy-Pair-Matching durchführen: Sortiere beide Pools nach Preis absteigend, paare iterativ die teuerste verfügbare Karte aus Pool A mit der preislich nächstgelegenen aus Pool B innerhalb der Toleranz.

#### Scenario: Einfacher 1:1-Pair-Trade
- **GIVEN** Pool A enthält eine Karte zu €10, Pool B enthält Karten zu €9 und €15, Toleranz ist ±15%
- **WHEN** Greedy-Matching läuft
- **THEN** das Pair (€10 A, €9 B) wird vorgeschlagen (|10-9|/10 = 10% ≤ 15%)
- **AND** die €15-Karte aus Pool B bleibt im skipped-Pool zurück (|10-15|/10 = 50% > 15%)

#### Scenario: Keine Karte in Toleranz — überspringen
- **GIVEN** Pool A: €10-Karte; Pool B: €5-Karte und €50-Karte; Toleranz ±15%
- **WHEN** Greedy-Matching läuft
- **THEN** keine Pairs werden vorgeschlagen
- **AND** beide Pools komplett in `skipped` enthalten

#### Scenario: Deterministisches Ergebnis bei gleichen Preisen
- **GIVEN** Pool A: 1× `Black Lotus` €5000; Pool B: 1× `Mox Ruby` €5000 und 1× `Mox Sapphire` €5000
- **WHEN** Greedy-Matching läuft
- **THEN** wird die alphabetisch erste Karte aus Pool B gewählt (z.B. `Mox Ruby`)
- **AND** das gleiche Ergebnis kommt bei wiederholtem Aufruf zustande

### Requirement: Bundle-Matching via Karmarkar-Karp-Heuristik
Der Wizard SHALL einen optionalen Bundle-Modus anbieten, der die Karmarkar-Karp-Heuristik anwendet, um n:m-Trades vorzuschlagen, bei denen mehrere Karten einer Seite gegen mehrere Karten der anderen Seite getauscht werden.

#### Scenario: n:m-Trade gefunden
- **GIVEN** Pool A: 1× €10-Karte; Pool B: 1× €4-Karte, 1× €3-Karte, 1× €3-Karte
- **WHEN** Bundle-Matching läuft
- **THEN** wird ein Trade mit `A=[€10]` gegen `B=[€4, €3, €3]` (Σ €10) vorgeschlagen
- **AND** Fairness Score ist 1.0 (Summen identisch)

#### Scenario: Bundle-Toggle wechselt Modus
- **GIVEN** Wizard-Ergebnis ist bereits angezeigt im Greedy-Modus
- **WHEN** der Benutzer den Toggle "Bundle-Modus" aktiviert und auf "Neu berechnen" klickt
- **THEN** das Backend liefert ein neues Ergebnis mit Karmarkar-Karp-Algorithmus
- **AND** die Wizard-Sektion zeigt das aktualisierte Ergebnis ohne Page-Reload

### Requirement: Fairness-Bewertung des Trade-Vorschlags
Der Wizard SHALL bei jedem Vorschlag einen Fairness-Score zwischen 0 und 1 berechnen und visuell darstellen, basierend auf der Summen-Differenz beider Seiten.

#### Scenario: Perfekt fairer Trade
- **GIVEN** Trade-Summe A = €100, Trade-Summe B = €100
- **WHEN** Fairness berechnet wird
- **THEN** Score = 1.0
- **AND** UI zeigt grünen Badge "Fair"

#### Scenario: Leicht unfairer Trade
- **GIVEN** Trade-Summe A = €100, Trade-Summe B = €95
- **WHEN** Fairness berechnet wird
- **THEN** Score = 0.95 (= 1 - |100-95|/100)
- **AND** UI zeigt gelben Badge "Akzeptabel"

#### Scenario: Stark unfairer Trade
- **GIVEN** Trade-Summe A = €100, Trade-Summe B = €70
- **WHEN** Fairness berechnet wird
- **THEN** Score = 0.70
- **AND** UI zeigt roten Badge "Unfair" mit Hinweis auf Differenz

### Requirement: Konfigurierbare Toleranz und Mindestwert
Der Wizard SHALL es dem Benutzer erlauben, Toleranz (Prozent) und Mindestkartenwert (Euro) im UI einzustellen, ohne Page-Reload.

#### Scenario: Toleranz-Slider ändert Ergebnis
- **GIVEN** Wizard-Ergebnis ist mit Toleranz 15% berechnet
- **WHEN** der Benutzer Toleranz auf 30% setzt und "Neu berechnen" klickt
- **THEN** mehr Pairs werden vorgeschlagen (toleranter Match)
- **AND** der Algorithmus bleibt der gleiche

#### Scenario: Mindestwert-Input filtert Mini-Karten
- **GIVEN** Mindestwert ist auf €0.50 gesetzt; Pool enthält Karten ab €0.10
- **WHEN** der Wizard läuft
- **THEN** alle Karten <€0.50 werden ignoriert
- **AND** in `skipped` aufgeführt mit Grund "below_min_value"

### Requirement: Wizard-Endpoint unter /api/compare/trade-wizard
Der Endpoint SHALL unter `POST /api/compare/trade-wizard` erreichbar sein, JSON-Body akzeptieren und JSON-Response liefern. CSRF ist (wie für alle `/api/**`) deaktiviert.

#### Scenario: Erfolgreicher Wizard-Aufruf
- **WHEN** ein JSON-Body `{"userA":"Victor","userB":"Andre","sets":["tdm"],"mode":"greedy","tolerancePercent":15,"minCardValue":0.5}` an `POST /api/compare/trade-wizard` gesendet wird
- **THEN** die Response ist `200 OK` mit JSON-Struktur `{pairs, totalA, totalB, diff, fairnessScore, skippedA, skippedB}`

#### Scenario: Ungültiger Mode
- **WHEN** der `mode`-Parameter weder `"greedy"` noch `"bundle"` ist
- **THEN** die Response ist `400 Bad Request` mit Fehlermeldung "Invalid mode"

#### Scenario: Leere Set-Liste
- **WHEN** der `sets`-Array leer ist
- **THEN** die Response ist `400 Bad Request` mit Fehlermeldung "At least one set required"

### Requirement: Performance-Garantien
Der Wizard SHALL bei realistischer Pool-Größe (bis zu 200 Karten je Seite) ein Ergebnis innerhalb von 200ms auf der Production-Hardware (Raspberry Pi 4 ARM64) liefern.

#### Scenario: Greedy bei 200 Karten je Seite
- **GIVEN** Pool A und Pool B haben je 200 Karten
- **WHEN** Greedy-Matching läuft
- **THEN** die Berechnung dauert < 50ms

#### Scenario: Karmarkar-Karp bei 200 Karten je Seite
- **GIVEN** Pool A und Pool B haben je 200 Karten
- **WHEN** Karmarkar-Karp läuft
- **THEN** die Berechnung dauert < 200ms

#### Scenario: Pool-Größenbeschränkung
- **GIVEN** Pool A hat 500 Karten
- **WHEN** der Wizard ausgeführt wird
- **THEN** der Pool wird auf die Top-300 nach Wert reduziert
- **AND** die Response enthält einen Hinweis "pool_truncated_to_300"
