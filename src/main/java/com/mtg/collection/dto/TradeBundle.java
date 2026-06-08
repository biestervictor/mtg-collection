package com.mtg.collection.dto;

import java.util.List;

/**
 * Bundle-Trade-Vorschlag: n:m-Tausch über mehrere Karten beider Seiten.
 * Wird vom Karmarkar-Karp-Algorithmus geliefert.
 */
public record TradeBundle(
        List<TradeCard> aSide,
        List<TradeCard> bSide
) {}
