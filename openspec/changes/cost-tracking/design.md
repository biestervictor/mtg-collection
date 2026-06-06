## Context

Das Projekt nutzt OpenCode als AI-Coding-Agent. Jede Session verursacht API-Kosten (Token-Verbrauch), die in der OpenCode-UI als "Spend" angezeigt werden. Aktuell gibt es keine persistente Aufzeichnung dieser Kosten. Die Information geht nach Session-Ende verloren.

## Goals / Non-Goals

**Goals:**
- Transparente Kostenerfassung pro Session und Task
- Einfaches manuelles Nachschlagen, welcher Change wie viel gekostet hat
- Agent-seitige Pflicht, die Datei bei jeder Aenderung zu aktualisieren

**Non-Goals:**
- Automatische Kostenerfassung (nicht moeglich, da Spend nur in der UI sichtbar ist)
- Budgetierung oder Alerts
- Integration in CI/CD oder externe Systeme

## Decisions

### 1. Format: Markdown-Tabelle in `costs.md`
**Rationale:** Einfach lesbar, versioniert mit Git, keine zusaetzlichen Tools noetig.
**Alternative:** JSON/CSV - abgelehnt, da weniger lesbar im Repo-Browser.

### 2. Gruppierung nach Session-ID
**Rationale:** Eine Session kann mehrere Changes enthalten. Die Session-ID (z.B. `16e063`) gruppiert zusammengehoerige Eintraege.
**Format:** Markdown H2-Header pro Session.

### 3. Delta-basierte Erfassung
**Rationale:** Der Agent notiert die Differenz seit dem letzten Eintrag (abgelesen aus der Spend-Anzeige). So entsteht kein kumulativer Fehler.

### 4. Pflicht-Anweisung in AGENTS.md
**Rationale:** AGENTS.md wird vom Agent bei jeder Session geladen. Eine Anweisung dort stellt sicher, dass die Kostenerfassung nicht vergessen wird.

## Risks / Trade-offs

- [Ungenauigkeit] Spend-Anzeige ist gerundet, Deltas koennen minimal abweichen → Akzeptabel fuer Ueberblickszwecke
- [Vergessen] Agent koennte Anweisung ignorieren → Explizite Pflicht in AGENTS.md minimiert das Risiko
- [Dateigroesse] costs.md waechst unbegrenzt → Bei Bedarf spaeter archivieren (nicht Teil dieses Changes)
