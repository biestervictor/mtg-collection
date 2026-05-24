package com.mtg.collection.dto;

import java.util.List;

/**
 * One treatment-group bucket returned by the Missing Card Wizard endpoint.
 * E.g. "Showcase" → 5 missing cards → total cost €12.40
 */
public class WizardGroup {

    private final String groupName;
    private final List<WizardCard> cards;
    /** Sum of priceRegular for all cards in this group (null prices treated as 0). */
    private final double totalCost;

    public WizardGroup(String groupName, List<WizardCard> cards, double totalCost) {
        this.groupName = groupName;
        this.cards = cards;
        this.totalCost = totalCost;
    }

    public String getGroupName()       { return groupName; }
    public List<WizardCard> getCards() { return cards; }
    public double getTotalCost()       { return totalCost; }
    public int getMissingCount()       { return cards.size(); }
}
