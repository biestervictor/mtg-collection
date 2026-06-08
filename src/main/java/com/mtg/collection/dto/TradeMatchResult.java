package com.mtg.collection.dto;

import java.util.List;

/**
 * Ergebnis eines Greedy-Pair-Matching: Pairs + nicht-matchbare Karten je Seite.
 */
public record TradeMatchResult(
        List<TradePair> pairs,
        List<SkippedCard> skippedA,
        List<SkippedCard> skippedB
) {}
