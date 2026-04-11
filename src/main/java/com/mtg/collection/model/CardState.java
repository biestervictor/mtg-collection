package com.mtg.collection.model;

public enum CardState {
    ALL("all"),
    OWNED("owned"),
    MISSING("missing"),
    TRADABLE("tradable");

    private final String value;

    CardState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
