package com.mtg.collection.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a meta-deck suggestion for a specific user, enriched with
 * ownership information derived from their collection.
 */
public class DeckSuggestion {

    private String deckName;
    private String format;
    private double playRate;         // META% from MTGGoldfish
    private String commanderName;    // null unless format == commander

    private int totalCards;          // non-land unique card slots in the deck
    private int ownedUniqueCards;    // unique card names the user owns (≥1 copy)
    private int missingUniqueCards;  // unique card names the user is missing

    private double completionPercent; // ownedUniqueCards / totalCards * 100

    private List<MissingCardEntry> missingCards;

    private LocalDate fetchedAt;

    public DeckSuggestion() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getDeckName() { return deckName; }
    public void setDeckName(String deckName) { this.deckName = deckName; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public double getPlayRate() { return playRate; }
    public void setPlayRate(double playRate) { this.playRate = playRate; }

    public String getCommanderName() { return commanderName; }
    public void setCommanderName(String commanderName) { this.commanderName = commanderName; }

    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int totalCards) { this.totalCards = totalCards; }

    public int getOwnedUniqueCards() { return ownedUniqueCards; }
    public void setOwnedUniqueCards(int ownedUniqueCards) { this.ownedUniqueCards = ownedUniqueCards; }

    public int getMissingUniqueCards() { return missingUniqueCards; }
    public void setMissingUniqueCards(int missingUniqueCards) { this.missingUniqueCards = missingUniqueCards; }

    public double getCompletionPercent() { return completionPercent; }
    public void setCompletionPercent(double completionPercent) { this.completionPercent = completionPercent; }

    public List<MissingCardEntry> getMissingCards() { return missingCards; }
    public void setMissingCards(List<MissingCardEntry> missingCards) { this.missingCards = missingCards; }

    public LocalDate getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDate fetchedAt) { this.fetchedAt = fetchedAt; }
}
