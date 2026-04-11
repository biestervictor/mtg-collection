package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "import-history")
public class ImportHistory {
    
    @Id
    private String id;
    private String user;
    private String format;
    private LocalDateTime importedAt;
    private int totalCardsCount;
    private int uniqueCardsCount;
    private int addedCardsCount;
    private int removedCardsCount;
    private List<ImportedCardInfo> addedCards;
    private List<ImportedCardInfo> removedCards;
    
    public ImportHistory() {
        this.importedAt = LocalDateTime.now();
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public int getTotalCardsCount() { return totalCardsCount; }
    public void setTotalCardsCount(int totalCardsCount) { this.totalCardsCount = totalCardsCount; }
    public int getUniqueCardsCount() { return uniqueCardsCount; }
    public void setUniqueCardsCount(int uniqueCardsCount) { this.uniqueCardsCount = uniqueCardsCount; }
    public int getAddedCardsCount() { return addedCardsCount; }
    public void setAddedCardsCount(int addedCardsCount) { this.addedCardsCount = addedCardsCount; }
    public int getRemovedCardsCount() { return removedCardsCount; }
    public void setRemovedCardsCount(int removedCardsCount) { this.removedCardsCount = removedCardsCount; }
    public List<ImportedCardInfo> getAddedCards() { return addedCards; }
    public void setAddedCards(List<ImportedCardInfo> addedCards) { this.addedCards = addedCards; }
    public List<ImportedCardInfo> getRemovedCards() { return removedCards; }
    public void setRemovedCards(List<ImportedCardInfo> removedCards) { this.removedCards = removedCards; }
    
    public static class ImportedCardInfo {
        private String name;
        private String setCode;
        private String collectorNumber;
        private int quantity;
        private boolean foil;
        
        public ImportedCardInfo() {}
        
        public ImportedCardInfo(String name, String setCode, String collectorNumber, int quantity, boolean foil) {
            this.name = name;
            this.setCode = setCode;
            this.collectorNumber = collectorNumber;
            this.quantity = quantity;
            this.foil = foil;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSetCode() { return setCode; }
        public void setSetCode(String setCode) { this.setCode = setCode; }
        public String getCollectorNumber() { return collectorNumber; }
        public void setCollectorNumber(String collectorNumber) { this.collectorNumber = collectorNumber; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public boolean isFoil() { return foil; }
        public void setFoil(boolean foil) { this.foil = foil; }
        
        public String getKey() {
            return setCode + "_" + collectorNumber + "_" + foil;
        }
    }
}
