package com.mtg.collection.dto;

import java.util.List;

/**
 * Response body for POST /api/compare/trade-wizard.
 *
 * @param mode            "greedy" or "bundle" — echoed from request
 * @param pairs           Greedy mode: 1:1 pairs (empty for bundle mode)
 * @param bundle          Bundle mode: full set of cards on both sides (null for greedy)
 * @param totalA          Sum of card prices on side A
 * @param totalB          Sum of card prices on side B
 * @param diff            |totalA - totalB|
 * @param fairnessScore   0.0 (very unfair) ... 1.0 (perfectly balanced)
 * @param skippedA        Cards from user A's pool that did not enter a trade (with reason)
 * @param skippedB        Cards from user B's pool that did not enter a trade
 * @param notes           Free-form informational messages (e.g. "pool truncated to 300 cards")
 */
public record TradeWizardResponse(
        String mode,
        List<TradePair> pairs,
        TradeBundle bundle,
        double totalA,
        double totalB,
        double diff,
        double fairnessScore,
        List<SkippedCard> skippedA,
        List<SkippedCard> skippedB,
        List<String> notes
) {}
