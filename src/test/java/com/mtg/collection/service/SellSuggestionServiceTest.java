package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.model.UserDeck;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import com.mtg.collection.service.SellSuggestionService.SellSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellSuggestionServiceTest {

    @Mock private UserCardRepository    userCardRepository;
    @Mock private UserDeckRepository    userDeckRepository;
    @Mock private ScryfallCardRepository scryfallCardRepository;

    private SellSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new SellSuggestionService(userCardRepository, userDeckRepository, scryfallCardRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserCard card(String name, String setCode, String number, int qty, boolean foil) {
        UserCard c = new UserCard("Victor", name, setCode, number, qty, foil);
        c.setPrice(0.0);
        return c;
    }

    private ScryfallCard sfCard(String setCode, String number, double priceRegular, double priceFoil) {
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode(setCode);
        sc.setCollectorNumber(number);
        sc.setPriceRegular(priceRegular);
        sc.setPriceFoil(priceFoil);
        sc.setPurchaseLink("https://www.cardmarket.com/en/Magic/Products/Singles/TestSet/TestCard");
        return sc;
    }

    private void stubNoDecks() {
        when(userDeckRepository.findByUserOrderByCommanderDescNameAsc("Victor"))
                .thenReturn(Collections.emptyList());
    }

    private void stubScryfall(ScryfallCard... cards) {
        when(scryfallCardRepository.findBySetCodeIn(anyCollection()))
                .thenReturn(List.of(cards));
    }

    // ── MIN_PRICE = 0.50 ─────────────────────────────────────────────────────

    @Test
    void cardsAtExactlyFiftyCentThresholdAreIncluded() {
        UserCard c = card("Pop Quiz", "snc", "44", 2, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        stubScryfall(sfCard("snc", "44", 0.50, 0.0));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size());
        assertEquals(0.50, result.get(0).getPricePerCopy(), 0.001);
    }

    @Test
    void cardsBelowFiftyCentThresholdAreExcluded() {
        UserCard c = card("Cheap Card", "m21", "100", 2, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        stubScryfall(sfCard("m21", "100", 0.49, 0.0));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertTrue(result.isEmpty(), "Card priced 0.49 € should be excluded");
    }

    @Test
    void cardsWithPriceOneEuroAreIncluded() {
        UserCard c = card("Expensive Card", "znr", "5", 3, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        stubScryfall(sfCard("znr", "5", 2.50, 0.0));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size());
        assertEquals(2.50, result.get(0).getPricePerCopy(), 0.001);
        assertEquals(2, result.get(0).getSellableQty()); // qty 3 → sell 2
    }

    // ── Case-insensitive setCode matching ─────────────────────────────────────

    @Test
    void uppercaseSetCodeInUserCardMatchesLowercaseScryfallCache() {
        // DragonShield imports set codes in uppercase; Scryfall cache stores them lowercase.
        UserCard c = card("Pop Quiz", "SNC", "44", 2, false); // uppercase
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        // Scryfall cache stores lowercase
        stubScryfall(sfCard("snc", "44", 3.00, 0.0));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size(), "Uppercase setCode should match lowercase Scryfall entry");
        assertEquals(3.00, result.get(0).getPricePerCopy(), 0.001);
    }

    // ── Foil price resolution ─────────────────────────────────────────────────

    @Test
    void foilCardUsesFoilPrice() {
        UserCard c = card("Shiny", "neo", "200", 2, true); // foil
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        stubScryfall(sfCard("neo", "200", 1.00, 5.00)); // foil = 5.00

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size());
        assertEquals(5.00, result.get(0).getPricePerCopy(), 0.001);
    }

    @Test
    void foilCardFallsBackToRegularPriceWhenFoilPriceIsZero() {
        UserCard c = card("Shiny", "neo", "200", 2, true);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        ScryfallCard sf = sfCard("neo", "200", 2.00, 0.0); // foil = 0, regular = 2.00
        sf.setPriceFoil(0.0);
        stubScryfall(sf);

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size());
        assertEquals(2.00, result.get(0).getPricePerCopy(), 0.001,
                "Should fall back to regular price when foil price is absent");
    }

    // ── Deck exclusion ────────────────────────────────────────────────────────

    @Test
    void cardsInADeckAreExcluded() {
        UserCard c = card("Commander Card", "c21", "10", 2, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));

        // Build a deck that contains this card
        UserDeck.DeckCard dc = new UserDeck.DeckCard();
        dc.setSetCode("c21");
        dc.setCollectorNumber("10");
        dc.setFoil(false);
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(dc));
        deck.setSideboard(Collections.emptyList());
        deck.setExtraboard(Collections.emptyList());
        when(userDeckRepository.findByUserOrderByCommanderDescNameAsc("Victor"))
                .thenReturn(List.of(deck));

        // scryfallCardRepository is NOT called because notInDecks is empty → no stub needed

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertTrue(result.isEmpty(), "Card used in a deck should not appear in sell suggestions");
    }

    // ── Quantity = 1 excluded ─────────────────────────────────────────────────

    @Test
    void singleCopyCardsAreExcluded() {
        UserCard c = card("Lonely Card", "afr", "1", 1, false); // qty = 1
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        // userDeckRepository and scryfallCardRepository are NOT called (service returns early)

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertTrue(result.isEmpty(), "Cards with quantity=1 should not appear");
        verify(scryfallCardRepository, never()).findBySetCodeIn(anyCollection());
    }

    // ── getCardmarketLink ─────────────────────────────────────────────────────

    @Test
    void cardmarketLinkTransformsEnToDeAndAddsSellerCountry() {
        ScryfallCard sf = sfCard("sld", "1371", 10.0, 0.0);
        sf.setPurchaseLink("https://www.cardmarket.com/en/Magic/Products/Singles/TestSet/TestCard");
        UserCard c = card("SLD Card", "sld", "1371", 2, false);
        SellSuggestion s = new SellSuggestion(c, sf, 10.0, 1);

        String link = s.getCardmarketLink();

        assertNotNull(link);
        assertTrue(link.contains("/de/Magic/"), "Link should use German locale");
        assertTrue(link.contains("sellerCountry=7"), "Link should include sellerCountry=7");
        assertFalse(link.contains("/en/Magic/"), "Link should not contain English locale");
    }

    @Test
    void cardmarketLinkAppendsSellerCountryWithAmpersandWhenQueryAlreadyPresent() {
        ScryfallCard sf = sfCard("snc", "44", 1.0, 0.0);
        sf.setPurchaseLink("https://www.cardmarket.com/en/Magic/Products/Singles/Test/Card?foo=bar");
        UserCard c = card("Card", "snc", "44", 2, false);
        SellSuggestion s = new SellSuggestion(c, sf, 1.0, 1);

        String link = s.getCardmarketLink();

        assertNotNull(link);
        assertTrue(link.contains("&sellerCountry=7"), "Should use & when query string already present");
    }

    @Test
    void cardmarketLinkIsNullWhenNoPurchaseLinkInScryfallCard() {
        ScryfallCard sf = sfCard("snc", "44", 1.0, 0.0);
        sf.setPurchaseLink(null);
        UserCard c = card("Card", "snc", "44", 2, false);
        SellSuggestion s = new SellSuggestion(c, sf, 1.0, 1);

        assertNull(s.getCardmarketLink());
    }

    @Test
    void cardmarketLinkIsNullWhenScryfallCardIsNull() {
        UserCard c = card("Card", "snc", "44", 2, false);
        SellSuggestion s = new SellSuggestion(c, null, 1.0, 1);

        assertNull(s.getCardmarketLink());
    }

    // ── SLD cards pass through service (filter is client-side only) ───────────

    @Test
    void sldCardsAreIncludedByServiceWhenPriceQualifies() {
        UserCard c = card("Secret Lair Card", "sld", "1371", 2, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c));
        stubNoDecks();
        stubScryfall(sfCard("sld", "1371", 15.00, 0.0));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(1, result.size(), "SLD cards should be included by the service (filter is client-side)");
    }

    // ── Result ordering ───────────────────────────────────────────────────────

    @Test
    void suggestionsAreSortedByTotalValueDescending() {
        UserCard c1 = card("Cheap", "m21", "1", 2, false);
        UserCard c2 = card("Expensive", "m21", "2", 3, false);
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(c1, c2));
        stubNoDecks();
        when(scryfallCardRepository.findBySetCodeIn(anyCollection()))
                .thenReturn(List.of(
                        sfCard("m21", "1", 1.00, 0.0),   // sellableQty=1, totalValue=1.00
                        sfCard("m21", "2", 2.00, 0.0)    // sellableQty=2, totalValue=4.00
                ));

        List<SellSuggestion> result = service.getSuggestions("Victor");

        assertEquals(2, result.size());
        assertTrue(result.get(0).getTotalValue() > result.get(1).getTotalValue(),
                "First suggestion should have higher total value");
    }

    // ── Thumbnail / image fallback ─────────────────────────────────────────────

    @Test
    void thumbnailUrlFallsBackToScryfallApiWhenScryfallCardIsNull() {
        UserCard c = card("Card", "snc", "44", 2, false);
        SellSuggestion s = new SellSuggestion(c, null, 1.0, 1);

        String url = s.getThumbnailUrl();

        assertTrue(url.contains("api.scryfall.com"), "Should fall back to Scryfall API URL");
        assertTrue(url.contains("snc"), "Should include set code");
        assertTrue(url.contains("44"),  "Should include collector number");
    }
}
