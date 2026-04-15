package com.mtg.collection.dto;

/**
 * A single card that the user is missing from a meta-deck,
 * together with the quantity required.
 */
public class MissingCardEntry {

    private String cardName;
    private int requiredQuantity;
    private int ownedQuantity;
    private int stillNeeded; // requiredQuantity - ownedQuantity (capped at 0)

    public MissingCardEntry() {}

    public MissingCardEntry(String cardName, int requiredQuantity, int ownedQuantity) {
        this.cardName = cardName;
        this.requiredQuantity = requiredQuantity;
        this.ownedQuantity = ownedQuantity;
        this.stillNeeded = Math.max(0, requiredQuantity - ownedQuantity);
    }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public int getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(int requiredQuantity) { this.requiredQuantity = requiredQuantity; }

    public int getOwnedQuantity() { return ownedQuantity; }
    public void setOwnedQuantity(int ownedQuantity) { this.ownedQuantity = ownedQuantity; }

    public int getStillNeeded() { return stillNeeded; }
    public void setStillNeeded(int stillNeeded) { this.stillNeeded = stillNeeded; }
}
