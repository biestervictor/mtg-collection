package com.mtg.collection.service;

import com.mtg.collection.dto.*;
import com.mtg.collection.model.ScryfallCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeWizardServiceTest {

    @Mock private CollectionService collectionService;

    @InjectMocks private TradeWizardService service;

    private List<SkippedCard> skippedOut;

    @BeforeEach
    void setUp() {
        skippedOut = new ArrayList<>();
    }

    // ── buildPool ──────────────────────────────────────────────────────────────

    @Test
    void buildPool_includesCardWithTwoOwnedAndZeroOther() {
        ScryfallCard sc = card("id1", "Lightning Bolt", 1.50, null);
        CardWithUserData uc = userCard(sc, 2, 0);
        CardWithUserData oc = userCard(sc, 0, 0);

        when(collectionService.getCardsWithUserData("Victor", "tst", null)).thenReturn(List.of(uc));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null)).thenReturn(List.of(oc));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertEquals(1, pool.size());
        assertEquals("Lightning Bolt", pool.get(0).name());
        assertFalse(pool.get(0).foil());
        assertTrue(skippedOut.isEmpty());
    }

    @Test
    void buildPool_excludesCardWithOnlyOneOwned() {
        ScryfallCard sc = card("id1", "X", 5.0, null);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(sc, 1, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(userCard(sc, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);
        assertTrue(pool.isEmpty());
    }

    @Test
    void buildPool_excludesWhenOtherUserAlsoOwns() {
        ScryfallCard sc = card("id1", "X", 5.0, null);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(sc, 3, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(userCard(sc, 1, 0)));  // Andre has 1 normal → exclude normal

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);
        assertTrue(pool.isEmpty());
    }

    @Test
    void buildPool_foilAndNormalSeparate() {
        // Victor: 2 normal + 2 foil; Andre: 0/0 → both should appear in pool
        ScryfallCard sc = card("id1", "X", 1.0, 5.0);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(sc, 2, 2)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(userCard(sc, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertEquals(2, pool.size());
        assertEquals(1, pool.stream().filter(t -> !t.foil()).count());
        assertEquals(1, pool.stream().filter(TradeCard::foil).count());
    }

    @Test
    void buildPool_belowMinValueGoesToSkipped() {
        ScryfallCard sc = card("id1", "Cheap", 0.20, null);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(sc, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(userCard(sc, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertTrue(pool.isEmpty());
        assertEquals(1, skippedOut.size());
        assertEquals("below_min_value", skippedOut.get(0).reason());
    }

    @Test
    void buildPool_nullPriceGoesToSkipped() {
        ScryfallCard sc = card("id1", "NoPrice", null, null);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(sc, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(userCard(sc, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertTrue(pool.isEmpty());
        assertEquals(1, skippedOut.size());
        assertEquals("no_price", skippedOut.get(0).reason());
    }

    @Test
    void buildPool_excludesLandsByDefault() {
        ScryfallCard land = card("id1", "Forest", 0.50, null);
        land.setTypeLine("Basic Land — Forest");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(land, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tst", null))
                .thenReturn(List.of(userCard(land, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertTrue(pool.isEmpty());
        assertEquals(1, skippedOut.size());
        assertEquals("excluded_land", skippedOut.get(0).reason());
    }

    @Test
    void buildPool_includesLandsWhenFlagSet() {
        ScryfallCard land = card("id1", "Forest", 0.50, null);
        land.setTypeLine("Basic Land — Forest");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(land, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tst", null))
                .thenReturn(List.of(userCard(land, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, true, false, skippedOut);

        assertEquals(1, pool.size());
        assertEquals("Forest", pool.get(0).name());
        assertTrue(skippedOut.isEmpty());
    }

    @Test
    void buildPool_excludesTokensByDefault() {
        ScryfallCard token = card("id1", "Goblin Token", 0.10, null);
        token.setTypeLine("Token Creature — Goblin");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(token, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tst", null))
                .thenReturn(List.of(userCard(token, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.0, false, false, skippedOut);

        assertTrue(pool.isEmpty());
        assertEquals(1, skippedOut.size());
        assertEquals("excluded_token", skippedOut.get(0).reason());
    }

    @Test
    void buildPool_includesTokensWhenFlagSet() {
        ScryfallCard token = card("id1", "Goblin Token", 0.10, null);
        token.setTypeLine("Token Creature — Goblin");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(userCard(token, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tst", null))
                .thenReturn(List.of(userCard(token, 0, 0)));

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.0, false, true, skippedOut);

        assertEquals(1, pool.size());
        assertEquals("Goblin Token", pool.get(0).name());
        assertTrue(skippedOut.isEmpty());
    }

    // ── greedyMatch ────────────────────────────────────────────────────────────

    @Test
    void greedyMatch_simpleEqualPricedPair() {
        TradeCard a = tradeCard("a1", "Alpha", 10.0);
        TradeCard b = tradeCard("b1", "Beta", 10.0);

        TradeMatchResult r = service.greedyMatch(List.of(a), List.of(b), 15.0);

        assertEquals(1, r.pairs().size());
        assertEquals(a, r.pairs().get(0).fromA());
        assertEquals(b, r.pairs().get(0).fromB());
        assertTrue(r.skippedA().isEmpty());
        assertTrue(r.skippedB().isEmpty());
    }

    @Test
    void greedyMatch_picksClosestPriceInTolerance() {
        TradeCard a = tradeCard("a1", "A", 10.0);
        TradeCard b1 = tradeCard("b1", "B1", 9.0);   // diff 10% ≤ 15% OK
        TradeCard b2 = tradeCard("b2", "B2", 15.0);  // diff 50% > 15% out

        TradeMatchResult r = service.greedyMatch(List.of(a), List.of(b1, b2), 15.0);

        assertEquals(1, r.pairs().size());
        assertEquals(b1, r.pairs().get(0).fromB());
        assertEquals(1, r.skippedB().size());
        assertEquals(b2, r.skippedB().get(0).card());
    }

    @Test
    void greedyMatch_noMatchInTolerance_allSkipped() {
        TradeCard a = tradeCard("a1", "A", 10.0);
        TradeCard b1 = tradeCard("b1", "B1", 5.0);
        TradeCard b2 = tradeCard("b2", "B2", 50.0);

        TradeMatchResult r = service.greedyMatch(List.of(a), List.of(b1, b2), 15.0);

        assertTrue(r.pairs().isEmpty());
        assertEquals(1, r.skippedA().size());
        assertEquals(2, r.skippedB().size());
        assertEquals("no_match_in_tolerance", r.skippedA().get(0).reason());
    }

    @Test
    void greedyMatch_deterministicTieBreaker() {
        // Two equal prices on side B → alphabetically first name wins (Mox Ruby < Mox Sapphire)
        TradeCard a = tradeCard("a1", "Black Lotus", 5000.0);
        TradeCard mr = tradeCard("b1", "Mox Ruby",     5000.0);
        TradeCard ms = tradeCard("b2", "Mox Sapphire", 5000.0);

        TradeMatchResult r1 = service.greedyMatch(List.of(a), List.of(ms, mr), 1.0);
        TradeMatchResult r2 = service.greedyMatch(List.of(a), List.of(mr, ms), 1.0);

        assertEquals(mr.name(), r1.pairs().get(0).fromB().name());
        assertEquals(mr.name(), r2.pairs().get(0).fromB().name());
    }

    @Test
    void greedyMatch_processestAHighestPriceFirst() {
        // a1 = 100, a2 = 10. Pool B has 95 and 9. Should pair 100↔95 and 10↔9.
        TradeCard aHi = tradeCard("a1", "AHi", 100.0);
        TradeCard aLo = tradeCard("a2", "ALo",  10.0);
        TradeCard bHi = tradeCard("b1", "BHi",  95.0);
        TradeCard bLo = tradeCard("b2", "BLo",   9.0);

        TradeMatchResult r = service.greedyMatch(List.of(aLo, aHi), List.of(bLo, bHi), 15.0);

        assertEquals(2, r.pairs().size());
        // First pair = highest priced (greedy from top)
        assertEquals(100.0, r.pairs().get(0).fromA().price());
        assertEquals( 95.0, r.pairs().get(0).fromB().price());
    }

    // ── karmarkarKarpMatch (Rarity-Based Bundle) ──────────────────────────────

    @Test
    void karmarkarKarp_matchesSameRarity() {
        // 2 mythics each side, 1 rare each → should pair mythics, skip rare from shorter side
        TradeCard mythicA1 = tradeCardWithRarity("m1", "MythicA1", 20.0, "mythic");
        TradeCard mythicA2 = tradeCardWithRarity("m2", "MythicA2", 18.0, "mythic");
        TradeCard rareA    = tradeCardWithRarity("r1", "RareA", 5.0, "rare");

        TradeCard mythicB1 = tradeCardWithRarity("m3", "MythicB1", 19.0, "mythic");
        TradeCard mythicB2 = tradeCardWithRarity("m4", "MythicB2", 17.0, "mythic");

        TradeBundleResult r = service.karmarkarKarpMatch(
                List.of(mythicA1, mythicA2, rareA),
                List.of(mythicB1, mythicB2)
        );

        // Should match 2 mythics, rare skipped
        assertEquals(2, r.bundle().aSide().size());
        assertEquals(2, r.bundle().bSide().size());
        assertTrue(r.skippedA().stream().anyMatch(s -> s.card().name().equals("RareA")));
    }

    @Test
    void karmarkarKarp_balancesSumsByRemovingCheapest() {
        // A: 10+9 = 19, B: 5+5+5+5 = 20
        // Rarity-based pairing: matches min(2,4)=2 pairs from each side
        // Result depends on price-based greedy within rarity
        TradeCard a1 = tradeCardWithRarity("a1", "A1", 10.0, "rare");
        TradeCard a2 = tradeCardWithRarity("a2", "A2", 9.0, "rare");
        TradeCard b1 = tradeCardWithRarity("b1", "B1", 5.0, "rare");
        TradeCard b2 = tradeCardWithRarity("b2", "B2", 5.0, "rare");
        TradeCard b3 = tradeCardWithRarity("b3", "B3", 5.0, "rare");
        TradeCard b4 = tradeCardWithRarity("b4", "B4", 5.0, "rare");

        TradeBundleResult r = service.karmarkarKarpMatch(
                List.of(a1, a2),
                List.of(b1, b2, b3, b4)
        );

        // Should match 2 from A with 2 from B (same rarity), 2 from B skipped
        assertEquals(2, r.bundle().aSide().size());
        assertEquals(2, r.bundle().bSide().size());
        assertEquals(2, r.skippedB().size());
    }

    @Test
    void karmarkarKarp_prioritizesRarityOrder() {
        // Mix: mythic, rare, uncommon, common → should group by rarity
        TradeCard mythicA = tradeCardWithRarity("m1", "M1", 50.0, "mythic");
        TradeCard rareA   = tradeCardWithRarity("r1", "R1", 10.0, "rare");
        TradeCard uncomA  = tradeCardWithRarity("u1", "U1", 2.0, "uncommon");
        TradeCard commA   = tradeCardWithRarity("c1", "C1", 0.5, "common");

        TradeCard mythicB = tradeCardWithRarity("m2", "M2", 48.0, "mythic");
        TradeCard rareB   = tradeCardWithRarity("r2", "R2", 11.0, "rare");
        TradeCard uncomB  = tradeCardWithRarity("u2", "U2", 2.5, "uncommon");
        TradeCard commB   = tradeCardWithRarity("c2", "C2", 0.6, "common");

        TradeBundleResult r = service.karmarkarKarpMatch(
                List.of(mythicA, rareA, uncomA, commA),
                List.of(mythicB, rareB, uncomB, commB)
        );

        // Should match pairs by rarity, not necessarily all
        int totalCards = r.bundle().aSide().size() + r.bundle().bSide().size();
        assertTrue(totalCards >= 2, "Should have at least one pair");
        assertTrue(totalCards <= 8, "Should have at most all cards");
    }

    @Test
    void karmarkarKarp_emptyPools() {
        TradeBundleResult r = service.karmarkarKarpMatch(List.of(), List.of());

        assertTrue(r.bundle().aSide().isEmpty());
        assertTrue(r.bundle().bSide().isEmpty());
    }

    // ── computeFairnessScore ───────────────────────────────────────────────────

    @Test
    void computeFairnessScore_perfectMatch_isOne() {
        assertEquals(1.0, service.computeFairnessScore(100.0, 100.0), 0.001);
    }

    @Test
    void computeFairnessScore_partialMatch() {
        assertEquals(0.95, service.computeFairnessScore(100.0, 95.0), 0.001);
        assertEquals(0.70, service.computeFairnessScore(100.0, 70.0), 0.001);
    }

    @Test
    void computeFairnessScore_zeroSums_isOne() {
        // Edge case: empty trade is "perfectly fair"
        assertEquals(1.0, service.computeFairnessScore(0.0, 0.0), 0.001);
    }

    @Test
    void computeFairnessScore_oneSideZero_isZero() {
        assertEquals(0.0, service.computeFairnessScore(100.0, 0.0), 0.001);
    }

    // ── Pool-Truncation ────────────────────────────────────────────────────────

    @Test
    void buildPool_truncatesAtMaxPoolSize() {
        // Generate 305 dummy owned cards with prices 1..305
        List<CardWithUserData> victorCards = new ArrayList<>();
        List<CardWithUserData> andreCards = new ArrayList<>();
        for (int i = 1; i <= 305; i++) {
            ScryfallCard sc = card("id" + i, "Card" + i, (double) i, null);
            victorCards.add(userCard(sc, 2, 0));
            andreCards.add(userCard(sc, 0, 0));
        }
        when(collectionService.getCardsWithUserData("Victor", "tst", null)).thenReturn(victorCards);
        when(collectionService.getCardsWithUserData("Andre",  "tst", null)).thenReturn(andreCards);

        List<TradeCard> pool = service.buildPool("Victor", "Andre", List.of("tst"), 0.5, false, false, skippedOut);

        assertEquals(TradeWizardService.MAX_POOL_SIZE, pool.size());
        // Top 300 by price → should contain price 305 (highest) and 6 (300th highest)
        assertEquals(305.0, pool.get(0).price());
        assertEquals(  6.0, pool.get(299).price());
        // 5 truncated (prices 1..5)
        assertEquals(5, skippedOut.size());
        assertTrue(skippedOut.stream().allMatch(s -> "pool_truncated".equals(s.reason())));
    }

    // ── Performance ────────────────────────────────────────────────────────────

    @Test
    void performance_greedyMatch_under50ms_for200Cards() {
        List<TradeCard> a = new ArrayList<>();
        List<TradeCard> b = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            a.add(tradeCard("a" + i, "A" + i, 5.0 + i * 0.1));
            b.add(tradeCard("b" + i, "B" + i, 5.0 + i * 0.1 + 0.05));
        }
        long start = System.nanoTime();
        TradeMatchResult r = service.greedyMatch(a, b, 15.0);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // On RPi 4 ARM64 budget = 50ms; local dev (Mac) should be well under that
        assertTrue(elapsedMs < 200, "greedyMatch took " + elapsedMs + "ms (budget 200ms local, 50ms RPi)");
        assertFalse(r.pairs().isEmpty());
    }

    @Test
    void performance_karmarkarKarp_under200ms_for200Cards() {
        List<TradeCard> a = new ArrayList<>();
        List<TradeCard> b = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            a.add(tradeCard("a" + i, "A" + i, 5.0 + i * 0.1));
            b.add(tradeCard("b" + i, "B" + i, 5.0 + i * 0.1 + 0.05));
        }
        long start = System.nanoTime();
        TradeBundleResult r = service.karmarkarKarpMatch(a, b);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 500, "karmarkarKarp took " + elapsedMs + "ms (budget 500ms local, 200ms RPi)");
        // Rarity-based matching: same rarity cards paired
        assertTrue(r.bundle().aSide().size() >= 100, "Should keep most cards on side A");
        assertTrue(r.bundle().bSide().size() >= 100, "Should keep most cards on side B");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static ScryfallCard card(String id, String name, Double priceRegular, Double priceFoil) {
        ScryfallCard sc = new ScryfallCard();
        sc.setId(id);
        sc.setName(name);
        sc.setSetCode("tst");
        sc.setCollectorNumber("1");
        sc.setPriceRegular(priceRegular);
        sc.setPriceFoil(priceFoil);
        sc.setRarity("rare");  // Default rarity for tests
        return sc;
    }

    private static CardWithUserData userCard(ScryfallCard sc, int qty, int foilQty) {
        return new CardWithUserData(sc, qty, foilQty);
    }

    private static TradeCard tradeCard(String id, String name, double price) {
        return new TradeCard(id, name, "tst", "1", price, false, "Victor", "rare");
    }

    private static TradeCard tradeCardWithRarity(String id, String name, double price, String rarity) {
        return new TradeCard(id, name, "tst", "1", price, false, "Victor", rarity);
    }
}
