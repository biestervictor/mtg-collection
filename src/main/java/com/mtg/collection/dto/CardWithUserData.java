package com.mtg.collection.dto;

import com.mtg.collection.model.ScryfallCard;

public class CardWithUserData {
    
    private ScryfallCard card;
    private int quantity;
    private int foilQuantity;
    private String setCode;
    private String cardName;
    
    public CardWithUserData() {}
    
    public CardWithUserData(ScryfallCard card, int quantity, int foilQuantity) {
        this.card = card;
        this.quantity = quantity;
        this.foilQuantity = foilQuantity;
    }

    public ScryfallCard getCard() { return card; }
    public void setCard(ScryfallCard card) { this.card = card; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getFoilQuantity() { return foilQuantity; }
    public void setFoilQuantity(int foilQuantity) { this.foilQuantity = foilQuantity; }
    public String getSetCode() { return setCode; }
    public void setSetCode(String setCode) { this.setCode = setCode; }
    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }
    
    public String getDisplayName() {
        if (card != null) return card.getName();
        return cardName != null ? cardName : "";
    }
    
    public String getDisplayThumbnail() {
        if (card != null) return card.getThumbnailFront();
        return null;
    }
    
    public boolean isMissing() {
        return quantity == 0 && foilQuantity == 0;
    }
}
