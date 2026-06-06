## ADDED Requirements

### Requirement: costs.md existiert im Projekt-Root
Das Projekt MUSS eine Datei `costs.md` im Root-Verzeichnis enthalten, die als Kosten-Logbuch dient.

#### Scenario: Initiale Datei vorhanden
- **WHEN** das Repository geklont wird
- **THEN** existiert eine Datei `costs.md` im Projekt-Root mit der definierten Tabellenstruktur

### Requirement: Tabellenformat pro Session
Jede Session MUSS als eigener Abschnitt mit H2-Header dokumentiert werden. Das Format MUSS sein:
```
## Session: <session-id> | Kosten = <delta-seit-letztem-eintrag>
| # | Change / Task | Summary | Delta | Session gesamt | Modell | Datum |
```

#### Scenario: Neuer Session-Eintrag
- **WHEN** eine neue Session beginnt und Arbeit geleistet wird
- **THEN** wird ein neuer H2-Abschnitt mit der Session-ID angelegt
- **THEN** enthaelt die Tabelle mindestens eine Zeile mit den Pflichtfeldern

#### Scenario: Mehrere Tasks in einer Session
- **WHEN** in einer Session mehrere Changes/Tasks bearbeitet werden
- **THEN** erhaelt jeder Task eine eigene Tabellenzeile unter dem gleichen Session-Header
- **THEN** ist die laufende Nummer (#) innerhalb der Session fortlaufend

### Requirement: Delta-Erfassung
Der Agent MUSS bei jedem abgeschlossenen Task das Kosten-Delta seit dem letzten Eintrag erfassen. Der Wert wird aus der Spend-Anzeige der OpenCode-UI abgelesen.

#### Scenario: Delta korrekt berechnet
- **WHEN** der Agent einen Task abschliesst
- **THEN** traegt er die Differenz zwischen aktuellem Spend und letztem dokumentierten Spend als Delta ein

### Requirement: AGENTS.md Pflichtanweisung
Die Datei `AGENTS.md` MUSS einen Abschnitt enthalten, der den Agent verpflichtet, bei jeder Aenderung die `costs.md` zu aktualisieren.

#### Scenario: Agent liest Pflicht
- **WHEN** eine neue Session startet und der Agent AGENTS.md laedt
- **THEN** ist die Anweisung zur Kostenerfassung sichtbar und verbindlich

### Requirement: Pflichtfelder pro Eintrag
Jede Tabellenzeile MUSS folgende Felder enthalten:
- `#`: Laufende Nummer innerhalb der Session
- `Change / Task`: Name des Changes oder der Aufgabe
- `Summary`: Kurzbeschreibung der Arbeit
- `Delta`: Kosten-Delta seit letztem Eintrag (z.B. `$0.12`)
- `Session gesamt`: Kumulierte Kosten der Session
- `Modell`: Verwendetes AI-Modell (z.B. `claude-opus-4.6`)
- `Datum`: Datum im Format `YYYY-MM-DD`

#### Scenario: Vollstaendiger Eintrag
- **WHEN** der Agent einen neuen Eintrag in costs.md schreibt
- **THEN** sind alle 7 Pflichtfelder ausgefuellt
