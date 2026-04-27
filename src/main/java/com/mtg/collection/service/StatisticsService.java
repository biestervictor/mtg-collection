package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    /**
     * Scryfall set_type values that should not appear in the Set Completion section.
     * Token sets, memorabilia, and mini-game sets are excluded because "completing"
     * them is not a meaningful goal for a physical collection.
     */
    private static final Set<String> EXCLUDED_COMPLETION_SET_TYPES =
            Set.of("token", "memorabilia", "minigame");

    private final UserCardRepository userCardRepository;
    private final ImportHistoryRepository importHistoryRepository;
    private final ScryfallService scryfallService;
    private final ScryfallCardRepository scryfallCardRepository;
    private final MongoTemplate mongoTemplate;

    public StatisticsService(UserCardRepository userCardRepository,
                          ImportHistoryRepository importHistoryRepository,
                          ScryfallService scryfallService,
                          ScryfallCardRepository scryfallCardRepository,
                          MongoTemplate mongoTemplate) {
        this.userCardRepository = userCardRepository;
        this.importHistoryRepository = importHistoryRepository;
        this.scryfallService = scryfallService;
        this.scryfallCardRepository = scryfallCardRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Returns a distinct, sorted list of all users who have cards in the collection. */
    public List<String> getDistinctUsers() {
        return mongoTemplate.findDistinct(new Query(), "user", UserCard.class, String.class)
                .stream().sorted().collect(Collectors.toList());
    }

    public Map<String, UserStatistics> getStatisticsForAllUsers() {
        List<String> users = getDistinctUsers();
        Map<String, UserStatistics> statsMap = new LinkedHashMap<>();
        for (String user : users) {
            statsMap.put(user, getStatisticsForUser(user));
        }
        return statsMap;
    }

    public UserStatistics getStatisticsForUser(String user) {
        List<UserCard> userCards = userCardRepository.findByUser(user);
        List<com.mtg.collection.model.ImportHistory> imports = importHistoryRepository.findByUserOrderByImportedAtDesc(user);

        // Batch-load all Scryfall cards for the user's sets in ONE query
        Map<String, ScryfallCard> sfMap = buildScryfallMap(userCards);

        // Actual distinct-card count per set from the DB (avoids stale ScryfallSet.cardCount)
        Map<String, Integer> actualScryfallCountBySet = new HashMap<>();
        for (ScryfallCard sc : sfMap.values()) {
            if (sc.getSetCode() != null) {
                actualScryfallCountBySet.merge(sc.getSetCode().toLowerCase(), 1, Integer::sum);
            }
        }

        UserStatistics stats = new UserStatistics();
        stats.setUser(user);
        
        stats.setTotalUploads(imports.size());
        
        if (!imports.isEmpty() && imports.get(0).getImportedAt() != null) {
            stats.setLastUpload(imports.get(0).getImportedAt().toLocalDate());
        }
        
        int totalCards = userCards.stream().mapToInt(c -> c.getQuantity()).sum();
        stats.setTotalCards(totalCards);
        
        double totalValue = userCards.stream()
                .mapToDouble(c -> effectivePrice(c, sfMap) * c.getQuantity())
                .sum();
        stats.setTotalValue(totalValue);
        
        List<CardWithPrice> expensiveCards = userCards.stream()
                .map(c -> {
                    double p = effectivePrice(c, sfMap);
                    if (p <= 0) return null;
                    ScryfallCard sf = sfMap.get(c.getSetCode() + "_" + c.getCollectorNumber());
                    String thumb = sf != null ? sf.getThumbnailFront() : null;
                    return new CardWithPrice(c.getName(), c.getSetCode(), p * c.getQuantity(), p, thumb);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CardWithPrice::getTotalPrice).reversed()
                        .thenComparing(Comparator.comparing(CardWithPrice::getPricePerCard).reversed()))
                .limit(100)
                .collect(Collectors.toList());
        stats.setMostExpensiveCards(expensiveCards);
        
        // Total physical card count per set (sum of quantities) – used for "Top 5 by Count"
        Map<String, Integer> setCounts = userCards.stream()
                .collect(Collectors.groupingBy(
                        UserCard::getSetCode,
                        Collectors.summingInt(UserCard::getQuantity)
                ));

        // Unique card count per set (distinct collector numbers, foil/normal merged)
        // This is the correct basis for set-completion: card 1/453 … 453/453
        Map<String, Integer> setUniqueCardCounts = userCards.stream()
                .collect(Collectors.groupingBy(
                        UserCard::getSetCode,
                        Collectors.collectingAndThen(
                                Collectors.mapping(UserCard::getCollectorNumber, Collectors.toSet()),
                                Set::size)
                ));

        Map<String, ScryfallSet> setMap = scryfallService.getAllSets(false).stream()
                .collect(Collectors.toMap(
                        s -> s.getSetCode().toLowerCase(),
                        s -> s,
                        (a, b) -> a));

        List<SetCount> topSets = setCounts.entrySet().stream()
                .map(e -> {
                    SetCount sc = new SetCount(e.getKey(), e.getValue().longValue());
                    ScryfallSet s = setMap.get(e.getKey().toLowerCase());
                    if (s != null) sc.setIconUrl(s.getIcon());
                    return sc;
                })
                .sorted(Comparator.comparing(SetCount::getCount).reversed())
                .limit(30)
                .collect(Collectors.toList());
        stats.setTopSetsByCount(topSets);
        
        Map<String, Double> setValues = new HashMap<>();
        for (UserCard c : userCards) {
            double p = effectivePrice(c, sfMap);
            setValues.merge(c.getSetCode(), p * c.getQuantity(), Double::sum);
        }
        
        List<SetValue> topSetsByValue = setValues.entrySet().stream()
                .filter(e -> e.getValue() > 0)   // hide sets where all prices are 0
                .filter(e -> !isExcludedFromCompletion(e.getKey(), setMap.get(e.getKey().toLowerCase())))
                .map(e -> {
                    ScryfallSet set = setMap.get(e.getKey().toLowerCase());
                    int totalCardsInSet = actualScryfallCountBySet.getOrDefault(
                            e.getKey().toLowerCase(), set != null ? set.getCardCount() : 0);
                    int uniqueOwned = setUniqueCardCounts.getOrDefault(e.getKey(), 0);
                    SetValue sv = new SetValue(e.getKey(), e.getValue(), totalCardsInSet, uniqueOwned);
                    if (set != null) sv.setIconUrl(set.getIcon());
                    return sv;
                })
                .sorted(Comparator.comparing(SetValue::getValue).reversed())
                .collect(Collectors.toList());
        stats.setTopSetsByValue(topSetsByValue);
        
        List<SetCompletion> completeSets = new ArrayList<>();
        List<SetCompletion> nearCompleteSets = new ArrayList<>();
        List<SetCompletion> nearComplete80 = new ArrayList<>();
        List<SetCompletion> nearComplete70 = new ArrayList<>();
        List<SetCompletion> nearComplete60 = new ArrayList<>();
        List<SetCompletion> nearComplete50 = new ArrayList<>();

        // Use ALL sets where the user owns cards – not just those with price > 0
        for (Map.Entry<String, Integer> entry : setUniqueCardCounts.entrySet()) {
            String setCode       = entry.getKey();
            int    uniqueOwned   = entry.getValue();
            ScryfallSet s        = setMap.get(setCode.toLowerCase());

            // Skip token sets, memorabilia, minigame sets (by setType or 4-char 't' heuristic)
            if (isExcludedFromCompletion(setCode, s)) continue;

            int totalCardsInSet = actualScryfallCountBySet.getOrDefault(
                    setCode.toLowerCase(), s != null ? s.getCardCount() : 0);
            if (totalCardsInSet > 0) {
                double percentage = (uniqueOwned * 100.0) / totalCardsInSet;
                SetCompletion sc = new SetCompletion(setCode, uniqueOwned, totalCardsInSet, percentage);
                if (s != null) sc.setIconUrl(s.getIcon());
                if (uniqueOwned >= totalCardsInSet) {
                    completeSets.add(sc);
                } else if (percentage >= 90) {
                    nearCompleteSets.add(sc);
                } else if (percentage >= 80) {
                    nearComplete80.add(sc);
                } else if (percentage >= 70) {
                    nearComplete70.add(sc);
                } else if (percentage >= 60) {
                    nearComplete60.add(sc);
                } else if (percentage >= 50) {
                    nearComplete50.add(sc);
                }
            }
        }
        
        completeSets.sort(Comparator.comparing(SetCompletion::getOwnedCards).reversed());
        nearCompleteSets.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        nearComplete80.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        nearComplete70.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        nearComplete60.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        nearComplete50.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        
        stats.setCompleteSets(completeSets);
        stats.setNearCompleteSets(nearCompleteSets.stream().limit(30).collect(Collectors.toList()));
        stats.setNearComplete80(nearComplete80.stream().limit(30).collect(Collectors.toList()));
        stats.setNearComplete70(nearComplete70.stream().limit(30).collect(Collectors.toList()));
        stats.setNearComplete60(nearComplete60.stream().limit(30).collect(Collectors.toList()));
        stats.setNearComplete50(nearComplete50.stream().limit(30).collect(Collectors.toList()));
        
        // Pass already-loaded userCards to avoid a second DB query
        calculateDailyChanges(userCards, stats);
        
        return stats;
    }

    private void calculateDailyChanges(List<UserCard> userCards, UserStatistics stats) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        double yesterdayValue = userCards.stream()
                .filter(c -> c.getPriceUpdatedAt() != null && c.getPriceUpdatedAt().equals(yesterday))
                .mapToDouble(c -> c.getPrice() * c.getQuantity())
                .sum();
        
        double currentValue = userCards.stream()
                .mapToDouble(c -> c.getPrice() * c.getQuantity())
                .sum();
        
        double change = currentValue - yesterdayValue;
        stats.setDailyChange(change);
        
        Map<String, Double> previousPrices = userCards.stream()
                .filter(c -> c.getPriceUpdatedAt() != null && c.getPriceUpdatedAt().isBefore(yesterday))
                .collect(Collectors.toMap(
                        c -> c.getSetCode() + c.getCollectorNumber() + c.isFoil(),
                        UserCard::getPrice,
                        (existing, replacement) -> existing
                ));
        
        List<CardPriceChange> winners = new ArrayList<>();
        List<CardPriceChange> losers = new ArrayList<>();
        
        for (UserCard card : userCards) {
            if (card.getPriceUpdatedAt() != null) {
                String key = card.getSetCode() + card.getCollectorNumber() + card.isFoil();
                Double prevPrice = previousPrices.get(key);
                if (prevPrice != null && Math.abs(prevPrice - card.getPrice()) > 0.01) {
                    double priceChange = (card.getPrice() - prevPrice) * card.getQuantity();
                    CardPriceChange cpc = new CardPriceChange(
                            card.getName(),
                            card.getSetCode(),
                            card.getPrice(),
                            prevPrice,
                            priceChange
                    );
                    if (change > 0) {
                        winners.add(cpc);
                    } else if (change < 0) {
                        losers.add(cpc);
                    }
                }
            }
        }
        
        winners.sort(Comparator.comparing(CardPriceChange::getChange).reversed());
        losers.sort(Comparator.comparing(CardPriceChange::getChange));
        
        stats.setTopWinners(winners.stream().limit(30).collect(Collectors.toList()));
        stats.setTopLosers(losers.stream().limit(30).collect(Collectors.toList()));
    }

    // ── Price enrichment helpers ──────────────────────────────────────────────

    /**
     * Returns true if the set should be excluded from completion/value statistics.
     * Two checks are combined:
     *   1. Explicit setType exclusion (token, memorabilia, minigame) from ScryfallSet
     *   2. Heuristic: 4-char set codes starting with 't' are Scryfall token sets
     *      (e.g. "tone" = tokens for ONE, "ttdm" = tokens for TDM). All legitimate
     *      expansion sets use 2-3 character codes.
     */
    private static boolean isExcludedFromCompletion(String setCode, ScryfallSet s) {
        if (s != null && s.getSetType() != null
                && EXCLUDED_COMPLETION_SET_TYPES.contains(s.getSetType())) return true;
        String lc = setCode.toLowerCase();
        if (lc.length() == 4 && lc.charAt(0) == 't') return true;
        return false;
    }

    /**
     * Batch-loads all ScryfallCards for the given user cards' set codes in a SINGLE
     * MongoDB query (using $in), replacing the previous N-queries-per-set approach.
     */
    private Map<String, ScryfallCard> buildScryfallMap(List<UserCard> userCards) {
        Set<String> setCodes = userCards.stream()
                .map(UserCard::getSetCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, ScryfallCard> sfMap = new HashMap<>();
        // Single batch query instead of one query per set code
        for (ScryfallCard sf : scryfallCardRepository.findBySetCodeIn(setCodes)) {
            sfMap.put(sf.getSetCode() + "_" + sf.getCollectorNumber(), sf);
        }
        return sfMap;
    }

    /**
     * Returns the best available price: UserCard.price if > 0, else ScryfallCard price.
     */
    private double effectivePrice(UserCard c, Map<String, ScryfallCard> sfMap) {
        if (c.getPrice() > 0) return c.getPrice();
        ScryfallCard sf = sfMap.get(c.getSetCode() + "_" + c.getCollectorNumber());
        if (sf == null) return 0.0;
        double p = c.isFoil()
                ? (sf.getPriceFoil()  != null ? sf.getPriceFoil()  : 0.0)
                : (sf.getPriceRegular() != null ? sf.getPriceRegular() : 0.0);
        return p;
    }

    public static class CardWithPrice {
        private String name;
        private String setCode;
        private double totalPrice;
        private double pricePerCard;
        private String thumbnailUrl;

        public CardWithPrice(String name, String setCode, double totalPrice, double pricePerCard) {
            this(name, setCode, totalPrice, pricePerCard, null);
        }

        public CardWithPrice(String name, String setCode, double totalPrice, double pricePerCard, String thumbnailUrl) {
            this.name = name;
            this.setCode = setCode;
            this.totalPrice = totalPrice;
            this.pricePerCard = pricePerCard;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getName() { return name; }
        public String getSetCode() { return setCode; }
        public double getTotalPrice() { return totalPrice; }
        public double getPricePerCard() { return pricePerCard; }
        public String getThumbnailUrl() { return thumbnailUrl; }
    }

    public static class SetCount {
        private String setCode;
        private long count;
        private String iconUrl;

        public SetCount(String setCode, long count) {
            this.setCode = setCode;
            this.count = count;
        }

        public String getSetCode() { return setCode; }
        public long getCount() { return count; }
        public String getIconUrl() { return iconUrl; }
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    }

    public static class SetValue {
        private String setCode;
        private double value;
        private int totalCardsInSet;
        private int ownedCards;
        private String iconUrl;

        public SetValue(String setCode, double value, int totalCardsInSet, int ownedCards) {
            this.setCode = setCode;
            this.value = value;
            this.totalCardsInSet = totalCardsInSet;
            this.ownedCards = ownedCards;
        }

        public String getSetCode() { return setCode; }
        public double getValue() { return value; }
        public int getTotalCardsInSet() { return totalCardsInSet; }
        public int getOwnedCards() { return ownedCards; }
        public String getIconUrl() { return iconUrl; }
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    }

    public static class SetCompletion {
        private String setCode;
        private int ownedCards;
        private int totalCards;
        private double percentage;
        private String iconUrl;

        public SetCompletion(String setCode, int ownedCards, int totalCards, double percentage) {
            this.setCode = setCode;
            this.ownedCards = ownedCards;
            this.totalCards = totalCards;
            this.percentage = percentage;
        }

        public String getSetCode() { return setCode; }
        public int getOwnedCards() { return ownedCards; }
        public int getTotalCards() { return totalCards; }
        public double getPercentage() { return percentage; }
        public String getIconUrl() { return iconUrl; }
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    }

    public static class CardPriceChange {
        private String name;
        private String setCode;
        private double currentPrice;
        private double previousPrice;
        private double change;

        public CardPriceChange(String name, String setCode, double currentPrice, 
                             double previousPrice, double change) {
            this.name = name;
            this.setCode = setCode;
            this.currentPrice = currentPrice;
            this.previousPrice = previousPrice;
            this.change = change;
        }

        public String getName() { return name; }
        public String getSetCode() { return setCode; }
        public double getCurrentPrice() { return currentPrice; }
        public double getPreviousPrice() { return previousPrice; }
        public double getChange() { return change; }
    }
}
