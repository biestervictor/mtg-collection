package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final UserCardRepository userCardRepository;
    private final ImportHistoryRepository importHistoryRepository;
    private final ScryfallService scryfallService;

    public StatisticsService(UserCardRepository userCardRepository,
                          ImportHistoryRepository importHistoryRepository,
                          ScryfallService scryfallService) {
        this.userCardRepository = userCardRepository;
        this.importHistoryRepository = importHistoryRepository;
        this.scryfallService = scryfallService;
    }

    public Map<String, UserStatistics> getStatisticsForAllUsers() {
        List<String> users = userCardRepository.findAll().stream()
                .map(UserCard::getUser)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, UserStatistics> statsMap = new LinkedHashMap<>();
        
        for (String user : users) {
            statsMap.put(user, getStatisticsForUser(user));
        }
        
        return statsMap;
    }

    public UserStatistics getStatisticsForUser(String user) {
        List<UserCard> userCards = userCardRepository.findByUser(user);
        List<com.mtg.collection.model.ImportHistory> imports = importHistoryRepository.findByUserOrderByImportedAtDesc(user);
        
        UserStatistics stats = new UserStatistics();
        stats.setUser(user);
        
        stats.setTotalUploads(imports.size());
        
        if (!imports.isEmpty() && imports.get(0).getImportedAt() != null) {
            stats.setLastUpload(imports.get(0).getImportedAt().toLocalDate());
        }
        
        int totalCards = userCards.stream().mapToInt(c -> c.getQuantity()).sum();
        stats.setTotalCards(totalCards);
        
        double totalValue = userCards.stream()
                .mapToDouble(c -> c.getPrice() * c.getQuantity())
                .sum();
        stats.setTotalValue(totalValue);
        
        List<CardWithPrice> expensiveCards = userCards.stream()
                .filter(c -> c.getPrice() > 0)
                .map(c -> new CardWithPrice(c.getName(), c.getSetCode(), c.getPrice() * c.getQuantity(), c.getPrice()))
                .sorted(Comparator.comparing(CardWithPrice::getTotalPrice).reversed())
                .limit(30)
                .collect(Collectors.toList());
        stats.setMostExpensiveCards(expensiveCards);
        
        Map<String, Integer> setCounts = userCards.stream()
                .collect(Collectors.groupingBy(
                        UserCard::getSetCode,
                        Collectors.summingInt(c -> c.getQuantity())
                ));
        
        List<SetCount> topSets = setCounts.entrySet().stream()
                .map(e -> new SetCount(e.getKey(), e.getValue().longValue()))
                .sorted(Comparator.comparing(SetCount::getCount).reversed())
                .limit(5)
                .collect(Collectors.toList());
        stats.setTopSetsByCount(topSets);
        
        Map<String, Double> setValues = userCards.stream()
                .collect(Collectors.groupingBy(
                        UserCard::getSetCode,
                        Collectors.summingDouble(c -> c.getPrice() * c.getQuantity())
                ));
        
        Map<String, ScryfallSet> setMap = scryfallService.getAllSets(false).stream()
                .collect(Collectors.toMap(ScryfallSet::getSetCode, s -> s, (a, b) -> a));
        
        List<SetValue> topSetsByValue = setValues.entrySet().stream()
                .map(e -> {
                    ScryfallSet set = setMap.get(e.getKey());
                    int totalCardsInSet = set != null ? set.getCardCount() : 0;
                    int ownedCards = setCounts.getOrDefault(e.getKey(), 0);
                    return new SetValue(e.getKey(), e.getValue(), totalCardsInSet, ownedCards);
                })
                .sorted(Comparator.comparing(SetValue::getValue).reversed())
                .collect(Collectors.toList());
        stats.setTopSetsByValue(topSetsByValue);
        
        List<SetCompletion> completeSets = new ArrayList<>();
        List<SetCompletion> nearCompleteSets = new ArrayList<>();
        
        for (SetValue sv : topSetsByValue) {
            int ownedCards = sv.getOwnedCards();
            int totalCardsInSet = sv.getTotalCardsInSet();
            if (totalCardsInSet > 0) {
                double percentage = (ownedCards * 100.0) / totalCardsInSet;
                SetCompletion sc = new SetCompletion(sv.getSetCode(), ownedCards, totalCardsInSet, percentage);
                if (ownedCards >= totalCards) {
                    completeSets.add(sc);
                } else if (percentage >= 90) {
                    nearCompleteSets.add(sc);
                }
            }
        }
        
        completeSets.sort(Comparator.comparing(SetCompletion::getOwnedCards).reversed());
        nearCompleteSets.sort(Comparator.comparing(SetCompletion::getPercentage).reversed());
        
        stats.setCompleteSets(completeSets);
        stats.setNearCompleteSets(nearCompleteSets.stream().limit(10).collect(Collectors.toList()));
        
        calculateDailyChanges(user, stats);
        
        return stats;
    }

    private void calculateDailyChanges(String user, UserStatistics stats) {
        List<UserCard> userCards = userCardRepository.findByUser(user);
        
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        double todayValue = userCards.stream()
                .filter(c -> c.getPriceUpdatedAt() != null && c.getPriceUpdatedAt().equals(today))
                .mapToDouble(c -> c.getPrice() * c.getQuantity())
                .sum();
        
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
                        UserCard::getPrice
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
        
        stats.setTopWinners(winners.stream().limit(10).collect(Collectors.toList()));
        stats.setTopLosers(losers.stream().limit(10).collect(Collectors.toList()));
    }

    public static class CardWithPrice {
        private String name;
        private String setCode;
        private double totalPrice;
        private double pricePerCard;

        public CardWithPrice(String name, String setCode, double totalPrice, double pricePerCard) {
            this.name = name;
            this.setCode = setCode;
            this.totalPrice = totalPrice;
            this.pricePerCard = pricePerCard;
        }

        public String getName() { return name; }
        public String getSetCode() { return setCode; }
        public double getTotalPrice() { return totalPrice; }
        public double getPricePerCard() { return pricePerCard; }
    }

    public static class SetCount {
        private String setCode;
        private long count;

        public SetCount(String setCode, long count) {
            this.setCode = setCode;
            this.count = count;
        }

        public String getSetCode() { return setCode; }
        public long getCount() { return count; }
    }

    public static class SetValue {
        private String setCode;
        private double value;
        private int totalCardsInSet;
        private int ownedCards;

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
    }

    public static class SetCompletion {
        private String setCode;
        private int ownedCards;
        private int totalCards;
        private double percentage;

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