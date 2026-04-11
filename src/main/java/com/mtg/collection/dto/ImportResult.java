package com.mtg.collection.dto;

import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import java.util.List;

public class ImportResult {
    
    private int cardsCount;
    private int newCardsCount;
    private int addedCardsCount;
    private int removedCardsCount;
    private List<CardWithUserData> newCards;
    private List<ImportedCardInfo> addedCards;
    private List<ImportedCardInfo> removedCards;
    private List<String> errors;
    
    public ImportResult() {}

    public int getCardsCount() { return cardsCount; }
    public void setCardsCount(int cardsCount) { this.cardsCount = cardsCount; }
    public int getNewCardsCount() { return newCardsCount; }
    public void setNewCardsCount(int newCardsCount) { this.newCardsCount = newCardsCount; }
    public int getAddedCardsCount() { return addedCardsCount; }
    public void setAddedCardsCount(int addedCardsCount) { this.addedCardsCount = addedCardsCount; }
    public int getRemovedCardsCount() { return removedCardsCount; }
    public void setRemovedCardsCount(int removedCardsCount) { this.removedCardsCount = removedCardsCount; }
    public List<CardWithUserData> getNewCards() { return newCards; }
    public void setNewCards(List<CardWithUserData> newCards) { this.newCards = newCards; }
    public List<ImportedCardInfo> getAddedCards() { return addedCards; }
    public void setAddedCards(List<ImportedCardInfo> addedCards) { this.addedCards = addedCards; }
    public List<ImportedCardInfo> getRemovedCards() { return removedCards; }
    public void setRemovedCards(List<ImportedCardInfo> removedCards) { this.removedCards = removedCards; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
