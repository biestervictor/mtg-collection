package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.model.UserDeck;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SellSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SellSuggestionService.class);
    private static final double MIN_PRICE = 0.5;

    private final UserCardRepository    userCardRepository;
    private final UserDeckRepository    userDeckRepository;
    private final ScryfallCardRepository scryfallCardRepository;

    public SellSuggestionService(UserCardRepository userCardRepository,
                                  UserDeckRepository userDeckRepository,
                                  ScryfallCardRepository scryfallCardRepository) {
        this.userCardRepository     = userCardRepository;
        this.userDeckRepository     = userDeckRepository;
        this.scryfallCardRepository = scryfallCardRepository;
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    public static class SellSuggestion {
        private final UserCard    userCard;
        private final ScryfallCard scryfallCard; // may be null
        private final double      pricePerCopy;
        private final int         sellableQty;   // quantity - 1 (keep one)
        private final double      totalValue;    // sellableQty * pricePerCopy

        public SellSuggestion(UserCard userCard, ScryfallCard scryfallCard,
                              double pricePerCopy, int sellableQty) {
            this.userCard     = userCard;
            this.scryfallCard = scryfallCard;
            this.pricePerCopy = pricePerCopy;
            this.sellableQty  = sellableQty;
            this.totalValue   = sellableQty * pricePerCopy;
        }

        public UserCard     getUserCard()    { return userCard;     }
        public ScryfallCard getScryfallCard(){ return scryfallCard; }
        public double       getPricePerCopy(){ return pricePerCopy; }
        public int          getSellableQty() { return sellableQty;  }
        public double       getTotalValue()  { return totalValue;   }

        /** Thumbnail URL: from ScryfallCard if available, else Scryfall API fallback. */
        public String getThumbnailUrl() {
            if (scryfallCard != null && scryfallCard.getThumbnailFront() != null
                    && !scryfallCard.getThumbnailFront().isEmpty()) {
                return scryfallCard.getThumbnailFront();
            }
            return "https://api.scryfall.com/cards/"
                    + userCard.getSetCode().toLowerCase() + "/"
                    + userCard.getCollectorNumber()
                    + "?format=image&version=small";
        }

        /** Full-size image URL for hover preview. */
        public String getImageUrl() {
            if (scryfallCard != null && scryfallCard.getImageFront() != null
                    && !scryfallCard.getImageFront().isEmpty()) {
                return scryfallCard.getImageFront();
            }
            return "https://api.scryfall.com/cards/"
                    + userCard.getSetCode().toLowerCase() + "/"
                    + userCard.getCollectorNumber()
                    + "?format=image&version=normal";
        }

        /** Cardmarket link forced to German locale with sellerCountry=7. */
        public String getCardmarketLink() {
            if (scryfallCard == null) return null;
            String link = scryfallCard.getPurchaseLink();
            if (link == null || link.isEmpty()) return null;
            // Replace English locale with German
            link = link.replace("cardmarket.com/en/", "cardmarket.com/de/");
            // Append seller country filter (7 = Germany)
            return link.contains("?") ? link + "&sellerCountry=7" : link + "?sellerCountry=7";
        }

        /** Rarity of the card: "common", "uncommon", "rare", "mythic", or "unknown" if not in cache. */
        public String getRarity() {
            if (scryfallCard == null) return "unknown";
            String r = scryfallCard.getRarity();
            return r != null ? r : "unknown";
        }

        /** @deprecated Use {@link #getCardmarketLink()} for the localised link. */
        @Deprecated
        public String getPurchaseLink() {
            return scryfallCard != null ? scryfallCard.getPurchaseLink() : null;
        }
    }

    // ── Main query ────────────────────────────────────────────────────────────

    public List<SellSuggestion> getSuggestions(String user) {
        // 1. Load all user cards → keep only those with qty > 1
        List<UserCard> allCards = userCardRepository.findByUser(user);
        List<UserCard> duplicates = allCards.stream()
                .filter(c -> c.getQuantity() > 1)
                .collect(Collectors.toList());

        if (duplicates.isEmpty()) return Collections.emptyList();

        // 2. Build "in-deck" key set from all user decks (all three boards)
        Set<String> inDeckKeys = buildInDeckKeys(user);

        // 3. Filter out cards that appear in any deck
        List<UserCard> notInDecks = duplicates.stream()
                .filter(c -> !inDeckKeys.contains(deckKey(c)))
                .collect(Collectors.toList());

        if (notInDecks.isEmpty()) return Collections.emptyList();

        // 4. Batch-load Scryfall data in one query; normalise to lowercase throughout
        //    to avoid case mismatches between DragonShield (uppercase) and Scryfall cache (lowercase).
        Set<String> setCodes = notInDecks.stream()
                .map(c -> c.getSetCode().toLowerCase())
                .collect(Collectors.toSet());
        Map<String, ScryfallCard> sfMap = new HashMap<>();
        scryfallCardRepository.findBySetCodeIn(setCodes)
                // putIfAbsent: first entry wins if duplicate documents exist in the cache
                .forEach(sf -> sfMap.putIfAbsent(sf.getSetCode().toLowerCase() + "_" + sf.getCollectorNumber(), sf));

        // 5. Build suggestions, filter by price >= MIN_PRICE, sort by totalValue desc
        List<SellSuggestion> result = new ArrayList<>();
        for (UserCard card : notInDecks) {
            ScryfallCard sf = sfMap.get(card.getSetCode().toLowerCase() + "_" + card.getCollectorNumber());
            double price = resolvePrice(card, sf);
            if (price < MIN_PRICE) continue;
            int sellableQty = card.getQuantity() - 1;
            result.add(new SellSuggestion(card, sf, price, sellableQty));
        }

        result.sort(Comparator.comparingDouble(SellSuggestion::getTotalValue).reversed());
        log.info("Sell suggestions for {}: {} cards, total potential revenue: {} €",
                user, result.size(),
                String.format("%.2f", result.stream().mapToDouble(SellSuggestion::getTotalValue).sum()));
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<String> buildInDeckKeys(String user) {
        Set<String> keys = new HashSet<>();
        for (UserDeck deck : userDeckRepository.findByUserOrderByCommanderDescNameAsc(user)) {
            deck.getMainboard() .forEach(c -> keys.add(deckKey(c.getSetCode(), c.getCollectorNumber(), c.isFoil())));
            deck.getSideboard() .forEach(c -> keys.add(deckKey(c.getSetCode(), c.getCollectorNumber(), c.isFoil())));
            deck.getExtraboard().forEach(c -> keys.add(deckKey(c.getSetCode(), c.getCollectorNumber(), c.isFoil())));
        }
        return keys;
    }

    private static String deckKey(UserCard c) {
        return deckKey(c.getSetCode(), c.getCollectorNumber(), c.isFoil());
    }

    private static String deckKey(String setCode, String collectorNumber, boolean foil) {
        return setCode + "_" + collectorNumber + "_" + foil;
    }

    /**
     * Effective price: prefer ScryfallCard price (foil or regular), fall back to UserCard.price.
     */
    private static double resolvePrice(UserCard card, ScryfallCard sf) {
        if (sf != null) {
            Double sfPrice = card.isFoil() ? sf.getPriceFoil() : sf.getPriceRegular();
            if (sfPrice != null && sfPrice > 0) return sfPrice;
            // fallback to the other variant if primary is missing
            Double alt = card.isFoil() ? sf.getPriceRegular() : sf.getPriceFoil();
            if (alt != null && alt > 0) return alt;
        }
        return card.getPrice(); // last resort: stored price
    }
}
