package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Document(collection = "card-collection")
public class UserCard {
    
    @Id
    private String id;
    private String user;
    private String name;
    private String setCode;
    private String collectorNumber;
    private int quantity;
    private boolean foil;
    
    public UserCard() {}
    
    public UserCard(String user, String name, String setCode, String collectorNumber, int quantity, boolean foil) {
        this.user = user;
        this.name = name;
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.quantity = quantity;
        this.foil = foil;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
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
}
