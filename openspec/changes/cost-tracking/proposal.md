## Why

Aktuell gibt es keine Nachvollziehbarkeit, wie viel eine OpenCode-Session oder ein einzelner Change an API-Kosten verursacht. Eine zentrale `costs.md` im Repo soll pro Session und Task die Kosten-Deltas festhalten, damit Ausgaben transparent bleiben und Budgets planbar werden.

## What Changes

- Neue Datei `costs.md` im Projekt-Root, die als Kosten-Logbuch dient
- Tabellarisches Format mit Spalten: #, Change/Task, Summary, Delta, Session gesamt, Modell, Datum
- Gruppierung nach Session-ID (z.B. `16e063`)
- Anweisung in `AGENTS.md`, dass bei jeder Aenderung die costs.md aktualisiert werden muss (Delta seit letztem Eintrag anhand der Spend-Anzeige)

## Capabilities

### New Capabilities
- `cost-tracking`: Zentrales Kosten-Logbuch (costs.md) mit Session-basierter Erfassung der API-Spend-Deltas

### Modified Capabilities
<!-- Keine bestehenden Specs vorhanden -->

## Impact

- Neues File: `costs.md` (Projekt-Root)
- Aenderung: `AGENTS.md` erhaelt zusaetzlichen Abschnitt mit der Pflicht zur Kostenerfassung
- Kein Einfluss auf Code, APIs oder Deployments - rein dokumentarische Aenderung
