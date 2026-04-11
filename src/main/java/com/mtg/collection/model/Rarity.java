package com.mtg.collection.model;

public enum Rarity {
    MYTHIC("mythic"),
    RARE("rare"),
    UNCOMMON("uncommon"),
    COMMON("common");

    private final String value;

    Rarity(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
