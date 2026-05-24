package com.mtg.collection.dto;

import java.util.List;

/**
 * A single missing card entry returned by the Missing Card Wizard endpoint.
 */
public class WizardCard {

    private final String name;
    private final String collectorNumber;
    private final String rarity;
    private final String thumbnail;
    private final Double priceRegular;
    private final Double priceFoil;
    private final String purchaseLink;
    /** Other users in the app who have at least 2 copies (1 tradable) of this card. */
    private final List<String> tradableBy;

    public WizardCard(String name, String collectorNumber, String rarity, String thumbnail,
                      Double priceRegular, Double priceFoil, String purchaseLink,
                      List<String> tradableBy) {
        this.name = name;
        this.collectorNumber = collectorNumber;
        this.rarity = rarity;
        this.thumbnail = thumbnail;
        this.priceRegular = priceRegular;
        this.priceFoil = priceFoil;
        this.purchaseLink = purchaseLink;
        this.tradableBy = tradableBy;
    }

    public String getName()            { return name; }
    public String getCollectorNumber() { return collectorNumber; }
    public String getRarity()          { return rarity; }
    public String getThumbnail()       { return thumbnail; }
    public Double getPriceRegular()    { return priceRegular; }
    public Double getPriceFoil()       { return priceFoil; }
    public String getPurchaseLink()    { return purchaseLink; }
    public List<String> getTradableBy(){ return tradableBy; }
}
