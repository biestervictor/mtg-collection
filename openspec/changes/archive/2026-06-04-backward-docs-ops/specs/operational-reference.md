# Spec: Operational Reference

## EXISTING Requirements

### Requirement: Externe Toolchain (USB-Stick)

Auf dem Raspberry Pi (Entwicklungs- und Produktionsumgebung) liegen Java und Maven auf einem USB-Stick unter `/mnt/usb`. Standard-Systempfade werden nicht verwendet.

#### Scenario: Java

- **WHEN** Java benötigt wird
- **THEN** ist der Pfad: `/mnt/usb/java17`
- **AND** `JAVA_HOME` muss auf `/mnt/usb/java17` gesetzt werden

#### Scenario: Maven

- **WHEN** Maven-Befehle ausgeführt werden
- **THEN** ist der Pfad: `/mnt/usb/maven/bin/mvn`
- **AND** das lokale Maven-Repository liegt unter `/mnt/usb/maven-repo`
- **AND** Maven-Settings liegen unter `/home/victor/.m2/settings.xml`
- **AND** Maven-Befehle werden immer mit explizitem `JAVA_HOME` aufgerufen:
  ```bash
  JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn <goal>
  ```

---

### Requirement: Projektpfade

#### Scenario: MTG Collection (aktuell, Spring Boot)

- **WHEN** das aktuelle Projekt referenziert wird
- **THEN** liegt es unter `/home/victor/mtg-springboot`
- **AND** GitHub Repo: `biestervictor/mtg-collection`
- **AND** Docker Registry: `ghcr.io/biestervictor/mtg-collection`
- **AND** K8s Manifests: `k8s/` im Projektverzeichnis
- **AND** K8s Namespace: `magiccollection`
- **AND** Kubernetes-Cluster läuft auf ARM64 (Raspberry Pi 4)

#### Scenario: MTG Collection (alt, Node.js)

- **WHEN** die alte Implementierung referenziert wird
- **THEN** liegt sie unter `/home/victor/mtg_old`
- **AND** es handelt sich um eine Node.js-Implementierung (nicht mehr aktiv gewartet)

---

### Requirement: Anwendungs-Startup

#### Scenario: App starten (korrekte Methode)

- **WHEN** die Anwendung manuell gestartet wird
- **THEN** wird folgender Befehl verwendet:
  ```bash
  /mnt/usb/java17/bin/java -jar target/mtg-collection-manager-0.1.0.jar > app.log 2>&1 &
  ```
- **AND** `> app.log 2>&1 &` muss in **einem einzigen Befehl** stehen (nicht als separate Verkettung)
- **AND** die Anwendung läuft auf Port **8080**
- **AND** Logs werden in `app.log` im Projektverzeichnis geschrieben
- **AND** die Anwendung ist von anderen Geräten im LAN erreichbar unter `http://192.168.178.140:8080`

#### Scenario: Maven Build (ohne Tests)

- **WHEN** ein Build ohne Tests durchgeführt wird
- **THEN** wird folgender Befehl verwendet:
  ```bash
  JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn package -DskipTests
  ```

#### Scenario: Anwendung im Dev-Modus starten

- **WHEN** die Anwendung im Maven Spring Boot Dev-Modus gestartet wird
- **THEN** wird folgender Befehl verwendet:
  ```bash
  JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn spring-boot:run
  ```

---

### Requirement: Kubernetes-Cluster Zugriff

#### Scenario: Cluster-Verbindung

- **WHEN** auf den Kubernetes-Cluster zugegriffen wird
- **THEN** liegt die Kubeconfig unter `/mnt/usb/kubeconfig` (alternativ `~/.kube/config`)
- **AND** der Cluster-API-Server ist erreichbar unter `https://192.168.178.90:16443`
- **AND** kubectl liegt unter `/mnt/usb/kubectl`
- **AND** Standard-kubectl-Befehle verwenden: `/mnt/usb/kubectl --kubeconfig ~/.kube/config <cmd>`

#### Scenario: Cluster-Status prüfen

- **WHEN** der Cluster-Status geprüft wird
- **THEN** werden folgende Befehle verwendet:
  ```bash
  /mnt/usb/kubectl --kubeconfig ~/.kube/config get nodes
  /mnt/usb/kubectl --kubeconfig ~/.kube/config get pods -A
  /mnt/usb/kubectl --kubeconfig ~/.kube/config get externalsecrets -A
  ```

#### Scenario: Azure Key Vault Secret Store

- **WHEN** Secrets aus dem Azure Key Vault abgerufen werden
- **THEN** ist der ClusterSecretStore konfiguriert mit:
  - Name: `azure-kv`
  - Tenant: `71b67176-40e1-4d4e-80fe-9251918425b2`
  - Vault URL: `https://treasurykeyvault.vault.azure.net/`
  - Secret `imagePullSecret`: enthält die GHCR Docker Config als Base64
  - TLS-Zertifikate: im Vault unter `my-tls-secret`

---

### Requirement: Referenz-Projekt `suppcheck`

Das Projekt `suppcheck` dient als CI/CD-Referenzimplementierung für `mtg-collection`.

#### Scenario: Referenz für CI/CD-Muster

- **WHEN** CI/CD-Konfiguration oder Helm Chart Struktur angepasst wird
- **THEN** dient `biestervictor/suppcheck` als Referenz-Implementierung
- **AND** das Docker-Registry ist `ghcr.io/biestervictor/suppcheck`
- **AND** K8s Manifests liegen als Helm Charts unter `helmcharts/`
- **AND** der CI-Workflow liegt unter `.github/workflows/maven.yml`

---

### Requirement: MongoDB-Verbindung (Lokal)

#### Scenario: Lokale Entwicklung / Raspberry Pi

- **WHEN** die Anwendung lokal oder auf dem Raspberry Pi betrieben wird
- **THEN** wird MongoDB unter `192.168.178.141:27017` erwartet
- **AND** MongoDB muss laufen bevor die Anwendung gestartet oder Tests ausgeführt werden

#### Scenario: CI-Tests (GitHub Actions)

- **WHEN** Tests in der CI-Pipeline laufen
- **THEN** wird das Spring-Profil `ci` aktiviert (`application-ci.properties`)
- **AND** das CI-Profil stellt Dummy-Azure-OAuth2-Credentials bereit (verhindert OIDC-Discovery-Aufrufe)
