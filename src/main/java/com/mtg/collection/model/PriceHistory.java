package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Daily price snapshot for a single card (setCode + collectorNumber).
 * One document per card per day; ID is deterministic to allow idempotent upserts.
 */
@Document(collection = "price-history")
public class PriceHistory {

    /** Composite key: setCode + "_" + collectorNumber + "_" + date (YYYY-MM-DD) */
    @Id
    private String id;

    private String    setCode;
    private String    collectorNumber;
    private String    cardName;
    private String    thumbnailUrl;
    private LocalDate date;
    private Double    priceRegular;
    private Double    priceFoil;

    public PriceHistory() {}

    public PriceHistory(String setCode, String collectorNumber, String cardName,
                        String thumbnailUrl, LocalDate date,
                        Double priceRegular, Double priceFoil) {
        this.id              = setCode + "_" + collectorNumber + "_" + date;
        this.setCode         = setCode;
        this.collectorNumber = collectorNumber;
        this.cardName        = cardName;
        this.thumbnailUrl    = thumbnailUrl;
        this.date            = date;
        this.priceRegular    = priceRegular;
        this.priceFoil       = priceFoil;
    }

    public String    getId()                          { return id; }
    public void      setId(String id)                 { this.id = id; }

    public String    getSetCode()                     { return setCode; }
    public void      setSetCode(String setCode)       { this.setCode = setCode; }

    public String    getCollectorNumber()             { return collectorNumber; }
    public void      setCollectorNumber(String cn)    { this.collectorNumber = cn; }

    public String    getCardName()                    { return cardName; }
    public void      setCardName(String cardName)     { this.cardName = cardName; }

    public String    getThumbnailUrl()                { return thumbnailUrl; }
    public void      setThumbnailUrl(String url)      { this.thumbnailUrl = url; }

    public LocalDate getDate()                        { return date; }
    public void      setDate(LocalDate date)          { this.date = date; }

    public Double    getPriceRegular()                { return priceRegular; }
    public void      setPriceRegular(Double price)    { this.priceRegular = price; }

    public Double    getPriceFoil()                   { return priceFoil; }
    public void      setPriceFoil(Double price)       { this.priceFoil = price; }
}
