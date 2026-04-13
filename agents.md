# Agent Configuration

## Development Rules

### Tests Required
**Every feature change MUST include unit tests.** Before pushing to GitHub:
1. Write tests for new functionality
2. Run tests locally: `JAVA_HOME=/mnt/usb/java17 /mnt/usb/maven/bin/mvn test`
3. Tests must pass before push

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
