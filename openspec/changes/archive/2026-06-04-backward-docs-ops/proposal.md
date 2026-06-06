# Proposal: Backward Documentation — Operative Ergänzung

## Why

Der initiale `backward-docs`-Change hat die Anwendungsarchitektur vollständig dokumentiert, aber die operativen Inhalte aus `agents.md` ausgelassen:

- Den verbindlichen Entwicklungs-Workflow (Pflichtschritte, CI-Eigenheiten, Merge-Prozedur)
- Die externe Toolchain auf dem Raspberry Pi (Java, Maven auf USB-Stick)
- Projektpfade und Startup-Kommandos
- Das Referenz-Projekt `suppcheck`

Diese Informationen sind für jeden Entwicklungs-Agenten (KI oder Mensch) kritisch: ohne sie werden CI-Runs gebrochen, Tests übersprungen oder der `dev→main`-Merge falsch durchgeführt.

## What Changes

Keine Code-Änderungen. Zwei neue Spec-Dateien werden ergänzt.

## Capabilities

### New Capabilities

- `development-workflow`: Verbindliche Pflichtschritte, CI/CD-Details, Merge-Prozedur, Test-Policy
- `operational-reference`: Externe Tools (USB-Stick), Projektpfade, Startup-Kommandos, Kubernetes-Zugriff, Referenz-Projekte

## Impact

- `openspec/changes/backward-docs-ops/specs/development-workflow.md`: neu
- `openspec/changes/backward-docs-ops/specs/operational-reference.md`: neu
