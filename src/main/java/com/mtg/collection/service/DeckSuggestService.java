package com.mtg.collection.service;

import com.mtg.collection.dto.DeckSuggestion;
import com.mtg.collection.dto.MissingCardEntry;
import com.mtg.collection.model.MetaDeck;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Matches a user's collection against meta-deck lists and produces
 * ranked {@link DeckSuggestion} results.
 */
@Service
public class DeckSuggestService {

    /** Basic-land names that are never counted as "missing". */
    public static final Set<String> BASIC_LANDS = Set.of(
            "Mountain", "Island", "Forest", "Plains", "Swamp",
            "Snow-Covered Mountain", "Snow-Covered Island",
            "Snow-Covered Forest", "Snow-Covered Plains", "Snow-Covered Swamp",
            "Wastes"
    );

    private final UserCardRepository userCardRepository;
    private final MetaDeckService metaDeckService;
    private final ScryfallCardRepository scryfallCardRepository;

    public DeckSuggestService(UserCardRepository userCardRepository,
                               MetaDeckService metaDeckService,
                               ScryfallCardRepository scryfallCardRepository) {
        this.userCardRepository = userCardRepository;
        this.metaDeckService = metaDeckService;
        this.scryfallCardRepository = scryfallCardRepository;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns deck suggestions for {@code user} and {@code format},
     * sorted ascending by {@code missingUniqueCards}, then descending by
     * {@code completionPercent}.
     */
    public List<DeckSuggestion> getSuggestions(String user, String format) {
        // 1. Aggregate user's collection by lower-cased card name
        Map<String, Integer> owned = buildOwnedMap(user);

        // 2. Fetch (possibly cached) meta-decks
        List<MetaDeck> metaDecks = metaDeckService.getMetaDecks(format);

        // 3. Collect all unique non-basic card names across all decks for a single price batch-query
        List<String> allCardNames = metaDecks.stream()
                .flatMap(d -> d.getMainboard().stream())
                .map(c -> c.getName().trim())
                .filter(n -> BASIC_LANDS.stream().noneMatch(b -> b.equalsIgnoreCase(n)))
                .distinct()
                .collect(Collectors.toList());

        // 4. Build name → cheapest regular EUR price map (one DB query)
        Map<String, Double> minPriceByName = buildMinPriceMap(allCardNames);

        // 5. Build a suggestion for each deck
        List<DeckSuggestion> suggestions = new ArrayList<>();
        for (MetaDeck deck : metaDecks) {
            suggestions.add(buildSuggestion(deck, owned, minPriceByName));
        }

        // 6. Sort: fewest missing first; ties broken by most complete first
        suggestions.sort(Comparator
                .comparingInt(DeckSuggestion::getMissingUniqueCards)
                .thenComparingDouble(s -> -s.getCompletionPercent()));

        return suggestions;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Builds a map from lower-cased card name → total quantity owned by user.
     */
    Map<String, Integer> buildOwnedMap(String user) {
        List<UserCard> cards = userCardRepository.findByUser(user);
        Map<String, Integer> map = new HashMap<>();
        for (UserCard c : cards) {
            String key = c.getName().toLowerCase().trim();
            map.merge(key, c.getQuantity(), Integer::sum);
        }
        return map;
    }

    /**
     * Fetches all printings for the given card names in one DB query and
     * returns a map from card name (original casing) → cheapest priceRegular.
     * Cards with no price data are omitted.
     */
    Map<String, Double> buildMinPriceMap(List<String> cardNames) {
        if (cardNames.isEmpty()) return Collections.emptyMap();
        List<ScryfallCard> cards = scryfallCardRepository.findByNameIn(cardNames);
        Map<String, Double> minPrice = new HashMap<>();
        for (ScryfallCard sc : cards) {
            if (sc.getPriceRegular() != null && sc.getPriceRegular() > 0) {
                minPrice.merge(sc.getName(), sc.getPriceRegular(), Math::min);
            }
        }
        return minPrice;
    }

    private DeckSuggestion buildSuggestion(MetaDeck deck, Map<String, Integer> owned,
                                            Map<String, Double> minPriceByName) {
        List<MissingCardEntry> missing = new ArrayList<>();
        int totalUnique = 0;
        int ownedUnique = 0;
        double deckMissingCost = 0.0;

        for (MetaDeck.MetaDeckCard deckCard : deck.getMainboard()) {
            String name = deckCard.getName().trim();

            // Skip basic lands
            if (BASIC_LANDS.stream().anyMatch(b -> b.equalsIgnoreCase(name))) continue;

            totalUnique++;
            int have = owned.getOrDefault(name.toLowerCase(), 0);
            if (have >= deckCard.getQuantity()) {
                ownedUnique++;
            } else {
                double price = minPriceByName.getOrDefault(name, 0.0);
                MissingCardEntry entry = new MissingCardEntry(name, deckCard.getQuantity(), have, price);
                missing.add(entry);
                deckMissingCost += entry.getTotalMissingCost();
            }
        }

        // Sort missing cards alphabetically
        missing.sort(Comparator.comparing(MissingCardEntry::getCardName));

        double completionPct = totalUnique == 0 ? 0.0
                : (ownedUnique * 100.0) / totalUnique;

        DeckSuggestion suggestion = new DeckSuggestion();
        suggestion.setDeckName(deck.getName());
        suggestion.setFormat(deck.getFormat());
        suggestion.setSlug(deck.getSlug());
        suggestion.setPlayRate(deck.getPlayRate());
        suggestion.setCommanderName(deck.getCommanderName());
        suggestion.setTotalCards(totalUnique);
        suggestion.setOwnedUniqueCards(ownedUnique);
        suggestion.setMissingUniqueCards(missing.size());
        suggestion.setCompletionPercent(Math.round(completionPct * 10.0) / 10.0);
        suggestion.setTotalMissingCost(Math.round(deckMissingCost * 100.0) / 100.0);
        suggestion.setMissingCards(missing);
        suggestion.setFetchedAt(deck.getFetchedAt());
        return suggestion;
    }
}
