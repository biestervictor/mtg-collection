## ADDED Requirements

### Requirement: Multi-Set-Auswahl auf der Compare-Seite
Die Compare-Seite SHALL es erlauben, 1 bis N Sets gleichzeitig auszuwählen. Bei N=1 verhält sich die Seite identisch zur bisherigen Single-Set-Variante.

#### Scenario: Benutzer wählt mehrere Sets aus
- **WHEN** der Benutzer im Set-Dropdown drei Sets nacheinander auswählt
- **THEN** alle drei Sets werden als entfernbare Tags (Chips) im Eingabefeld angezeigt
- **AND** jedes Tag zeigt das Scryfall-Set-Icon sowie den Set-Namen
- **AND** der Submit-Button lädt die Compare-Daten für alle drei Sets

#### Scenario: Backwards-kompatible Single-Set-URL
- **WHEN** ein Benutzer eine alte URL `/compare?set=tdm&user=Victor&compareUser=Andre` aufruft
- **THEN** die Seite zeigt das Set `tdm` korrekt an
- **AND** das Set-Dropdown zeigt `tdm` als ausgewähltes Tag

#### Scenario: Multi-Set-URL mit Komma-Trennung
- **WHEN** ein Benutzer die URL `/compare?sets=tdm,dmu,fdn&user=Victor&compareUser=Andre` aufruft
- **THEN** die Seite zeigt drei collapsable Sektionen, jeweils mit der Diff für ein Set
- **AND** die Set-Tags im Dropdown sind in der Reihenfolge `tdm`, `dmu`, `fdn`

#### Scenario: Single-Set-Spezialfall — bereits expandiert
- **WHEN** der Benutzer genau ein Set auswählt
- **THEN** die einzige Set-Sektion wird initial expanded angezeigt (kein zusätzlicher Klick nötig)

### Requirement: Entfernen einzelner Sets aus der Auswahl
Die Compare-Seite SHALL es erlauben, ein ausgewähltes Set per Klick auf das X-Symbol am Tag wieder zu entfernen, ohne die anderen Sets zu verlieren.

#### Scenario: Set per Tag-Klick entfernen
- **GIVEN** drei Sets `tdm`, `dmu`, `fdn` sind ausgewählt und angezeigt
- **WHEN** der Benutzer auf das X-Symbol des `dmu`-Tags klickt
- **THEN** das Tag `dmu` verschwindet aus dem Eingabefeld
- **AND** nach Form-Submit zeigt die Seite nur noch die Sektionen für `tdm` und `fdn`
- **AND** die URL ändert sich auf `?sets=tdm,fdn`

#### Scenario: Letztes Set kann nicht entfernt werden
- **GIVEN** nur ein einziges Set ist ausgewählt
- **WHEN** der Benutzer versucht, das Tag zu entfernen
- **THEN** entweder das Tag bleibt bestehen oder der Submit-Button ist disabled mit Hinweis "Mindestens ein Set wählen"

### Requirement: Collapsable Ergebnis-Sektion pro Set
Jedes ausgewählte Set SHALL in einer eigenen Bootstrap-Accordion-Sektion mit der bekannten Diff-Darstellung erscheinen. Sektionen können unabhängig auf- und zugeklappt werden.

#### Scenario: Mehrere Sektionen können gleichzeitig offen sein
- **GIVEN** drei Sets sind ausgewählt und alle collapsed
- **WHEN** der Benutzer den Header von `tdm` UND danach den Header von `dmu` klickt
- **THEN** beide Sektionen sind expanded
- **AND** die `fdn`-Sektion bleibt collapsed

#### Scenario: Sektion-Header zeigt Zusammenfassung
- **WHEN** eine Set-Sektion gerendert wird
- **THEN** der Accordion-Header zeigt: Set-Icon, Set-Name, Set-Code und zwei Badges für die Anzahl `onlyUser` und `onlyCompare`-Karten

#### Scenario: Default-Zustand bei mehreren Sets — alle collapsed
- **WHEN** mehr als ein Set ausgewählt ist
- **THEN** alle Set-Sektionen sind initial collapsed
- **AND** der Benutzer muss aktiv expandieren, um Karten zu sehen

### Requirement: Set-spezifische Filter & Toggles bleiben funktional
Bestehende Filter (Tradable, Treatment-Divider, Token/Promo-Toggles, View-Mode-Switch) SHALL pro Set unabhängig wirken oder global gelten (je nach bestehender Semantik).

#### Scenario: Tradable-Filter wirkt pro Set
- **GIVEN** zwei Sets sind ausgewählt und der Tradable-Filter ist aktiv
- **WHEN** die Compare-Daten geladen werden
- **THEN** beide Set-Sektionen zeigen nur Karten mit ≥2 Stück beim Owner

#### Scenario: Token-Toggle gilt für alle Sets
- **GIVEN** zwei Hauptsets `tdm` und `dmu` sind ausgewählt und Token-Toggle ist aktiv
- **WHEN** die Seite geladen wird
- **THEN** unterhalb jeder Hauptset-Sektion erscheint ggf. eine Token-Sub-Sektion (z.B. `ttdm`, `tdmu`)
- **AND** wenn ein Token-Set nicht existiert, wird keine leere Sub-Sektion gerendert

### Requirement: URL-Parameter-Normalisierung
Der Controller SHALL beide URL-Parameter-Formen `?set=X` (alt) und `?sets=X,Y,Z` (neu) akzeptieren und intern auf eine einheitliche Liste normalisieren.

#### Scenario: Beide Parameter gleichzeitig — sets gewinnt
- **WHEN** eine URL `?set=tdm&sets=dmu,fdn` aufgerufen wird
- **THEN** die Compare-Seite zeigt `dmu` und `fdn` (der `sets`-Parameter überschreibt `set`)

#### Scenario: Leerer sets-Parameter
- **WHEN** `?sets=` (leer) aufgerufen wird
- **THEN** die Seite zeigt das gewohnte leere Set-Dropdown ohne Ergebnisse, wie beim ersten Page-Load
