package com.mtg.collection.dto;

import java.util.List;

/**
 * Ergebnis eines Karmarkar-Karp-Bundle-Matching: Bundle + nicht-eingebundene Karten.
 */
public record TradeBundleResult(
        TradeBundle bundle,
        List<SkippedCard> skippedA,
        List<SkippedCard> skippedB
) {}
