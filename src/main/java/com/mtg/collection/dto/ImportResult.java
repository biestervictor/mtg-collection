package com.mtg.collection.dto;

import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    // ── Inner class: info about one set of removed exact-duplicate lines ────
    public static class DuplicateInfo {
        private final String folder;
        private final String cardName;
        private final String setCode;
        private final String collectorNumber;
        private final boolean foil;
        private final int occurrences; // number of extra copies removed (total appearances - 1)

        public DuplicateInfo(String folder, String cardName, String setCode,
                             String collectorNumber, boolean foil, int occurrences) {
            this.folder          = folder;
            this.cardName        = cardName;
            this.setCode         = setCode;
            this.collectorNumber = collectorNumber;
            this.foil            = foil;
            this.occurrences     = occurrences;
        }

        public String  getFolder()          { return folder; }
        public String  getCardName()        { return cardName; }
        public String  getSetCode()         { return setCode; }
        public String  getCollectorNumber() { return collectorNumber; }
        public boolean isFoil()             { return foil; }
        public int     getOccurrences()     { return occurrences; }
    }

    // ── Fields ───────────────────────────────────────────────────────────────
    private int cardsCount;
    private int newCardsCount;
    private int addedCardsCount;
    private int removedCardsCount;
    private List<CardWithUserData>   newCards;
    private List<ImportedCardInfo>   addedCards;
    private List<ImportedCardInfo>   removedCards;
    private List<String>             errors;
    private List<DuplicateInfo>      duplicatesRemoved = new ArrayList<>();
    private List<String>             unknownSetCodes   = new ArrayList<>();

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
    public List<DuplicateInfo> getDuplicatesRemoved() { return duplicatesRemoved; }
    public void setDuplicatesRemoved(List<DuplicateInfo> duplicatesRemoved) { this.duplicatesRemoved = duplicatesRemoved; }
    public List<String> getUnknownSetCodes() { return unknownSetCodes; }
    public void setUnknownSetCodes(List<String> unknownSetCodes) { this.unknownSetCodes = unknownSetCodes; }
}
