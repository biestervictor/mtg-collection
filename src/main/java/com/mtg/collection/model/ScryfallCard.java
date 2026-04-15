package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "scryfall-cards")
public class ScryfallCard {
    
    @Id
    private String id;
    private String name;
    private String setCode;
    private String collectorNumber;
    private String rarity;
    private String typeLine;
    private String frameStatus;
    private String thumbnailFront;
    private String thumbnailBack;
    private String imageFront;
    private String imageBack;
    private Double priceRegular;
    private Double priceFoil;
    private String purchaseLink;
    private String borderColor;
    private boolean fullArt;
    
    public ScryfallCard() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSetCode() { return setCode; }
    public void setSetCode(String setCode) { this.setCode = setCode; }
    public String getCollectorNumber() { return collectorNumber; }
    public void setCollectorNumber(String collectorNumber) { this.collectorNumber = collectorNumber; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getTypeLine() { return typeLine; }
    public void setTypeLine(String typeLine) { this.typeLine = typeLine; }
    public String getFrameStatus() { return frameStatus; }
    public void setFrameStatus(String frameStatus) { this.frameStatus = frameStatus; }
    public String getThumbnailFront() { return thumbnailFront; }
    public void setThumbnailFront(String thumbnailFront) { this.thumbnailFront = thumbnailFront; }
    public String getThumbnailBack() { return thumbnailBack; }
    public void setThumbnailBack(String thumbnailBack) { this.thumbnailBack = thumbnailBack; }
    public String getImageFront() { return imageFront; }
    public void setImageFront(String imageFront) { this.imageFront = imageFront; }
    public String getImageBack() { return imageBack; }
    public void setImageBack(String imageBack) { this.imageBack = imageBack; }
    public Double getPriceRegular() { return priceRegular; }
    public void setPriceRegular(Double priceRegular) { this.priceRegular = priceRegular; }
    public Double getPriceFoil() { return priceFoil; }
    public void setPriceFoil(Double priceFoil) { this.priceFoil = priceFoil; }
    public String getPurchaseLink() { return purchaseLink; }
    public void setPurchaseLink(String purchaseLink) { this.purchaseLink = purchaseLink; }
    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }
    public boolean isFullArt() { return fullArt; }
    public void setFullArt(boolean fullArt) { this.fullArt = fullArt; }
}
