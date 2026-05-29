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
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, null, null, null, null, null);
        
        assertEquals(4, result.size());
    }

    @Test
    void testFilterOwned_ReturnsCardsWithQuantity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "owned", null, null, null, null, null, null);
        
        assertEquals(3, result.size());
    }

    @Test
    void testFilterMissing_ReturnsCardsWithNoQuantity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "missing", null, null, null, null, null, null);
        
        assertEquals(1, result.size());
    }

    @Test
    void testFilterByRarity() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, "mythic,rare", null, null, null, null);
        
        assertEquals(2, result.size());
    }

    @Test
    void testFilterBySearchName() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, null, "Card 1", null, null, null);
        
        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getCard().getCollectorNumber());
    }

    @Test
    void testEmptyList() {
        List<CardWithUserData> result = filterService.filterCards(Collections.emptyList(), "owned", null, null, null, null, null, null);
        
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
        
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, null, null);
        
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
        
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, "true", null, null);
        
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

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "extendedart", null);

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

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "showcase", null);

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

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "borderless", null);

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
        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, "true", "fullart", null);

        assertEquals(1, result.size());
        assertEquals("Full Art Forest", result.get(0).getCard().getName());
    }

    @Test
    void testFrameStyle_ExtendedArt_NullFrameStatus_ReturnsEmpty() {
        // Cards with null frameStatus must not show up under extendedart filter
        ScryfallCard card = createCard("20", "Normal Card", "rare");
        // frameStatus left null (as it would be for old cached cards)

        List<CardWithUserData> cards = Arrays.asList(createCardWithUserData(card, 1, 0));

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "extendedart", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testHideTokens_FiltersOutTokenCards() {
        ScryfallCard tokenCard = createCard("30", "Soldier Token", "common");
        tokenCard.setTypeLine("Token Creature — Soldier");
        ScryfallCard normalCard = createCard("31", "Lightning Bolt", "uncommon");
        normalCard.setTypeLine("Instant");

        List<CardWithUserData> cards = Arrays.asList(
            createCardWithUserData(tokenCard, 1, 0),
            createCardWithUserData(normalCard, 1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, null, "true");

        assertEquals(1, result.size());
        assertEquals("Lightning Bolt", result.get(0).getCard().getName());
    }

    @Test
    void testHideTokens_Null_IncludesTokenCards() {
        ScryfallCard tokenCard = createCard("32", "Goblin Token", "common");
        tokenCard.setTypeLine("Token Creature — Goblin");

        List<CardWithUserData> cards = Arrays.asList(createCardWithUserData(tokenCard, 1, 0));

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, null, null);

        assertEquals(1, result.size());
    }

    // ── filterByState – tradable ──────────────────────────────────────────────

    @Test
    void testFilterTradable_ReturnsCardsWithMoreThanOne() {
        // qty=2 → tradable (1 copy kept)
        List<CardWithUserData> result = filterService.filterCards(testCards, "tradable", null, null, null, null, null, null);

        // card1 has qty=2 → tradable; card4 has foilQty=2 → tradable
        assertEquals(2, result.size());
        // tradable quantities are qty-1
        result.forEach(c -> assertTrue(c.getQuantity() < 2 || c.getFoilQuantity() < 2));
    }

    @Test
    void testFilterTradable_FoilPrinting_OnlyFoilTradable() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "tradable", "foil", null, null, null, null, null);

        // Only card4 has foilQty=2
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getFoilQuantity()); // 2-1=1 tradable
    }

    @Test
    void testFilterOwned_FoilPrinting_OnlyFoilOwned() {
        // card1 has foilQty=1, card4 has foilQty=2
        List<CardWithUserData> result = filterService.filterCards(testCards, "owned", "foil", null, null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    void testFilterMissing_FoilPrinting_MissingFoil() {
        // card2 qty=0 foilQty=0, card3 qty=1 foilQty=0
        List<CardWithUserData> result = filterService.filterCards(testCards, "missing", "foil", null, null, null, null, null);

        // missing foil = foilQty == 0 → card2 and card3
        assertEquals(2, result.size());
    }

    @Test
    void testFilterState_DefaultWithFoil_DelegatesToPrintingFilter() {
        // state="unknown" (default branch) + printing="foil" → returns foil-owned cards
        List<CardWithUserData> result = filterService.filterCards(testCards, "unknown", "foil", null, null, null, null, null);

        // card1 foilQty=1, card4 foilQty=2
        assertEquals(2, result.size());
    }

    // ── filterByPrinting – no state filter ───────────────────────────────────

    @Test
    void testPrintingFoil_NoState_ReturnsOnlyFoilOwned() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", "foil", null, null, null, null, null);

        // card1 foilQty=1, card4 foilQty=2
        assertEquals(2, result.size());
    }

    @Test
    void testPrintingNonFoil_ReturnsAllCards() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", "normal", null, null, null, null, null);

        assertEquals(4, result.size());
    }

    // ── filterBySearch – numeric (collector number) ───────────────────────────

    @Test
    void testFilterBySearchNumber_MatchesCollectorNumber() {
        List<CardWithUserData> result = filterService.filterCards(testCards, "all", null, null, "2", null, null, null);

        assertEquals(1, result.size());
        assertEquals("2", result.get(0).getCard().getCollectorNumber());
    }

    // ── frameStyle – retroframe ───────────────────────────────────────────────

    @Test
    void testFrameStyle_RetroFrame_Returns1997And1993() {
        ScryfallCard retro97 = createCard("40", "Old Bolt", "uncommon");
        retro97.setFrame("1997");
        ScryfallCard retro93 = createCard("41", "Older Bolt", "uncommon");
        retro93.setFrame("1993");
        ScryfallCard modern = createCard("42", "New Bolt", "uncommon");
        modern.setFrame("2015");

        List<CardWithUserData> cards = Arrays.asList(
                createCardWithUserData(retro97, 1, 0),
                createCardWithUserData(retro93, 1, 0),
                createCardWithUserData(modern,  1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "retroframe", null);

        assertEquals(2, result.size());
    }

    // ── frameStyle – multiple comma-separated styles ──────────────────────────

    @Test
    void testFrameStyle_MultipleStyles_ReturnsUnion() {
        ScryfallCard showcase   = createCard("50", "Showcase",   "rare"); showcase.setFrameStatus("showcase");
        ScryfallCard borderless = createCard("51", "Borderless", "rare"); borderless.setBorderColor("borderless");
        ScryfallCard normal     = createCard("52", "Normal",     "rare");

        List<CardWithUserData> cards = Arrays.asList(
                createCardWithUserData(showcase,   1, 0),
                createCardWithUserData(borderless, 1, 0),
                createCardWithUserData(normal,     1, 0)
        );

        List<CardWithUserData> result = filterService.filterCards(cards, "all", null, null, null, null, "showcase,borderless", null);

        assertEquals(2, result.size());
    }

    // ── getOnlyInLeft ─────────────────────────────────────────────────────────

    @Test
    void testGetOnlyInLeft_ReturnsCardsNotInRight() {
        ScryfallCard sc1 = createCard("1", "Alpha", "rare");
        ScryfallCard sc2 = createCard("2", "Beta",  "rare");
        ScryfallCard sc3 = createCard("3", "Gamma", "rare");

        List<CardWithUserData> left  = Arrays.asList(
                createCardWithUserData(sc1, 1, 0),
                createCardWithUserData(sc2, 1, 0)
        );
        List<CardWithUserData> right = Arrays.asList(
                createCardWithUserData(sc2, 1, 0),
                createCardWithUserData(sc3, 1, 0)
        );

        List<CardWithUserData> result = filterService.getOnlyInLeft(left, right);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getCard().getCollectorNumber());
    }

    @Test
    void testGetOnlyInLeft_EmptyRight_ReturnsAllLeft() {
        ScryfallCard sc1 = createCard("1", "Alpha", "rare");
        List<CardWithUserData> left  = Arrays.asList(createCardWithUserData(sc1, 1, 0));
        List<CardWithUserData> right = Collections.emptyList();

        List<CardWithUserData> result = filterService.getOnlyInLeft(left, right);

        assertEquals(1, result.size());
    }

    @Test
    void testGetOnlyInLeft_EmptyLeft_ReturnsEmpty() {
        ScryfallCard sc1 = createCard("1", "Alpha", "rare");
        List<CardWithUserData> result = filterService.getOnlyInLeft(
                Collections.emptyList(),
                Arrays.asList(createCardWithUserData(sc1, 1, 0)));

        assertTrue(result.isEmpty());
    }
}
