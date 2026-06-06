# Spec: Development Workflow

## EXISTING Requirements

### Requirement: Pflichtschritte bei jeder Änderung

Jede Code-Änderung muss diese Schritte in dieser Reihenfolge durchlaufen. Kein Schritt darf übersprungen werden.

#### Scenario: Vollständiger Change-Zyklus

- **WHEN** eine Änderung implementiert ist
- **THEN** müssen lokal alle Tests grün sein (`mvn test`) bevor gepusht wird
- **AND** der Push geht auf den Branch `dev` (niemals direkt auf `main`)
- **AND** der GitHub Actions CI/CD-Build wird automatisch ausgelöst
- **AND** nach erfolgreichem Build deployed ArgoCD das neue Image auf die Dev-Stage (`https://mtg-dev.biester.vip`)
- **AND** die Dev-Stage wird manuell getestet
- **AND** erst nach erfolgreichem Dev-Test wird `dev` in `main` gemergt
- **AND** ArgoCD erkennt den Merge auf `main` und aktualisiert das Produktions-Deployment (`https://mtg-kubitos.biester.vip`) automatisch

#### Scenario: Fehler im CI-Build oder auf Dev-Stage

- **WHEN** der CI-Build fehlschlägt oder ein Fehler auf der Dev-Stage auftritt
- **THEN** werden Logs und Exceptions analysiert
- **AND** ein Fix wird implementiert
- **AND** die Schleife beginnt erneut ab Schritt 1 (lokal testen)

---

### Requirement: CI-Workflow läuft nur auf `dev`

Dies ist eine kritische Eigenheit des Workflows, die beim Merge beachtet werden muss.

#### Scenario: CI-Workflow Scope

- **WHEN** ein Commit auf `dev` gepusht wird
- **THEN** läuft `.github/workflows/maven.yml` und:
  - baut das Docker-Image
  - bumpt die Version in `pom.xml`
  - trägt den neuen Image-Tag in `helmcharts/values.yaml` ein (als separater CI-Commit)
- **WHEN** ein Commit direkt auf `main` gepusht wird
- **THEN** läuft der CI-Workflow **nicht**

#### Scenario: Korrekter `dev → main` Merge

- **WHEN** `dev` in `main` gemergt werden soll
- **THEN** muss zuerst `git pull origin dev` ausgeführt werden (um den CI-Commit mit dem aktuellen Image-Tag zu holen)
- **AND** danach wird `main` mit `origin/dev` zusammengeführt
- **AND** dadurch enthält `helmcharts/values.yaml` auf `main` den korrekten Image-Tag
- **AND** ArgoCD kann das richtige Image auf Produktion deployen

---

### Requirement: Test-Policy

#### Scenario: Tests bei Feature-Änderungen

- **WHEN** eine neue Funktionalität implementiert wird
- **THEN** müssen Unit-Tests für die neue Funktionalität geschrieben werden
- **AND** alle Tests müssen lokal grün sein vor dem Push
- **AND** ein Push ohne grüne Tests ist nicht erlaubt

#### Scenario: Tests lokal ausführen

- **WHEN** Tests lokal ausgeführt werden sollen
- **THEN** wird folgender Befehl verwendet:
  ```bash
  JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn test
  ```
- **AND** MongoDB muss laufen (192.168.178.141:27017)

#### Scenario: Einzelne Testklasse ausführen

- **WHEN** nur eine spezifische Testklasse ausgeführt werden soll
- **THEN** wird folgender Befehl verwendet:
  ```bash
  JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn test -Dtest=<TestClassName>
  ```

---

### Requirement: Produktions- und Dev-URLs

#### Scenario: Umgebungs-URLs

- **WHEN** die Anwendung auf Dev-Stage getestet wird
- **THEN** ist sie erreichbar unter `https://mtg-dev.biester.vip`
- **WHEN** die Anwendung in Produktion betrieben wird
- **THEN** ist sie erreichbar unter `https://mtg-kubitos.biester.vip`
