package com.mtg.collection.dto;

/**
 * Eine handelbare Karten-Variante (Normal oder Foil als getrennte Einträge).
 * Owner = Username, der diese Karte besitzt und bereit ist zu tauschen.
 */
public record TradeCard(
        String cardId,
        String name,
        String setCode,
        String collectorNumber,
        Double price,
        boolean foil,
        String owner
) {}
