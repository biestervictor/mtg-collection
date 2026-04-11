# MTG Collection Manager - Spring Boot Thymeleaf

Ein Magic: The Gathering Sammlungsverwaltung, implementiert mit Spring Boot und Thymeleaf.

## Voraussetzungen

- Java 17 oder höher
- Maven 3.6+
- MongoDB (lokal oder Docker)

## Installation

### MongoDB starten (optional, mit Docker)

```bash
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

### Anwendung bauen

```bash
cd mtg-springboot
mvn clean package
```

### Anwendung starten

```bash
mvn spring-boot:run
```

Oder mit der JAR-Datei:

```bash
java -jar target/mtg-collection-manager-0.1.0.jar
```

Die Anwendung ist dann unter http://localhost:8080 erreichbar.

## Konfiguration

Die Standardkonfiguration befindet sich in `src/main/resources/application.properties`:

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/mtg-manager
server.port=8080
```

Für Produktion kannst du Umgebungsvariablen verwenden oder eine externe `application.yml` erstellen.

## Funktionen

- **Startseite**: Willkommensseite mit Links zu allen Funktionen
- **Collection Importer**: CSV-Dateien von Dragonshield importieren
  - Dragonshield Web Format
  - Dragonshield App Format
- **Show Collection**: Sammlung nach Set anzeigen mit Filtern
  - Nach Seltenheit filtern
  - Nach Status filtern (Besitzt, Fehlt, Tauschbar)
  - Nach Kartennamen oder Nummer suchen
- **Compare Collection**: Sammlungen zweier Benutzer vergleichen

## Benutzer

Die Anwendung unterstützt mehrere Benutzer:
- Andre (user1)
- Victor (user2)
- User 3 (user3)
- Marcel (user4)

## Technologie-Stack

- Spring Boot 3.2.0
- Spring Data MongoDB
- Thymeleaf
- Bootstrap 5
- Scryfall API für Kartendaten
