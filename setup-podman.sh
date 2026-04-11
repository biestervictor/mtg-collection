#!/bin/bash
# Podman Setup Script for MTG Collection
# Run with: chmod +x setup-podman.sh && ./setup-podman.sh

set -e

USB_MOUNT="/mnt/usb"
PODMAN_DIR="$USB_MOUNT/podman"

echo "=========================================="
echo "MTG Collection - Podman Setup"
echo "=========================================="

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Bitte mit sudo ausführen: sudo $0"
    exit 1
fi

# Create directories on USB
echo "[1/5] Erstelle Verzeichnisse auf USB..."
mkdir -p "$PODMAN_DIR"/{run,storage,images,volumes}

# Install Podman
echo "[2/5] Installiere Podman..."
apt-get update
apt-get install -y podman fuse-overlayfs curl

# Configure Podman to use USB storage
echo "[3/5] Konfiguriere Storage auf USB..."
cat > /etc/containers/storage.conf << 'EOF'
[storage]
driver = "overlay"
runroot = "/mnt/usb/podman/run"
graphroot = "/mnt/usb/podman/storage"
EOF

# Create podman-compose file for easy deployment
echo "[4/5] Erstelle docker-compose.yml..."

cat > "$USB_MOUNT/mtg-collection/docker-compose.yml" << 'EOF'
version: "3.8"

services:
  mtg-collection:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: mtg-collection
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://192.168.178.141:27017/mtg_collection
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s

  # Optional: MongoDB container (uncomment if no external MongoDB)
  # mongodb:
  #   image: mongo:7
  #   container_name: mtg-mongodb
  #   ports:
  #     - "27017:27017"
  #   volumes:
  #     - mongodb-data:/data/db
  #   restart: unless-stopped

# volumes:
#   mongodb-data:
EOF

# Create helper scripts
echo "[5/5] Erstelle Hilfs-Scripts..."

# Build script
cat > "$USB_MOUNT/mtg-collection/build.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
podman build -t mtg-collection:latest .
EOF
chmod +x "$USB_MOUNT/mtg-collection/build.sh"

# Run script
cat > "$USB_MOUNT/mtg-collection/run.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
podman run -d --name mtg-collection -p 8080:8080 \
    -e SPRING_DATA_MONGODB_URI=mongodb://192.168.178.141:27017/mtg_collection \
    --restart unless-stopped \
    localhost/mtg-collection:latest
EOF
chmod +x "$USB_MOUNT/mtg-collection/run.sh"

# Stop script
cat > "$USB_MOUNT/mtg-collection/stop.sh" << 'EOF'
#!/bin/bash
podman stop mtg-collection
podman rm mtg-collection
EOF
chmod +x "$USB_MOUNT/mtg-collection/stop.sh"

# Logs script
cat > "$USB_MOUNT/mtg-collection/logs.sh" << 'EOF'
#!/bin/bash
podman logs -f mtg-collection
EOF
chmod +x "$USB_MOUNT/mtg-collection/logs.sh"

echo ""
echo "=========================================="
echo "Podman Setup abgeschlossen!"
echo "=========================================="
echo ""
echo "Verzeichnisse auf USB:"
echo "  $PODMAN_DIR"
echo ""
echo "MTG Collection Projekt:"
echo "  $USB_MOUNT/mtg-collection/"
echo ""
echo "Nächste Schritte:"
echo "  cd $USB_MOUNT/mtg-collection"
echo "  ./build.sh    # Buildt das Image"
echo "  ./run.sh      # Startet den Container"
echo "  ./logs.sh     # Zeigt Logs"
echo "  ./stop.sh     # Stoppt den Container"
echo ""
