package com.mtg.collection.service;

import com.mtg.collection.dto.DeckSuggestion;
import com.mtg.collection.dto.MissingCardEntry;
import com.mtg.collection.model.MetaDeck;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckSuggestServiceTest {

    @Mock
    private UserCardRepository userCardRepository;

    @Mock
    private MetaDeckService metaDeckService;

    @Mock
    private ScryfallCardRepository scryfallCardRepository;

    private DeckSuggestService service;

    @BeforeEach
    void setUp() {
        service = new DeckSuggestService(userCardRepository, metaDeckService, scryfallCardRepository);
    }

    // ── buildOwnedMap ──────────────────────────────────────────────────────────

    @Test
    void buildOwnedMap_aggregatesQuantitiesByNameCaseInsensitive() {
        UserCard c1 = new UserCard("Victor", "Lightning Bolt", "M10", "1", 3, false);
        UserCard c2 = new UserCard("Victor", "lightning bolt", "M11", "1", 1, false); // same name, different case/set
        UserCard c3 = new UserCard("Victor", "Swamp", "BFZ", "2", 10, false);

        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c1, c2, c3));

        Map<String, Integer> owned = service.buildOwnedMap("Victor");

        assertEquals(4, owned.get("lightning bolt"));
        assertEquals(10, owned.get("swamp"));
    }

    @Test
    void buildOwnedMap_emptyForUserWithNoCards() {
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        assertTrue(service.buildOwnedMap("Andre").isEmpty());
    }

    // ── buildMinPriceMap ───────────────────────────────────────────────────────

    @Test
    void buildMinPriceMap_picksLowestPriceAcrossPrintings() {
        ScryfallCard cheap = scryfallCard("Lightning Bolt", 0.50);
        ScryfallCard expensive = scryfallCard("Lightning Bolt", 2.00);
        ScryfallCard other = scryfallCard("Lava Spike", 1.20);

        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(List.of(cheap, expensive, other));

        Map<String, Double> prices = service.buildMinPriceMap(List.of("Lightning Bolt", "Lava Spike"));

        assertEquals(0.50, prices.get("Lightning Bolt"), 0.001);
        assertEquals(1.20, prices.get("Lava Spike"), 0.001);
    }

    @Test
    void buildMinPriceMap_emptyInputReturnsEmptyMap() {
        assertTrue(service.buildMinPriceMap(Collections.emptyList()).isEmpty());
    }

    @Test
    void buildMinPriceMap_ignoresNullAndZeroPrices() {
        ScryfallCard noPrice = scryfallCard("Counterspell", null);
        ScryfallCard zeroPrice = scryfallCard("Dark Ritual", 0.0);
        ScryfallCard valid = scryfallCard("Dark Ritual", 0.30);

        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(List.of(noPrice, zeroPrice, valid));

        Map<String, Double> prices = service.buildMinPriceMap(List.of("Counterspell", "Dark Ritual"));

        assertFalse(prices.containsKey("Counterspell"));
        assertEquals(0.30, prices.get("Dark Ritual"), 0.001);
    }

    // ── getSuggestions ─────────────────────────────────────────────────────────

    @Test
    void getSuggestions_perfectlyOwnedDeckIsMarkedComplete() {
        // User owns exactly what the deck needs
        UserCard lb = new UserCard("Victor", "Lightning Bolt", "M10", "1", 4, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(lb));
        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(Collections.emptyList());

        MetaDeck deck = buildDeck("commander", "Burn", List.of(
                new MetaDeck.MetaDeckCard("Lightning Bolt", 4),
                new MetaDeck.MetaDeckCard("Mountain", 20)           // basic land – ignored
        ));
        when(metaDeckService.getMetaDecks("commander")).thenReturn(List.of(deck));

        List<DeckSuggestion> results = service.getSuggestions("Victor", "commander");

        assertEquals(1, results.size());
        DeckSuggestion s = results.get(0);
        assertEquals(0, s.getMissingUniqueCards());
        assertEquals(1, s.getTotalCards());     // Mountain excluded
        assertEquals(1, s.getOwnedUniqueCards());
        assertEquals(100.0, s.getCompletionPercent());
        assertTrue(s.getMissingCards().isEmpty());
        assertEquals(0.0, s.getTotalMissingCost(), 0.001);
    }

    @Test
    void getSuggestions_missingCardsAreListedCorrectly() {
        // User owns 2 of Lightning Bolt (needs 4) and 0 of Lava Spike (needs 4)
        UserCard lb = new UserCard("Victor", "Lightning Bolt", "M10", "1", 2, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(lb));
        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(Collections.emptyList());

        MetaDeck deck = buildDeck("modern", "Burn", List.of(
                new MetaDeck.MetaDeckCard("Lightning Bolt", 4),
                new MetaDeck.MetaDeckCard("Lava Spike", 4),
                new MetaDeck.MetaDeckCard("Mountain", 16)
        ));
        when(metaDeckService.getMetaDecks("modern")).thenReturn(List.of(deck));

        List<DeckSuggestion> results = service.getSuggestions("Victor", "modern");

        assertEquals(1, results.size());
        DeckSuggestion s = results.get(0);
        assertEquals(2, s.getMissingUniqueCards());
        assertEquals(2, s.getTotalCards()); // Mountain excluded
        assertEquals(0, s.getOwnedUniqueCards());

        // Both cards are missing
        List<MissingCardEntry> missing = s.getMissingCards();
        assertEquals(2, missing.size());

        // Alphabetical order: Lava Spike < Lightning Bolt
        assertEquals("Lava Spike", missing.get(0).getCardName());
        assertEquals(4, missing.get(0).getRequiredQuantity());
        assertEquals(0, missing.get(0).getOwnedQuantity());
        assertEquals(4, missing.get(0).getStillNeeded());

        assertEquals("Lightning Bolt", missing.get(1).getCardName());
        assertEquals(4, missing.get(1).getRequiredQuantity());
        assertEquals(2, missing.get(1).getOwnedQuantity());
        assertEquals(2, missing.get(1).getStillNeeded());
    }

    @Test
    void getSuggestions_totalMissingCostSumsAllMissingCopies() {
        when(userCardRepository.findByUser("Victor")).thenReturn(Collections.emptyList());

        // Lava Spike: 4 needed, €1.00 cheapest → cost = 4 × 1.00 = 4.00
        // Lightning Bolt: 4 needed, €0.50 cheapest → cost = 4 × 0.50 = 2.00
        // Total: €6.00
        ScryfallCard lavaSpike = scryfallCard("Lava Spike", 1.00);
        ScryfallCard bolt = scryfallCard("Lightning Bolt", 0.50);
        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(List.of(lavaSpike, bolt));

        MetaDeck deck = buildDeck("modern", "Burn", List.of(
                new MetaDeck.MetaDeckCard("Lava Spike", 4),
                new MetaDeck.MetaDeckCard("Lightning Bolt", 4)
        ));
        when(metaDeckService.getMetaDecks("modern")).thenReturn(List.of(deck));

        List<DeckSuggestion> results = service.getSuggestions("Victor", "modern");

        assertEquals(6.0, results.get(0).getTotalMissingCost(), 0.01);
    }

    @Test
    void getSuggestions_sortedByMissingThenCompletionDesc() {
        // Deck A: 2 missing, Deck B: 1 missing → B first
        when(userCardRepository.findByUser("Victor")).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findByNameIn(anyList())).thenReturn(Collections.emptyList());

        MetaDeck deckA = buildDeck("modern", "Deck A", List.of(
                new MetaDeck.MetaDeckCard("CardX", 1),
                new MetaDeck.MetaDeckCard("CardY", 1)
        ));
        MetaDeck deckB = buildDeck("modern", "Deck B", List.of(
                new MetaDeck.MetaDeckCard("CardZ", 1)
        ));
        when(metaDeckService.getMetaDecks("modern")).thenReturn(List.of(deckA, deckB));

        List<DeckSuggestion> results = service.getSuggestions("Victor", "modern");

        assertEquals("Deck B", results.get(0).getDeckName());
        assertEquals("Deck A", results.get(1).getDeckName());
    }

    @Test
    void getSuggestions_basicLandsAreExcludedFromMissingCount() {
        when(userCardRepository.findByUser("Victor")).thenReturn(Collections.emptyList());
        // No scryfallCardRepository stub needed: all cards are basic lands → allCardNames is empty

        MetaDeck deck = buildDeck("commander", "Lands", List.of(
                new MetaDeck.MetaDeckCard("Mountain", 5),
                new MetaDeck.MetaDeckCard("Island", 5),
                new MetaDeck.MetaDeckCard("Forest", 5),
                new MetaDeck.MetaDeckCard("Plains", 5),
                new MetaDeck.MetaDeckCard("Swamp", 5),
                new MetaDeck.MetaDeckCard("Snow-Covered Mountain", 2),
                new MetaDeck.MetaDeckCard("Wastes", 2)
        ));
        when(metaDeckService.getMetaDecks("commander")).thenReturn(List.of(deck));

        List<DeckSuggestion> results = service.getSuggestions("Victor", "commander");

        assertEquals(0, results.get(0).getTotalCards());
        assertEquals(0, results.get(0).getMissingUniqueCards());
    }

    @Test
    void getSuggestions_emptyMetaDecksReturnsEmptyList() {
        when(userCardRepository.findByUser("Victor")).thenReturn(Collections.emptyList());
        when(metaDeckService.getMetaDecks("legacy")).thenReturn(Collections.emptyList());

        assertTrue(service.getSuggestions("Victor", "legacy").isEmpty());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private MetaDeck buildDeck(String format, String name, List<MetaDeck.MetaDeckCard> cards) {
        MetaDeck d = new MetaDeck();
        d.setId(format + "_" + name);
        d.setFormat(format);
        d.setName(name);
        d.setSlug(name);
        d.setPlayRate(1.0);
        d.setMainboard(cards);
        d.setFetchedAt(LocalDate.now());
        return d;
    }

    private ScryfallCard scryfallCard(String name, Double price) {
        ScryfallCard sc = new ScryfallCard();
        sc.setName(name);
        sc.setPriceRegular(price);
        return sc;
    }
}
