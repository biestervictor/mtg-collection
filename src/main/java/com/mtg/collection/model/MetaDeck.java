package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Document(collection = "meta-decks")
public class MetaDeck {

    @Id
    private String id; // format + "_" + slug

    private String format;       // commander, modern, pioneer, standard, legacy
    private String name;         // display name from MTGGoldfish
    private String slug;         // URL slug (e.g. "Mono-Red+Aggro")
    private double playRate;     // META% as double (e.g. 1.1)
    private String commanderName; // only set for Commander format
    private List<MetaDeckCard> mainboard;
    private LocalDate fetchedAt;

    public MetaDeck() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public double getPlayRate() { return playRate; }
    public void setPlayRate(double playRate) { this.playRate = playRate; }

    public String getCommanderName() { return commanderName; }
    public void setCommanderName(String commanderName) { this.commanderName = commanderName; }

    public List<MetaDeckCard> getMainboard() { return mainboard; }
    public void setMainboard(List<MetaDeckCard> mainboard) { this.mainboard = mainboard; }

    public LocalDate getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDate fetchedAt) { this.fetchedAt = fetchedAt; }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class MetaDeckCard {
        private String name;
        private int quantity;

        public MetaDeckCard() {}

        public MetaDeckCard(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
