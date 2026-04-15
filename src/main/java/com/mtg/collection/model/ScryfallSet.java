package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "scryfall-sets")
public class ScryfallSet {
    
    @Id
    private String id;
    private String name;
    private String setCode;
    private int cardCount;
    private String releasedAt;
    private String icon;
    private boolean digital;   // true = Arena/MTGO-only set (excluded from UI)
    private String setType;    // Scryfall set_type (e.g. "expansion", "starter", "digital")
    
    public ScryfallSet() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSetCode() { return setCode; }
    public void setSetCode(String setCode) { this.setCode = setCode; }
    public int getCardCount() { return cardCount; }
    public void setCardCount(int cardCount) { this.cardCount = cardCount; }
    public String getReleasedAt() { return releasedAt; }
    public void setReleasedAt(String releasedAt) { this.releasedAt = releasedAt; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isDigital() { return digital; }
    public void setDigital(boolean digital) { this.digital = digital; }
    public String getSetType() { return setType; }
    public void setSetType(String setType) { this.setType = setType; }
}
