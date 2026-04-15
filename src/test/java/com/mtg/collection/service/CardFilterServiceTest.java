package com.mtg.collection.service;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardFilterServiceTest {

    private CardFilterService filterService;
    private List<CardWithUserData> testCards;

    @BeforeEach
    void setUp() {
        filterService = new CardFilterService();
        
        ScryfallCard card1 = createCard("1", "Test Card 1", "mythic");
        ScryfallCard card2 = createCard("2", "Test Card 2", "rare");
        ScryfallCard card3 = createCard("3", "Test Card 3", "uncommon");
        ScryfallCard card4 = createCard("4", "Test Card 4", "common");
        
        testCards = Arrays.asList(
            createCardWithUserData(card1, 2, 1),
            createCardWithUserData(card2, 0, 0),
            createCardWithUserData(card3, 1, 0),
            createCardWithUserData(card4, 0, 2)
        );
    }

    private ScryfallCard createCard(String collectorNumber, String name, String rarity) {
        ScryfallCard card = new ScryfallCard();
        card.setCollectorNumber(collectorNumber);
        card.setName(name);
        card.setRarity(rarity);
        card.setSetCode("test");
        return card;
    }

    private CardWithUserData createCardWithUserData(ScryfallCard card, int quantity, int foilQuantity) {
        return new CardWithUserData(card, quantity, foilQuantity);
    }

    @Test
    void testFilterAll_ReturnsAllCards() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, null, null, null, null);
        
        assertEquals(4, result.size());
    }

    @Test
    void testFilterOwned_ReturnsCardsWithQuantity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "owned", null, null, null, null, null);
        
        assertEquals(3, result.size());
    }

    @Test
    void testFilterMissing_ReturnsCardsWithNoQuantity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "missing", null, null, null, null, null);
        
        assertEquals(1, result.size());
    }

    @Test
    void testFilterByRarity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, "mythic,rare", null, null, null);
        
        assertEquals(2, result.size());
    }

    @Test
    void testFilterBySearchName() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, null, "Card 1", null, null);
        
        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getCard().getCollectorNumber());
    }

    @Test
    void testEmptyList() {
        List<CardWithUserData> result = filterService.filterCards(Collections.emptyList(), "owned", null, null, null, null, null);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testShowBasicsExcludesBasicLands() {
        ScryfallCard basicLand = createCard("5", "Forest", "common");
        basicLand.setTypeLine("Basic Land — Forest");
        
        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(basicLand, 1, 0),
            createCardWithUserData(createCard("6", "Lightning Bolt", "uncommon"), 1, 0)
        );
        
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, null);
        
        assertEquals(1, result.size());
        assertEquals("Lightning Bolt", result.get(0).getCard().getName());
    }

    @Test
    void testShowBasicsTrueIncludesBasicLands() {
        ScryfallCard basicLand = createCard("5", "Forest", "common");
        basicLand.setTypeLine("Basic Land — Forest");
        
        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(basicLand, 1, 0),
            createCardWithUserData(createCard("6", "Lightning Bolt", "uncommon"), 1, 0)
        );
        
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, "true", null);
        
        assertEquals(2, result.size());
    }

    @Test
    void testFrameStyle_ExtendedArt_ReturnsOnlyExtendedArt() {
        ScryfallCard extCard = createCard("10", "Extended Art Card", "rare");
        extCard.setFrameStatus("extendedart");
        ScryfallCard normalCard = createCard("11", "Normal Card", "rare");

        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(extCard, 1, 0),
            createCardWithUserData(normalCard, 1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "extendedart");

        assertEquals(1, result.size());
        assertEquals("Extended Art Card", result.get(0).getCard().getName());
    }

    @Test
    void testFrameStyle_Showcase_ReturnsOnlyShowcase() {
        ScryfallCard showcaseCard = createCard("12", "Showcase Card", "rare");
        showcaseCard.setFrameStatus("showcase");
        ScryfallCard normalCard = createCard("13", "Normal Card", "rare");

        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(showcaseCard, 1, 0),
            createCardWithUserData(normalCard, 1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "showcase");

        assertEquals(1, result.size());
        assertEquals("Showcase Card", result.get(0).getCard().getName());
    }

    @Test
    void testFrameStyle_Borderless_ReturnsOnlyBorderless() {
        ScryfallCard borderlessCard = createCard("14", "Borderless Card", "mythic");
        borderlessCard.setBorderColor("borderless");
        ScryfallCard normalCard = createCard("15", "Normal Card", "mythic");
        normalCard.setBorderColor("black");

        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(borderlessCard, 1, 0),
            createCardWithUserData(normalCard, 1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "borderless");

        assertEquals(1, result.size());
        assertEquals("Borderless Card", result.get(0).getCard().getName());
    }

    @Test
    void testFrameStyle_FullArt_ReturnsOnlyFullArt() {
        ScryfallCard fullArtLand = createCard("16", "Full Art Forest", "common");
        fullArtLand.setTypeLine("Basic Land — Forest");
        fullArtLand.setFullArt(true);
        ScryfallCard normalLand = createCard("17", "Forest", "common");
        normalLand.setTypeLine("Basic Land — Forest");
        normalLand.setFullArt(false);

        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(fullArtLand, 1, 0),
            createCardWithUserData(normalLand, 1, 0)
        );

        // showBasics=true so basic lands are not filtered out; frameStyle=fullart
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, "true", "fullart");

        assertEquals(1, result.size());
        assertEquals("Full Art Forest", result.get(0).getCard().getName());
    }

    @Test
    void testFrameStyle_ExtendedArt_NullFrameStatus_ReturnsEmpty() {
        // Cards with null frameStatus must not show up under extendedart filter
        ScryfallCard card = createCard("20", "Normal Card", "rare");
        // frameStatus left null (as it would be for old cached cards)

        List<CardWithUserData> cards = Arrays.asList(createCardWithUserData(card, 1, 0));

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "extendedart");

        assertTrue(result.isEmpty());
    }
}
