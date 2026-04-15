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
    private double pricePerCard;   // cheapest regular EUR price across all printings
    private double totalMissingCost; // pricePerCard * stillNeeded

    public MissingCardEntry() {}

    public MissingCardEntry(String cardName, int requiredQuantity, int ownedQuantity) {
        this(cardName, requiredQuantity, ownedQuantity, 0.0);
    }

    public MissingCardEntry(String cardName, int requiredQuantity, int ownedQuantity, double pricePerCard) {
        this.cardName = cardName;
        this.requiredQuantity = requiredQuantity;
        this.ownedQuantity = ownedQuantity;
        this.stillNeeded = Math.max(0, requiredQuantity - ownedQuantity);
        this.pricePerCard = pricePerCard;
        this.totalMissingCost = pricePerCard * this.stillNeeded;
    }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public int getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(int requiredQuantity) { this.requiredQuantity = requiredQuantity; }

    public int getOwnedQuantity() { return ownedQuantity; }
    public void setOwnedQuantity(int ownedQuantity) { this.ownedQuantity = ownedQuantity; }

    public int getStillNeeded() { return stillNeeded; }
    public void setStillNeeded(int stillNeeded) { this.stillNeeded = stillNeeded; }

    public double getPricePerCard() { return pricePerCard; }
    public void setPricePerCard(double pricePerCard) { this.pricePerCard = pricePerCard; }

    public double getTotalMissingCost() { return totalMissingCost; }
    public void setTotalMissingCost(double totalMissingCost) { this.totalMissingCost = totalMissingCost; }
}
