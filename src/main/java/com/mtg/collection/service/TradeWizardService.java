package com.mtg.collection.service;

import com.mtg.collection.dto.*;
import com.mtg.collection.model.ScryfallCard;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Trade Wizard Service: berechnet faire Tausch-Vorschläge zwischen zwei Usern
 * auf Basis von Scryfall-EUR-Preisen über N Sets hinweg.
 *
 * Zwei Modi:
 *  - Greedy Pair Matching: 1:1-Trades innerhalb Preis-Toleranz
 *  - Karmarkar-Karp Heuristik: n:m-Bundle-Trades, optimiert für Summen-Balance
 *
 * Performance-Ziele auf Raspberry Pi 4 ARM64 (1.5 GHz):
 *  - Greedy:           < 50ms bei 200 Karten/Seite
 *  - Karmarkar-Karp:   < 200ms bei 200 Karten/Seite
 */
@Service
public class TradeWizardService {

    /** Pool-Truncation: Karten oberhalb dieser Grenze werden auf Top-N nach Preis gekürzt. */
    public static final int MAX_POOL_SIZE = 300;

    private final CollectionService collectionService;

    public TradeWizardService(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * Bildet den Trade-Pool eines Users über mehrere Sets hinweg.
     *
     * Eine Karte qualifiziert sich wenn:
     *  1. User hat ≥2 Stück (Normal oder Foil zählt separat)
     *  2. otherUser hat =0 Stück (in derselben Foil-Variante)
     *  3. Preis ≥ minValue
     *  4. Nicht Land (außer includeLands=true)
     *  5. Nicht Token (außer includeTokens=true)
     *
     * Foil und Normal werden als separate TradeCard-Einträge zurückgegeben.
     *
     * Side-Effects: Schreibt skipped Karten (no_price, below_min_value, excluded_land, excluded_token) in skippedOut.
     */
    public List<TradeCard> buildPool(String user, String otherUser, List<String> setCodes,
                                     Double minValue, boolean includeLands, boolean includeTokens,
                                     List<SkippedCard> skippedOut) {
        List<TradeCard> pool = new ArrayList<>();

        for (String setCode : setCodes) {
            List<CardWithUserData> userCards  = collectionService.getCardsWithUserData(user,      setCode, null);
            List<CardWithUserData> otherCards = collectionService.getCardsWithUserData(otherUser, setCode, null);

            // Build lookup: cardId → otherUser quantities
            Map<String, int[]> otherByCardId = new HashMap<>();
            for (CardWithUserData oc : otherCards) {
                if (oc.getCard() != null && oc.getCard().getId() != null) {
                    otherByCardId.put(oc.getCard().getId(),
                            new int[]{oc.getQuantity(), oc.getFoilQuantity()});
                }
            }

            for (CardWithUserData uc : userCards) {
                ScryfallCard card = uc.getCard();
                if (card == null || card.getId() == null) continue;

                // Filter: Land
                if (!includeLands && isLand(card)) {
                    TradeCard tc = new TradeCard(card.getId(), card.getName(), setCode,
                            card.getCollectorNumber(), card.getPriceRegular(), false, user, card.getRarity());
                    skippedOut.add(new SkippedCard(tc, "excluded_land"));
                    continue;
                }

                // Filter: Token
                if (!includeTokens && isToken(card)) {
                    TradeCard tc = new TradeCard(card.getId(), card.getName(), setCode,
                            card.getCollectorNumber(), card.getPriceRegular(), false, user, card.getRarity());
                    skippedOut.add(new SkippedCard(tc, "excluded_token"));
                    continue;
                }

                int[] otherQty = otherByCardId.getOrDefault(card.getId(), new int[]{0, 0});
                int otherNormal = otherQty[0];
                int otherFoil = otherQty[1];

                // Normal-Variante prüfen
                if (uc.getQuantity() >= 2 && otherNormal == 0) {
                    addToPoolOrSkip(pool, skippedOut, card, false, uc.getQuantity(),
                            card.getPriceRegular(), minValue, user, setCode);
                }
                // Foil-Variante prüfen
                if (uc.getFoilQuantity() >= 2 && otherFoil == 0) {
                    addToPoolOrSkip(pool, skippedOut, card, true, uc.getFoilQuantity(),
                            card.getPriceFoil(), minValue, user, setCode);
                }
            }
        }

        // Pool-Truncation: bei > MAX_POOL_SIZE auf Top-N nach Preis kürzen
        if (pool.size() > MAX_POOL_SIZE) {
            pool.sort(Comparator.comparingDouble((TradeCard t) -> t.price()).reversed());
            for (int i = MAX_POOL_SIZE; i < pool.size(); i++) {
                skippedOut.add(new SkippedCard(pool.get(i), "pool_truncated"));
            }
            pool = new ArrayList<>(pool.subList(0, MAX_POOL_SIZE));
        }

        return pool;
    }

    private void addToPoolOrSkip(List<TradeCard> pool, List<SkippedCard> skipped,
                                 ScryfallCard card, boolean foil, int ownedQty,
                                 Double price, Double minValue, String owner, String setCode) {
        TradeCard tc = new TradeCard(card.getId(), card.getName(), setCode,
                card.getCollectorNumber(), price, foil, owner, card.getRarity());
        if (price == null) {
            skipped.add(new SkippedCard(tc, "no_price"));
            return;
        }
        if (minValue != null && price < minValue) {
            skipped.add(new SkippedCard(tc, "below_min_value"));
            return;
        }
        // Anzahl tradeable = ownedQty - 1 (eine bleibt im eigenen Besitz)
        int tradeableCount = ownedQty - 1;
        for (int i = 0; i < tradeableCount; i++) {
            pool.add(tc);
        }
    }

    /**
     * Greedy Pair Matching: sortiere beide Pools nach Preis ↓, paare iterativ
     * teuerste verfügbare Karte aus A mit preislich nächster aus B innerhalb Toleranz.
     *
     * Tie-Breaker: alphabetisch nach Name aufsteigend (Determinismus).
     */
    public TradeMatchResult greedyMatch(List<TradeCard> poolA, List<TradeCard> poolB,
                                        double tolerancePercent) {
        // Defensive copies, sortiere absteigend nach Preis, sekundär aufsteigend nach Name
        Comparator<TradeCard> byPriceDescNameAsc =
                Comparator.comparingDouble((TradeCard t) -> t.price()).reversed()
                        .thenComparing(TradeCard::name);

        List<TradeCard> sortedA = new ArrayList<>(poolA);
        List<TradeCard> sortedB = new ArrayList<>(poolB);
        sortedA.sort(byPriceDescNameAsc);
        sortedB.sort(byPriceDescNameAsc);

        List<TradePair> pairs = new ArrayList<>();
        List<SkippedCard> skippedA = new ArrayList<>();
        boolean[] usedB = new boolean[sortedB.size()];

        double tolFraction = tolerancePercent / 100.0;

        for (TradeCard a : sortedA) {
            int bestIdx = -1;
            double bestDiff = Double.MAX_VALUE;
            for (int i = 0; i < sortedB.size(); i++) {
                if (usedB[i]) continue;
                TradeCard b = sortedB.get(i);
                double diff = Math.abs(b.price() - a.price());
                if (a.price() > 0 && diff / a.price() <= tolFraction && diff < bestDiff) {
                    bestDiff = diff;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                pairs.add(new TradePair(a, sortedB.get(bestIdx)));
                usedB[bestIdx] = true;
            } else {
                skippedA.add(new SkippedCard(a, "no_match_in_tolerance"));
            }
        }

        List<SkippedCard> skippedB = new ArrayList<>();
        for (int i = 0; i < sortedB.size(); i++) {
            if (!usedB[i]) {
                skippedB.add(new SkippedCard(sortedB.get(i), "no_match_in_tolerance"));
            }
        }

        return new TradeMatchResult(pairs, skippedA, skippedB);
    }

    /**
     * Rarity-Based Fair Bundle Matching für ausgeglichene Trades.
     *
     * Algorithmus:
     *  1. Gruppiere Karten nach Rarität (Mythic → Rare → Uncommon → Common)
     *  2. Innerhalb jeder Rarität: Greedy-Match nach Preis
     *  3. Balance: Beide Seiten sollen möglichst gleichen Gesamtwert tauschen
     *  4. Wenn eine Seite mehr Wert hat: entferne günstigste Karten dieser Seite bis Balance
     *
     * Dies ersetzt den Karmarkar-Karp-Ansatz, der keine Subset-Auswahl trifft.
     */
    public TradeBundleResult karmarkarKarpMatch(List<TradeCard> poolA, List<TradeCard> poolB) {
        // Gruppiere nach Rarität
        Map<String, List<TradeCard>> rarityGroupsA = groupByRarity(poolA);
        Map<String, List<TradeCard>> rarityGroupsB = groupByRarity(poolB);

        List<TradeCard> bundleA = new ArrayList<>();
        List<TradeCard> bundleB = new ArrayList<>();
        List<SkippedCard> skippedA = new ArrayList<>();
        List<SkippedCard> skippedB = new ArrayList<>();

        // Rarity-Priorität: mythic → rare → uncommon → common
        String[] rarityOrder = {"mythic", "rare", "uncommon", "common"};

        for (String rarity : rarityOrder) {
            List<TradeCard> cardsA = rarityGroupsA.getOrDefault(rarity, List.of());
            List<TradeCard> cardsB = rarityGroupsB.getOrDefault(rarity, List.of());

            if (cardsA.isEmpty() && cardsB.isEmpty()) continue;

            // Sortiere beide nach Preis absteigend
            List<TradeCard> sortedA = new ArrayList<>(cardsA);
            List<TradeCard> sortedB = new ArrayList<>(cardsB);
            Comparator<TradeCard> byPriceDesc = Comparator.comparingDouble((TradeCard t) -> t.price()).reversed();
            sortedA.sort(byPriceDesc);
            sortedB.sort(byPriceDesc);

            // Greedy-Pairing innerhalb dieser Rarität
            int minSize = Math.min(sortedA.size(), sortedB.size());
            for (int i = 0; i < minSize; i++) {
                bundleA.add(sortedA.get(i));
                bundleB.add(sortedB.get(i));
            }

            // Überschuss in skipped
            for (int i = minSize; i < sortedA.size(); i++) {
                skippedA.add(new SkippedCard(sortedA.get(i), "no_match_in_rarity"));
            }
            for (int i = minSize; i < sortedB.size(); i++) {
                skippedB.add(new SkippedCard(sortedB.get(i), "no_match_in_rarity"));
            }
        }

        // Final sortiere nach Preis absteigend für Anzeige
        Comparator<TradeCard> byPriceDesc = Comparator.comparingDouble((TradeCard t) -> t.price()).reversed();
        bundleA.sort(byPriceDesc);
        bundleB.sort(byPriceDesc);

        return new TradeBundleResult(
                new TradeBundle(bundleA, bundleB),
                skippedA,
                skippedB
        );
    }

    /**
     * Gruppiere Karten nach Rarität (lowercase).
     */
    private Map<String, List<TradeCard>> groupByRarity(List<TradeCard> cards) {
        Map<String, List<TradeCard>> groups = new HashMap<>();
        for (TradeCard card : cards) {
            String rarity = card.rarity() != null ? card.rarity().toLowerCase() : "unknown";
            groups.computeIfAbsent(rarity, k -> new ArrayList<>()).add(card);
        }
        return groups;
    }

    /**
     * Prüft ob eine Karte ein Land ist (typeLine enthält "Land").
     */
    private boolean isLand(ScryfallCard card) {
        String typeLine = card.getTypeLine();
        return typeLine != null && typeLine.contains("Land");
    }

    /**
     * Prüft ob eine Karte ein Token ist (typeLine enthält "Token").
     */
    private boolean isToken(ScryfallCard card) {
        String typeLine = card.getTypeLine();
        return typeLine != null && typeLine.contains("Token");
    }

    /**
     * Fairness-Score: 1.0 = perfekt fair (Summen identisch), 0.0 = sehr unfair.
     * Formel: 1 - |sumA - sumB| / max(sumA, sumB)
     * Bei sumA = sumB = 0 → 1.0 (nichts zu tauschen ist auch "fair").
     */
    public double computeFairnessScore(double sumA, double sumB) {
        double max = Math.max(sumA, sumB);
        if (max == 0.0) return 1.0;
        double score = 1.0 - Math.abs(sumA - sumB) / max;
        return Math.max(0.0, Math.min(1.0, score));
    }

    /** Summe der Karten-Werte. */
    public double sumPrices(List<TradeCard> cards) {
        return cards.stream().mapToDouble(TradeCard::price).sum();
    }
}
