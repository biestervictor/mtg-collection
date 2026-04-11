package com.mtg.collection.dto;

import com.mtg.collection.model.CardState;

public class UserFilter {
    
    private String state;
    private String printing;
    private String rarity;
    private String search;
    
    public UserFilter() {
        this.state = CardState.ALL.getValue();
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPrinting() { return printing; }
    public void setPrinting(String printing) { this.printing = printing; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
}
