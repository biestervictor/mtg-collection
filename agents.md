# Agent Configuration

## Development Workflow

### Pflichtschritte bei jeder Änderung

1. **Lokal testen** – Tests müssen lokal grün sein vor dem Push
2. **Push auf `dev`** – löst den GitHub Actions CI/CD-Build automatisch aus
3. **CI/CD abwarten** – Build inkl. Maven-Tests, Docker-Image-Build und Push nach `ghcr.io`
4. **ArgoCD deployed automatisch** – bei erfolgreichem Build zieht ArgoCD das neue Image und deployed auf die Dev-Stage
5. **Dev-Stage testen** – manueller Test unter `https://mtg-dev.biester.vip`
6. **Bei Fehler** – Logs/Exception analysieren, Fix implementieren, Schleife ab Schritt 1 wiederholen
7. **Produktion** – nach erfolgreichem Dev-Test: `dev` → `main` mergen. ArgoCD erkennt den Merge auf `main` und aktualisiert das Produktions-Deployment automatisch

> **Wichtig:** Der CI-Workflow (`.github/workflows/maven.yml`) läuft **nur auf `dev`**, nicht auf `main`.
> Er baut das Docker-Image, bumpt die Version in `pom.xml` und trägt den neuen Tag in `helmcharts/values.yaml` ein.
> Nach dem Merge `dev → main` muss daher zuerst `git pull origin dev` (um den CI-Commit zu holen)
> und dann `main` mit `origin/dev` zusammengeführt werden, damit `values.yaml` den richtigen Image-Tag enthält.

### Produktions-URL
- **Prod:** `https://mtg-kubitos.biester.vip`
- **Dev:** `https://mtg-dev.biester.vip`

### Tests Required
**Every feature change MUST include unit tests.** Before pushing to GitHub:
1. Write tests for new functionality
2. Run tests locally: `mvn test`
3. Tests must pass before push

### Kostenerfassung (Pflicht)
**Bei JEDER Aenderung MUSS die Datei `costs.md` im Projekt-Root aktualisiert werden.**

1. Aktuellen Spend aus der OpenCode-SQLite-DB lesen (siehe unten) oder aus der UI-Anzeige
2. Delta seit dem letzten Eintrag berechnen
3. Neue Zeile in die Tabelle der aktuellen Session einfuegen

**Spend programmatisch ermitteln (bevorzugt):**
```bash
# Aktuelle Session-ID + Spend (sortiert nach time_updated)
sqlite3 ~/.local/share/opencode/opencode.db \
  "SELECT id, cost FROM session ORDER BY time_updated DESC LIMIT 1;"
```
Die SQLite-DB `~/.local/share/opencode/opencode.db` enthaelt die `session`-Tabelle mit Spalte
`cost` (REAL, in USD). Das ist die zuverlaessige Quelle — kein Raten oder Schaetzen mehr noetig.

**Format:**
```markdown
## Session: <session-id> | Kosten = <delta-seit-letztem-eintrag>

| # | Change / Task | Summary | Delta | Session gesamt | Modell | Datum |
|---|---------------|---------|-------|----------------|--------|-------|
| 1 | <change-name> | <kurze Beschreibung> | $X.XX | $X.XX | <modell> | YYYY-MM-DD |
```

**Regeln:**
- Session-ID: Die ersten 6 Zeichen der OpenCode-Session-ID (z.B. `1624e5` aus `ses_1624e5f...`)
- Delta: Differenz zwischen aktuellem Spend (DB-Wert) und letztem dokumentierten Wert
- Session gesamt: Kumulierte Kosten aller Eintraege der Session (= aktueller DB-Wert)
- Neue Session = neuer H2-Abschnitt
- Mehrere Tasks in einer Session = fortlaufende Nummerierung unter gleichem Header

---

## Anwendungsarchitektur

### Stack
- **Backend:** Spring Boot 3, Java 17
- **Templates:** Thymeleaf (server-side rendering)
- **Datenbank:** MongoDB (Spring Data)
- **Auth:** Microsoft Entra ID (Azure AD) via OAuth2/OIDC (`spring-security-oauth2-client`)
- **Sessions:** Spring Session MongoDB – Sessions überleben Pod-Restarts; Timeout 7 Tage
- **CSS/JS:** Bootstrap 5, Bootstrap Icons, Select2 (compare-Seite), jQuery (webjars)

### Seiten
| Route | Beschreibung |
|-------|-------------|
| `/show` | Zeigt die Kartensammlung eines Users für ein gewähltes Set |
| `/compare` | Vergleicht zwei Sammlungen (diff: nur User, nur Compare) |
| `/import` | CSV-Import (Dragonshield-Format) |
| `/statistics` | Sammlungsstatistiken mit Preisverlauf |
| `/deck-suggest` | Deck-Vorschläge basierend auf der Sammlung |
| `/my-decks` | Eigene Decks verwalten |
| `/sell-suggestions` | Verkaufsvorschläge |
| `/price-watch` | Preisbeobachtung |
| `/search` | Kartensuche |

### Benutzer-System
- **App-User:** `Victor` und `Andre` (feste Strings, case-sensitiv)
- **Mapping Email → App-User:** persistiert in MongoDB, Collection `user_email_mappings`
  - `_id`: lowercase Email (aus OIDC-Claim `email`, Fallback `preferred_username`)
  - `appUser`: `"Victor"` oder `"Andre"`
  - `createdAt`: Timestamp
- **Prod-Lock:** In Produktion kann ein bestehendes Mapping nicht überschrieben werden
  (`POST /api/user/map` prüft `isProd() && findById(email).isPresent()`)
- **Erkennung in Thymeleaf:** `${currentAppUser}` – gesetzt von `GlobalModelAttributes`
- **Erstes Login ohne Mapping:** `${userMappingRequired} = true` → nicht-schließbares Bootstrap-Modal erscheint

### CSRF
- CSRF ist **nur für `/api/**` deaktiviert** (fetch()-POST-Aufrufe aus JS)
- Alle Thymeleaf-Formular-Endpoints (`/show`, `/import`, etc.) behalten CSRF-Schutz

---

## Token / Promo Set Handling

### Konzept
Scryfall kennt neben regulären Sets auch **Token-Sets** (z.B. `ttdm` für das Token-Set zu `tdm`)
und **Promo-Sets** (z.B. `pdmu` für Promo-Karten zu `dmu`).
Diese sollen **nicht im Set-Dropdown** erscheinen, sondern als **Extra-Sektion** unterhalb der
regulären Karten auf der Show-Seite anzeigbar sein.

### Set-Code-Konvention (Scryfall-Standard)
- Token-Set-Code: `"t" + mainSetCode` → z.B. `tdm` → `ttdm`
- Promo-Set-Code: `"p" + mainSetCode` → z.B. `dmu` → `pdmu`

### Implementierung

**1. DB-Speicherung (ScryfallService.fetchSetsFromApi):**
Token- und Promo-Sets werden in MongoDB gespeichert (sie bestehen den API-Fetch-Filter, der nur
`alchemy, minigame, memorabilia, vanguard, digital` ausschließt).

**2. Dropdown-Filter (ScryfallService.getAllSets):**
```java
return sets.stream()
    .filter(s -> !s.isDigital())
    .filter(s -> !"token".equals(s.getSetType()))   // Token-Sets raus
    .filter(s -> !"promo".equals(s.getSetType()))    // Promo-Sets raus
    .collect(Collectors.toList());
```
Dieser Filter gilt für **alle** Seiten (show, compare, statistics, …) – `getAllSets(false)` wird
überall verwendet. Token- und Promo-Sets erscheinen in **keinem** Set-Dropdown.

**3. Extra-Sektion auf der Show-Seite (CollectionController.showCollection):**
```java
// Token-Set laden, wenn "Show Tokens" aktiv
if ("true".equals(showTokens)) {
    String tokenSetCode = "t" + set;                // Konvention: "t" + Code
    List<ScryfallCard> tokenSetCards = scryfallService.getCardsBySet(tokenSetCode, null);
    if (!tokenSetCards.isEmpty()) {
        tokenCards = collectionService.getCardsWithUserData(user, tokenSetCode, null);
    }
}
// Analog für Promo-Set mit "p" + set
```
Die Token-/Promo-Karten werden als separate Model-Attribute `tokenCards` / `promoCards` übergeben
und in `show.html` in eigenen Sektionen am Ende der Seite gerendert.

**4. Compare-Seite:** Hat **keine** Token/Promo-Sektionen – nur die regulären Set-Karten.

### Was NICHT geändert werden darf
- Die `getAllSets`-Filter dürfen **nicht entfernt** werden, sonst erscheinen Token-/Promo-Sets im Dropdown
- Das Set-Code-Präfix `"t"` / `"p"` ist Scryfall-Standard – nicht ändern
- Token/Promo-Cards sind in MongoDB vorhanden (für den Show-Feature nötig) – `deleteAll()` im
  Cache-Clear darf diese nicht löschen

---

## Scryfall-Set-Typen (Referenz)
| setType | Beschreibung | Im Dropdown |
|---------|-------------|-------------|
| `expansion` | Reguläres Erweiterungs-Set | ✅ |
| `core` | Core Set | ✅ |
| `masters` | Masters-Set (Reprints) | ✅ |
| `commander` | Commander-Precon | ✅ |
| `draft_innovation` | Draft-Innovation (z.B. Conspiracy) | ✅ |
| `token` | Token-Karten zum Haupt-Set | ❌ (Extra-Sektion) |
| `promo` | Promo-Karten zum Haupt-Set | ❌ (Extra-Sektion) |
| `alchemy` | Digitale Alchemy-Varianten | ❌ (nicht in DB) |
| `memorabilia` | Sammlerstücke ohne Spielwert | ❌ (nicht in DB) |
| `digital` | Rein digital (Arena) | ❌ (nicht in DB) |

---

## External Tools Location

**USB Stick:** `/mnt/usb`

### Java
- **Path:** `/mnt/usb/java17`
- **JAVA_HOME:** `/mnt/usb/java17`

### Maven
- **Path:** `/mnt/usb/maven`
- **Local Repository:** `/mnt/usb/maven-repo`
- **Settings:** `/home/victor/.m2/settings.xml`

## Project Locations

### MTG Collection (Neu)
- **Path:** `/home/victor/mtg-springboot`
- **Type:** Spring Boot Application
- **Build:** Maven
- **GitHub Repo:** `biestervictor/mtg-collection`
- **Docker Registry:** `ghcr.io/biestervictor/mtg-collection`
- **K8s Manifests:** `k8s/` im Projektverzeichnis
- **K8s Namespace:** `magiccollection`
- **Raspberry Pi:** Cluster läuft auf ARM64

### MTG Collection (Alt)
- **Path:** `/home/victor/mtg_old`
- **Type:** Node.js Implementation

## Prerequisites

**MongoDB required** - already running at `192.168.178.141:27017` (configured in application.properties)

## Application Startup

### MTG Spring Boot App (korrekt)
```bash
cd /home/victor/mtg-springboot
/mnt/usb/java17/bin/java -jar target/mtg-collection-manager-0.1.0.jar > app.log 2>&1 &
```
- WICHTIG: `> app.log 2>&1 &` muss in EINEM Befehl sein, nicht verkettet
- Runs on port **8080**
- **Zugriff von anderen Geräten:** `http://192.168.178.140:8080`
- **Log-Datei:** `app.log` im Projektverzeichnis

### Maven Build (ohne Tests)
```bash
JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn package -DskipTests
```

### Tests ausführen
```bash
# Requires MongoDB running
JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn test
```

### Run single test class
```bash
JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn test -Dtest=CollectionControllerTest
```

### Alternative: Run with Maven (dev mode)
```bash
cd /home/victor/mtg-springboot
JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn spring-boot:run
```

## Kubernetes Cluster

### Produktionscluster (MicroK8s auf Raspberry Pi 4)
- **Kubeconfig:** `/mnt/usb/kubeconfig` + `~/.kube/config`
- **Server:** `https://192.168.178.90:16443`
- **kubectl:** `/mnt/usb/kubectl`
- **Architektur:** ARM64 (Raspberry Pi)

### Ingress/Kubernetes Upload Configuration

Die App verwendet nginx Ingress Controller für Datei-Uploads. Diese Konfiguration ist CRITICAL für große CSV-Dateien:

**k8s/deployment.yaml - Environment Variables:**
```yaml
env:
  - name: SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE
    value: "50MB"
  - name: SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE
    value: "50MB"
```

**k8s/ingress.yaml - Annotations:**
```yaml
annotations:
  nginx.ingress.kubernetes.io/proxy-body-size: "0"
  nginx.ingress.kubernetes.io/proxy-connect-timeout: "300"
  nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
  nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
  nginx.ingress.kubernetes.io/location-snippet: |
    client_max_body_size 0;
    client_body_buffer_size 64m;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
```

### Azure Key Vault Secret Store (ClusterSecretStore)
- **Name:** `azure-kv`
- **Tenant:** `71b67176-40e1-4d4e-80fe-9251918425b2`
- **Vault URL:** `https://treasurykeyvault.vault.azure.net/`
- **Secret Name:** `imagePullSecret` (enthält GHCR Docker Config als Base64)
- **TLS Zertifikate:** Im Vault unter `my-tls-secret`

### Zugriff
```bash
/mnt/usb/kubectl --kubeconfig ~/.kube/config get nodes
/mnt/usb/kubectl --kubeconfig ~/.kube/config get pods -A
/mnt/usb/kubectl --kubeconfig ~/.kube/config get externalsecrets -A
```

## Referenz-Projekte

### suppcheck
- **GitHub:** `biestervictor/suppcheck`
- **Docker Registry:** `ghcr.io/biestervictor/suppcheck`
- **K8s Manifests:** Helmcharts unter `helmcharts/`
- **Workflow:** `.github/workflows/maven.yml`
- **Verwendet für:** CI/CD Muster, Helm Chart Struktur
