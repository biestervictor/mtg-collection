package com.mtg.collection.dto;

/**
 * Karte, die aus dem Trade-Pool herausgefallen ist mit Grund.
 * Reasons: no_price, below_min_value, pool_truncated, no_match_in_tolerance
 */
public record SkippedCard(
        TradeCard card,
        String reason
) {}
