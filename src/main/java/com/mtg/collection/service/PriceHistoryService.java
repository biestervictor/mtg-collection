package com.mtg.collection.service;

import com.mtg.collection.model.PriceHistory;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.model.UserDeck;
import com.mtg.collection.repository.PriceHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.ScryfallSetRepository;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages daily price snapshots for owned cards and computes winners/losers
 * by comparing the two most recent snapshots.
 */
@Service
public class PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);

    /** Days of history to retain. Older snapshots are pruned on each new snapshot. */
    private static final int RETENTION_DAYS = 90;

    private final PriceHistoryRepository priceHistoryRepository;
    private final UserCardRepository     userCardRepository;
    private final ScryfallCardRepository scryfallCardRepository;
    private final ScryfallSetRepository  scryfallSetRepository;
    private final UserDeckRepository     userDeckRepository;

    public PriceHistoryService(PriceHistoryRepository priceHistoryRepository,
                               UserCardRepository     userCardRepository,
                               ScryfallCardRepository scryfallCardRepository,
                               ScryfallSetRepository  scryfallSetRepository,
                               UserDeckRepository     userDeckRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
        this.userCardRepository     = userCardRepository;
        this.scryfallCardRepository = scryfallCardRepository;
        this.scryfallSetRepository  = scryfallSetRepository;
        this.userDeckRepository     = userDeckRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves today's Scryfall prices for every card owned by any user.
     * Uses deterministic IDs (setCode_cn_date) so repeated calls on the same
     * day are idempotent (upsert). Also prunes history older than
     * {@link #RETENTION_DAYS} days.
     *
     * @return number of card snapshots written
     */
    public int snapshotOwnedCardPrices() {
        LocalDate today = LocalDate.now();
        log.info("Taking price snapshot for {}", today);

        List<UserCard> allUserCards = userCardRepository.findAll();
        if (allUserCards.isEmpty()) {
            log.info("No user cards found; skipping price snapshot");
            return 0;
        }

        // Collect distinct lowercase-setCode + collectorNumber pairs and a card name for each
        Set<String>           setCodes      = new HashSet<>();
        Map<String, String>   nameByKey     = new LinkedHashMap<>();
        for (UserCard uc : allUserCards) {
            String lc  = uc.getSetCode().toLowerCase();
            String key = lc + "_" + uc.getCollectorNumber();
            if (nameByKey.putIfAbsent(key, uc.getName()) == null) {
                setCodes.add(lc);
            }
        }

        // Batch-load Scryfall data for all owned sets
        List<ScryfallCard>     sfCards = scryfallCardRepository.findBySetCodeIn(setCodes);
        Map<String, ScryfallCard> sfMap = new HashMap<>();
        for (ScryfallCard sc : sfCards) {
            sfMap.putIfAbsent(sc.getSetCode().toLowerCase() + "_" + sc.getCollectorNumber(), sc);
        }

        List<PriceHistory> toSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : nameByKey.entrySet()) {
            String     key  = entry.getKey();
            String     name = entry.getValue();
            ScryfallCard sc = sfMap.get(key);
            if (sc == null) continue;

            // key format: "setCode_cn"  (setCode has no underscore restriction but cn might be "150a" etc.)
            int sep = key.indexOf('_');
            String setCode = key.substring(0, sep);
            String cn      = key.substring(sep + 1);

            toSave.add(new PriceHistory(setCode, cn, name, sc.getThumbnailFront(),
                    today, sc.getPriceRegular(), sc.getPriceFoil()));
        }

        priceHistoryRepository.saveAll(toSave);

        // Prune old history
        priceHistoryRepository.deleteByDateBefore(today.minusDays(RETENTION_DAYS));

        log.info("Snapshotted {} card prices on {}", toSave.size(), today);
        return toSave.size();
    }

    /**
     * Returns the top {@code limit} winners (largest positive % change) among cards
     * with current price &gt;= {@code minPrice}, comparing the two most recent snapshots.
     */
    public List<PriceChange> getTopWinners(int limit, double minPrice) {
        return computeChanges(limit, minPrice, true);
    }

    /**
     * Returns the top {@code limit} losers (largest negative % change) among cards
     * with current price &gt;= {@code minPrice}, comparing the two most recent snapshots.
     */
    public List<PriceChange> getTopLosers(int limit, double minPrice) {
        return computeChanges(limit, minPrice, false);
    }

    /**
     * Returns one {@link SetSummary} for each set that has tracked cards
     * (current price &gt;= minPrice) with at least one price movement, sorted by
     * activity (most winners + losers first).
     */
    public List<SetSummary> getSetSummaries(double minPrice, int topN) {
        List<LocalDate> dates = getTwoLatestDates();
        if (dates.size() < 2) return Collections.emptyList();

        Map<String, PriceHistory> latestMap = buildMap(priceHistoryRepository.findByDate(dates.get(0)));
        Map<String, PriceHistory> prevMap   = buildMap(priceHistoryRepository.findByDate(dates.get(1)));

        // Set code → display name
        Map<String, String> setNames = scryfallSetRepository.findAll().stream()
                .collect(Collectors.toMap(
                        s -> s.getSetCode().toLowerCase(),
                        ScryfallSet::getName,
                        (a, b) -> a));

        // Accumulate PriceChanges grouped by setCode
        Map<String, List<PriceChange>> bySet = new LinkedHashMap<>();
        for (PriceHistory lat : latestMap.values()) {
            if (lat.getPriceRegular() == null || lat.getPriceRegular() < minPrice) continue;
            PriceHistory prev = prevMap.get(lat.getSetCode() + "_" + lat.getCollectorNumber());
            if (prev == null || prev.getPriceRegular() == null || prev.getPriceRegular() < 0.001) continue;

            double oldP = prev.getPriceRegular();
            double newP = lat.getPriceRegular();
            double abs  = newP - oldP;
            double pct  = abs / oldP * 100.0;

            bySet.computeIfAbsent(lat.getSetCode(), k -> new ArrayList<>())
                 .add(new PriceChange(lat.getCardName(), lat.getSetCode(),
                         lat.getCollectorNumber(), lat.getThumbnailUrl(),
                         oldP, newP, abs, pct, 0, "", false));
        }

        List<SetSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<PriceChange>> e : bySet.entrySet()) {
            String setCode = e.getKey();
            List<PriceChange> all = e.getValue();

            List<PriceChange> winners = all.stream()
                    .filter(c -> c.absoluteChange > 0.0)
                    .sorted(Comparator.comparingDouble(PriceChange::getAbsoluteChange).reversed())
                    .limit(topN)
                    .collect(Collectors.toList());

            List<PriceChange> losers = all.stream()
                    .filter(c -> c.absoluteChange < 0.0)
                    .sorted(Comparator.comparingDouble(PriceChange::getAbsoluteChange))
                    .limit(topN)
                    .collect(Collectors.toList());

            if (!winners.isEmpty() || !losers.isEmpty()) {
                String setName = setNames.getOrDefault(setCode, setCode.toUpperCase());
                summaries.add(new SetSummary(setCode, setName, all.size(), winners, losers));
            }
        }

        summaries.sort(Comparator.comparingInt(
                s -> -(s.getTopWinners().size() + s.getTopLosers().size())));
        return summaries;
    }

    /**
     * Returns the full price history for one card, sorted oldest → newest.
     */
    public List<PriceHistory> getPriceHistory(String setCode, String collectorNumber) {
        return priceHistoryRepository.findBySetCodeAndCollectorNumberOrderByDateAsc(
                setCode.toLowerCase(), collectorNumber);
    }

    /**
     * Returns the date of the most recent snapshot, or {@code null} if none exists.
     */
    public LocalDate getLastSnapshotDate() {
        List<LocalDate> dates = getTwoLatestDates();
        return dates.isEmpty() ? null : dates.get(0);
    }

    /**
     * Returns the number of distinct cards in the most recent snapshot.
     */
    public int getTotalTrackedCards() {
        LocalDate latest = getLastSnapshotDate();
        if (latest == null) return 0;
        return priceHistoryRepository.findByDate(latest).size();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<PriceChange> computeChanges(int limit, double minPrice, boolean winners) {
        List<LocalDate> dates = getTwoLatestDates();
        if (dates.size() < 2) return Collections.emptyList();

        Map<String, PriceHistory> latestMap = buildMap(priceHistoryRepository.findByDate(dates.get(0)));
        Map<String, PriceHistory> prevMap   = buildMap(priceHistoryRepository.findByDate(dates.get(1)));

        // Quantity per setCode_cn (sum across all users)
        Map<String, Integer> qtyByKey = new HashMap<>();
        for (UserCard uc : userCardRepository.findAll()) {
            String k = uc.getSetCode().toLowerCase() + "_" + uc.getCollectorNumber();
            qtyByKey.merge(k, uc.getQuantity(), Integer::sum);
        }

        // In-deck keys: setCode_cn combinations used in any deck (all users, all boards)
        Set<String> inDeckKeys = new HashSet<>();
        for (UserDeck deck : userDeckRepository.findAll()) {
            deck.getMainboard() .forEach(c -> inDeckKeys.add(c.getSetCode().toLowerCase() + "_" + c.getCollectorNumber()));
            deck.getSideboard() .forEach(c -> inDeckKeys.add(c.getSetCode().toLowerCase() + "_" + c.getCollectorNumber()));
            deck.getExtraboard().forEach(c -> inDeckKeys.add(c.getSetCode().toLowerCase() + "_" + c.getCollectorNumber()));
        }

        // Rarity map: setCode_cn → rarity (from Scryfall data)
        Set<String> setCodes = latestMap.values().stream()
                .map(PriceHistory::getSetCode)
                .collect(Collectors.toSet());
        Map<String, String> rarityByKey = new HashMap<>();
        for (ScryfallCard sc : scryfallCardRepository.findBySetCodeIn(setCodes)) {
            rarityByKey.put(sc.getSetCode().toLowerCase() + "_" + sc.getCollectorNumber(),
                    sc.getRarity() != null ? sc.getRarity() : "");
        }

        // Default sort: absolute change (more meaningful than % for portfolio relevance).
        // A card jumping €0.10→€0.20 (+100%) matters less than €5→€8 (+€3).
        Comparator<PriceChange> order = winners
                ? Comparator.comparingDouble(PriceChange::getAbsoluteChange).reversed()
                : Comparator.comparingDouble(PriceChange::getAbsoluteChange);

        return latestMap.values().stream()
                .filter(lat -> lat.getPriceRegular() != null && lat.getPriceRegular() >= minPrice)
                .map(lat -> {
                    String key = lat.getSetCode() + "_" + lat.getCollectorNumber();
                    PriceHistory prev = prevMap.get(key);
                    if (prev == null || prev.getPriceRegular() == null
                            || prev.getPriceRegular() < 0.001) return null;
                    double oldP = prev.getPriceRegular();
                    double newP = lat.getPriceRegular();
                    double abs  = newP - oldP;
                    double pct  = abs / oldP * 100.0;
                    if (winners && abs <= 0.0) return null;
                    if (!winners && abs >= 0.0) return null;

                    int     qty      = qtyByKey.getOrDefault(key, 0);
                    String  rarity   = rarityByKey.getOrDefault(key, "");
                    boolean tradable = !inDeckKeys.contains(key) && qty > 1;

                    return new PriceChange(lat.getCardName(), lat.getSetCode(),
                            lat.getCollectorNumber(), lat.getThumbnailUrl(),
                            oldP, newP, abs, pct, qty, rarity, tradable);
                })
                .filter(Objects::nonNull)
                .sorted(order)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Finds up to two most recent dates with snapshots, walking back up to 30 days
     * from today. Index 0 = latest, index 1 = second-latest.
     *
     * Package-private for testing.
     */
    List<LocalDate> getTwoLatestDates() {
        LocalDate base   = LocalDate.now();
        List<LocalDate> found = new ArrayList<>();
        for (int i = 0; i <= 30 && found.size() < 2; i++) {
            if (priceHistoryRepository.existsByDate(base.minusDays(i))) {
                found.add(base.minusDays(i));
            }
        }
        return found;
    }

    private Map<String, PriceHistory> buildMap(List<PriceHistory> list) {
        Map<String, PriceHistory> map = new HashMap<>();
        for (PriceHistory ph : list) {
            map.putIfAbsent(ph.getSetCode() + "_" + ph.getCollectorNumber(), ph);
        }
        return map;
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public static class PriceChange {
        private final String  cardName;
        private final String  setCode;
        private final String  collectorNumber;
        private final String  thumbnailUrl;
        private final double  oldPrice;
        private final double  newPrice;
        private final double  absoluteChange;
        private final double  percentChange;
        private final int     quantity;
        private final String  rarity;
        private final boolean tradable;

        public PriceChange(String cardName, String setCode, String collectorNumber,
                           String thumbnailUrl,
                           double oldPrice, double newPrice,
                           double absoluteChange, double percentChange,
                           int quantity, String rarity, boolean tradable) {
            this.cardName        = cardName;
            this.setCode         = setCode;
            this.collectorNumber = collectorNumber;
            this.thumbnailUrl    = thumbnailUrl;
            this.oldPrice        = oldPrice;
            this.newPrice        = newPrice;
            this.absoluteChange  = absoluteChange;
            this.percentChange   = percentChange;
            this.quantity        = quantity;
            this.rarity          = rarity;
            this.tradable        = tradable;
        }

        public String  getCardName()        { return cardName; }
        public String  getSetCode()         { return setCode; }
        public String  getCollectorNumber() { return collectorNumber; }
        public String  getThumbnailUrl()    { return thumbnailUrl; }
        public double  getOldPrice()        { return oldPrice; }
        public double  getNewPrice()        { return newPrice; }
        public double  getAbsoluteChange()  { return absoluteChange; }
        public double  getPercentChange()   { return percentChange; }
        public int     getQuantity()        { return quantity; }
        public String  getRarity()          { return rarity; }
        public boolean isTradable()         { return tradable; }
    }

    public static class SetSummary {
        private final String            setCode;
        private final String            setName;
        private final int               trackedCount;
        private final List<PriceChange> topWinners;
        private final List<PriceChange> topLosers;

        public SetSummary(String setCode, String setName, int trackedCount,
                          List<PriceChange> topWinners, List<PriceChange> topLosers) {
            this.setCode      = setCode;
            this.setName      = setName;
            this.trackedCount = trackedCount;
            this.topWinners   = topWinners;
            this.topLosers    = topLosers;
        }

        public String            getSetCode()      { return setCode; }
        public String            getSetName()      { return setName; }
        public int               getTrackedCount() { return trackedCount; }
        public List<PriceChange> getTopWinners()   { return topWinners; }
        public List<PriceChange> getTopLosers()    { return topLosers; }
    }
}
