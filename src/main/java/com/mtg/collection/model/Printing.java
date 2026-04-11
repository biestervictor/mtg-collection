package com.mtg.collection.model;

public enum Printing {
    REGULAR("regular"),
    FOIL("foil");

    private final String value;

    Printing(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
