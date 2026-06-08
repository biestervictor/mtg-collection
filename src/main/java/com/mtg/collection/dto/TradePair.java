package com.mtg.collection.dto;

/**
 * Ein einzelner 1:1-Trade-Vorschlag: User A bietet eine Karte, bekommt eine andere.
 */
public record TradePair(
        TradeCard fromA,
        TradeCard fromB
) {}
