package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.WizardGroup;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.service.CardFilterService;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ScryfallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock private ScryfallService    scryfallService;
    @Mock private CollectionService  collectionService;
    @Mock private CardFilterService  cardFilterService;
    @Mock private UserCardRepository userCardRepository;

    @InjectMocks
    private CollectionController collectionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(collectionController)
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(viewName.substring("redirect:".length()), true);
                    }
                    return (model, request, response) -> response.setContentType("text/html");
                })
                .build();
    }

    // ── GET /show ─────────────────────────────────────────────────────────────

    @Test
    void showCollection_noSetOrUser_returnsSetsOnly() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of(new ScryfallSet()));

        mockMvc.perform(get("/show"))
                .andExpect(status().isOk())
                .andExpect(view().name("show"))
                .andExpect(model().attributeExists("sets"));

        verify(collectionService, never()).getCardsWithUserData(any(), any(), any());
    }

    @Test
    void showCollection_withSetAndUser_loadsAndFiltersCards() throws Exception {
        ScryfallCard sc = new ScryfallCard();
        sc.setCollectorNumber("1"); sc.setName("Alpha");
        CardWithUserData card = new CardWithUserData(sc, 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of(card));
        when(cardFilterService.filterCards(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(card));

        mockMvc.perform(get("/show").param("set", "tst").param("user", "victor"))
                .andExpect(status().isOk())
                .andExpect(view().name("show"))
                .andExpect(model().attribute("selectedSet", "tst"))
                .andExpect(model().attribute("selectedUser", "victor"))
                .andExpect(model().attributeExists("filteredCards"));
    }

    @Test
    void showCollection_missingUser_doesNotLoadCards() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        mockMvc.perform(get("/show").param("set", "tst"))
                .andExpect(status().isOk())
                .andExpect(view().name("show"));

        verify(collectionService, never()).getCardsWithUserData(any(), any(), any());
    }

    // ── GET /compare ──────────────────────────────────────────────────────────

    @Test
    void compareCollection_noParams_returnsSetsOnly() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        mockMvc.perform(get("/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("compare"))
                .andExpect(model().attributeExists("sets"));

        verify(collectionService, never()).getCardsWithUserData(any(), any(), any());
    }

    @Test
    void compareCollection_allParams_computesDiffs() throws Exception {
        CardWithUserData cardA = new CardWithUserData(cardSc("1"), 1, 0);
        CardWithUserData cardB = new CardWithUserData(cardSc("2"), 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor",  "tst", null)).thenReturn(List.of(cardA));
        when(collectionService.getCardsWithUserData("alice",   "tst", null)).thenReturn(List.of(cardB));
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("set", "tst")
                        .param("user", "victor")
                        .param("compareUser", "alice"))
                .andExpect(status().isOk())
                .andExpect(view().name("compare"))
                .andExpect(model().attribute("selectedSet", "tst"));
    }

    @Test
    void compareCollection_zeroQtyCards_filteredBeforeDiff() throws Exception {
        // Bug regression: getCardsWithUserData returns ALL set cards including qty=0.
        // The compare must filter to owned cards before calling getOnlyInLeft,
        // otherwise every card matches by collectorNumber and results are always empty.
        ScryfallCard sc1 = cardSc("1");
        ScryfallCard sc2 = cardSc("2");
        ScryfallCard sc3 = cardSc("3");

        // Victor owns card 1; card 2 is in the set but unowned (qty=0)
        CardWithUserData victorOwns   = new CardWithUserData(sc1, 2, 0);
        CardWithUserData victorZero   = new CardWithUserData(sc2, 0, 0);
        // Andre owns card 3; card 1 is in the set but unowned (qty=0)
        CardWithUserData andreOwns    = new CardWithUserData(sc3, 1, 0);
        CardWithUserData andreZero    = new CardWithUserData(sc1, 0, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null))
                .thenReturn(List.of(victorOwns, victorZero));
        when(collectionService.getCardsWithUserData("andre",  "tst", null))
                .thenReturn(List.of(andreOwns, andreZero));
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("set", "tst")
                        .param("user", "victor")
                        .param("compareUser", "andre"))
                .andExpect(status().isOk());

        // getOnlyInLeft must be called only with the OWNED subset (qty > 0)
        verify(cardFilterService).getOnlyInLeft(
                argThat(l -> l.size() == 1 && l.get(0).getQuantity() == 2),   // only victorOwns
                argThat(l -> l.size() == 1 && l.get(0).getQuantity() == 1));  // only andreOwns
    }

    // ── POST /api/cache/clear ────────────────────────────────────────────────

    @Test
    void testClearCacheForSpecificSet() throws Exception {
        mockMvc.perform(post("/api/cache/clear").param("setCode", "3ed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/show?set=3ed"));

        verify(scryfallService).clearCache("3ed");
    }

    @Test
    void testClearAllCache() throws Exception {
        mockMvc.perform(post("/api/cache/clear"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/show"));

        verify(scryfallService).clearAllCache();
    }

    // ── POST /api/sets/refresh ───────────────────────────────────────────────

    @Test
    void testRefreshSets_callsGetAllSetsWithForceRefresh() throws Exception {
        when(scryfallService.getAllSets(true)).thenReturn(List.of());

        mockMvc.perform(post("/api/sets/refresh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/show"));

        verify(scryfallService).getAllSets(true);
    }

    // ── GET /api/set/{setCode}/top-cards ──────────────────────────────────────

    @Test
    void topCardsForSet_returnsGroupedByRarity() throws Exception {
        ScryfallCard mythic = cardSc("1"); mythic.setRarity("mythic"); mythic.setPriceRegular(25.0);
        ScryfallCard rare   = cardSc("2"); rare.setRarity("rare");     rare.setPriceRegular(5.0);
        when(scryfallService.getCardsBySet("tst", null)).thenReturn(List.of(mythic, rare));

        mockMvc.perform(get("/api/set/tst/top-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mythic").isArray())
                .andExpect(jsonPath("$.rare").isArray());
    }

    @Test
    void topCardsForSet_cardWithNullPrice_excluded() throws Exception {
        ScryfallCard noPrice = cardSc("1"); noPrice.setRarity("rare");
        // No price set — should be filtered out
        when(scryfallService.getCardsBySet("tst", null)).thenReturn(List.of(noPrice));

        mockMvc.perform(get("/api/set/tst/top-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rare").isEmpty());
    }

    @Test
    void topCardsForSet_sortedByPriceDescending() throws Exception {
        ScryfallCard cheap     = cardSc("1"); cheap.setRarity("rare");     cheap.setPriceRegular(1.0);
        ScryfallCard expensive = cardSc("2"); expensive.setRarity("rare"); expensive.setPriceRegular(50.0);
        when(scryfallService.getCardsBySet("tst", null)).thenReturn(List.of(cheap, expensive));

        mockMvc.perform(get("/api/set/tst/top-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rare[0].priceRegular").value(50.0));
    }

    // ── treatmentGroup helper ────────────────────────────────────────────────

    @Test
    void testClearCacheServiceMethods() {
        collectionController.clearCache("tla");
        verify(scryfallService).clearCache("tla");

        collectionController.clearCache(null);
        verify(scryfallService).clearAllCache();
    }

    @Test
    void treatmentGroup_nullCard_returnsNormal() {
        assertEquals("Normal", CollectionController.treatmentGroup(null));
    }

    @Test
    void treatmentGroup_noSpecialFlags_returnsNormal() {
        ScryfallCard c = new ScryfallCard();
        c.setBorderColor("black");
        c.setFullArt(false);
        assertEquals("Normal", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_showcaseFrameStatus_returnsShowcase() {
        ScryfallCard c = new ScryfallCard();
        c.setFrameStatus("showcase");
        assertEquals("Showcase", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_extendedArtInFrameStatus_returnsExtendedArt() {
        ScryfallCard c = new ScryfallCard();
        c.setFrameStatus("extendedart,legendary");
        assertEquals("Extended Art", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_borderlessColor_returnsBorderless() {
        ScryfallCard c = new ScryfallCard();
        c.setBorderColor("borderless");
        assertEquals("Borderless", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_fullArt_returnsFullArt() {
        ScryfallCard c = new ScryfallCard();
        c.setFullArt(true);
        assertEquals("Full Art", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_showcaseTakesPrecedenceOverFullArt() {
        ScryfallCard c = new ScryfallCard();
        c.setFrameStatus("showcase");
        c.setFullArt(true);
        assertEquals("Showcase", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_frame1997_returnsRetroFrame() {
        ScryfallCard c = new ScryfallCard();
        c.setFrame("1997");
        assertEquals("Retro Frame", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_frame1993_returnsRetroFrame() {
        ScryfallCard c = new ScryfallCard();
        c.setFrame("1993");
        assertEquals("Retro Frame", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_frame2015_returnsNormal() {
        ScryfallCard c = new ScryfallCard();
        c.setFrame("2015");
        assertEquals("Normal", CollectionController.treatmentGroup(c));
    }

    @Test
    void treatmentGroup_showcaseTakesPrecedenceOverRetroFrame() {
        ScryfallCard c = new ScryfallCard();
        c.setFrameStatus("showcase");
        c.setFrame("1997");
        assertEquals("Showcase", CollectionController.treatmentGroup(c));
    }

    // ── Missing Card Wizard endpoint ─────────────────────────────────────────

    private static ScryfallCard sfCard(String name, String cn, String rarity,
                                       Double price, String frameStatus, String borderColor) {
        ScryfallCard sc = new ScryfallCard();
        sc.setName(name); sc.setCollectorNumber(cn); sc.setRarity(rarity);
        sc.setPriceRegular(price); sc.setSetCode("tst");
        sc.setFrameStatus(frameStatus); sc.setBorderColor(borderColor);
        return sc;
    }

    private static ScryfallCard cardSc(String cn) {
        ScryfallCard sc = new ScryfallCard();
        sc.setCollectorNumber(cn); sc.setName("Card" + cn); sc.setSetCode("tst");
        return sc;
    }

    private static CardWithUserData missing(ScryfallCard sc) {
        CardWithUserData c = new CardWithUserData(); c.setCard(sc); c.setQuantity(0); c.setFoilQuantity(0); return c;
    }

    private static CardWithUserData owned(ScryfallCard sc) {
        CardWithUserData c = new CardWithUserData(); c.setCard(sc); c.setQuantity(1); c.setFoilQuantity(0); return c;
    }

    @Test
    void wizard_noMissingCards_returnsEmptyList() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null)).thenReturn(List.of(owned(sc)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertTrue(result.isEmpty());
    }

    @Test
    void wizard_missingCardGroupedCorrectly() {
        ScryfallCard normal   = sfCard("Alpha", "1", "rare",   2.0, null,       "black");
        ScryfallCard showcase = sfCard("Beta",  "2", "mythic", 8.0, "showcase", "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(normal), missing(showcase)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(2, result.size());
        assertEquals("Normal",   result.get(0).getGroupName());
        assertEquals("Showcase", result.get(1).getGroupName());
        assertEquals(2.0, result.get(0).getTotalCost(), 0.001);
        assertEquals(8.0, result.get(1).getTotalCost(), 0.001);
    }

    @Test
    void wizard_ownedCardsNotListed() {
        ScryfallCard ownedSc = sfCard("Alpha", "1", "rare", 2.0, null, "black");
        ScryfallCard missed  = sfCard("Beta",  "2", "rare", 3.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(owned(ownedSc), missing(missed)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(1, result.size());
        assertEquals("Beta", result.get(0).getCards().get(0).getName());
    }

    @Test
    void wizard_tradableByOtherUser_detected() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));
        when(userCardRepository.findBySetCode("tst"))
                .thenReturn(List.of(new UserCard("Andre", "Alpha", "tst", "1", 3, false)));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(List.of("Andre"), result.get(0).getCards().get(0).getTradableBy());
    }

    @Test
    void wizard_requestingUserNotListedAsTradable() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));
        when(userCardRepository.findBySetCode("tst"))
                .thenReturn(List.of(new UserCard("Victor", "Alpha", "tst", "1", 3, false)));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertTrue(result.get(0).getCards().get(0).getTradableBy().isEmpty());
    }

    @Test
    void wizard_tradableRequiresMoreThanOneCopy() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));
        when(userCardRepository.findBySetCode("tst"))
                .thenReturn(List.of(new UserCard("Andre", "Alpha", "tst", "1", 1, false)));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertTrue(result.get(0).getCards().get(0).getTradableBy().isEmpty());
    }

    @Test
    void wizard_totalCostSumsOnlyPriceRegular() {
        ScryfallCard c1 = sfCard("Alpha", "1", "rare",     3.0, null, "black");
        ScryfallCard c2 = sfCard("Beta",  "2", "uncommon", null, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(c1), missing(c2)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(3.0, result.get(0).getTotalCost(), 0.001);
    }

    // ── GET /compare – onlyTradable + viewMode params ────────────────────────

    @Test
    void compareCollection_defaultParams_onlyTradableModelAttributesAreTrue() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        mockMvc.perform(get("/compare"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("onlyTradableUser",    true))
                .andExpect(model().attribute("onlyTradableCompare", true))
                .andExpect(model().attribute("viewMode",            "normal"));
    }

    @Test
    void compareCollection_explicitViewMode_passedToModel() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        mockMvc.perform(get("/compare").param("viewMode", "text"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("viewMode", "text"));
    }

    @Test
    void compareCollection_onlyTradableUserTrue_callsFilterTradableForUser() throws Exception {
        CardWithUserData tradable = new CardWithUserData(cardSc("1"), 2, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of(tradable));
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of(tradable), List.of());
        when(cardFilterService.filterTradable(any())).thenReturn(List.of(tradable));

        mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "true")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk());

        // filterTradable called exactly once (for the user side), not for compare
        verify(cardFilterService, times(1)).filterTradable(any());
    }

    @Test
    void compareCollection_onlyTradableCompareFalse_filterTradableNotCalledForCompare() throws Exception {
        CardWithUserData card = new CardWithUserData(cardSc("5"), 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of(card));
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of(), List.of(card));

        mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk());

        // neither side requests tradable filtering → filterTradable never invoked
        verify(cardFilterService, never()).filterTradable(any());
    }

    @Test
    void compareCollection_bothTradableTrue_filterTradableCalledTwice() throws Exception {
        CardWithUserData userCard    = new CardWithUserData(cardSc("1"), 2, 0);
        CardWithUserData compareCard = new CardWithUserData(cardSc("2"), 3, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of(userCard));
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of(compareCard));
        when(cardFilterService.getOnlyInLeft(any(), any()))
                .thenReturn(List.of(userCard), List.of(compareCard));
        when(cardFilterService.filterTradable(any())).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "true")
                        .param("onlyTradableCompare", "true"))
                .andExpect(status().isOk());

        verify(cardFilterService, times(2)).filterTradable(any());
    }

    // ── GET /compare – token-filter + treatment dividers ─────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void compareCollection_tokenCardsFilteredOut() throws Exception {
        // Token-Karte (typeLine enthaelt "Token") soll NICHT auf der Compare-Seite landen
        ScryfallCard token = cardSc("99");
        token.setTypeLine("Token Creature - Soldier");
        ScryfallCard normal = cardSc("1");
        normal.setTypeLine("Creature - Human");

        CardWithUserData tokenCard  = new CardWithUserData(token,  1, 0);
        CardWithUserData normalCard = new CardWithUserData(normal, 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null))
                .thenReturn(List.of(tokenCard, normalCard));
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());
        // getOnlyInLeft gibt beide zurueck (token + normal); danach muss der Controller
        // den Token via filterOutTokens entfernen.
        when(cardFilterService.getOnlyInLeft(any(), any()))
                .thenReturn(List.of(tokenCard, normalCard), List.of());

        var result = mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk())
                .andReturn();

        List<CardWithUserData> onlyUser = (List<CardWithUserData>)
                result.getModelAndView().getModel().get("onlyUser");
        assertNotNull(onlyUser);
        assertEquals(1, onlyUser.size(), "Token-Karte muss herausgefiltert worden sein");
        assertEquals("1", onlyUser.get(0).getCard().getCollectorNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareCollection_dividersAddedToModel() throws Exception {
        // Zwei Karten in unterschiedlichen Treatment-Gruppen → 2 Divider erwartet
        ScryfallCard normal = cardSc("1");
        normal.setTypeLine("Creature");
        ScryfallCard showcase = cardSc("2");
        showcase.setTypeLine("Creature");
        showcase.setFrameStatus("showcase");

        CardWithUserData c1 = new CardWithUserData(normal,   1, 0);
        CardWithUserData c2 = new CardWithUserData(showcase, 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null))
                .thenReturn(List.of(c1, c2));
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());
        when(cardFilterService.getOnlyInLeft(any(), any()))
                .thenReturn(List.of(c1, c2), List.of());

        var result = mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("userDividers"))
                .andExpect(model().attributeExists("compareDividers"))
                .andReturn();

        java.util.Map<Integer, ?> userDividers = (java.util.Map<Integer, ?>)
                result.getModelAndView().getModel().get("userDividers");
        // Zwei Gruppen → zwei Divider-Eintraege bei Index 0 und 1
        assertEquals(2, userDividers.size());
        assertTrue(userDividers.containsKey(0));
        assertTrue(userDividers.containsKey(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareCollection_emptyResults_dividersEmptyMaps() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of());

        var result = mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk())
                .andReturn();

        java.util.Map<Integer, ?> userDividers = (java.util.Map<Integer, ?>)
                result.getModelAndView().getModel().get("userDividers");
        java.util.Map<Integer, ?> compareDividers = (java.util.Map<Integer, ?>)
                result.getModelAndView().getModel().get("compareDividers");
        assertTrue(userDividers.isEmpty());
        assertTrue(compareDividers.isEmpty());
    }

    // ── GET /compare – Show Tokens / Show Promos ─────────────────────────────

    @Test
    void compareCollection_showTokensFalse_noTokenSetLookup() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData(any(), any(), any())).thenReturn(List.of());
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("tokenOnlyUser"))
                .andExpect(model().attributeDoesNotExist("promoOnlyUser"));

        // Token/Promo-Set-Cards duerfen ohne Toggle nicht geladen werden
        verify(scryfallService, never()).getCardsBySet("ttst", null);
        verify(scryfallService, never()).getCardsBySet("ptst", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareCollection_showTokensTrue_loadsTokenSetAndComputesDiff() throws Exception {
        // Token-Set "ttst" exists with a card victor owns but alice doesn't
        ScryfallCard tokenScryfall = cardSc("1");
        tokenScryfall.setSetCode("ttst");
        CardWithUserData victorToken = new CardWithUserData(tokenScryfall, 1, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        // Regular set "tst": both users empty
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());

        // Token-Set "ttst" exists (non-empty)
        when(scryfallService.getCardsBySet("ttst", null)).thenReturn(List.of(tokenScryfall));
        when(collectionService.getCardsWithUserData("victor", "ttst", null))
                .thenReturn(List.of(victorToken));
        when(collectionService.getCardsWithUserData("alice",  "ttst", null))
                .thenReturn(List.of());

        when(cardFilterService.getOnlyInLeft(any(), any()))
                .thenReturn(List.of())                  // regular set: no diff
                .thenReturn(List.of())
                .thenReturn(List.of(victorToken))       // token: victor has it, alice doesn't
                .thenReturn(List.of());

        var result = mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false")
                        .param("showTokens",  "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("tokenOnlyUser"))
                .andExpect(model().attributeExists("tokenOnlyCompare"))
                .andReturn();

        List<CardWithUserData> tokenOnlyUser = (List<CardWithUserData>)
                result.getModelAndView().getModel().get("tokenOnlyUser");
        assertEquals(1, tokenOnlyUser.size());
        assertEquals("1", tokenOnlyUser.get(0).getCard().getCollectorNumber());

        verify(scryfallService).getCardsBySet("ttst", null);
    }

    @Test
    void compareCollection_showTokensTrueButTokenSetEmpty_noModelAttributes() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData(any(), any(), any())).thenReturn(List.of());
        when(cardFilterService.getOnlyInLeft(any(), any())).thenReturn(List.of());
        // Token-Set "ttst" leer → keine Token-Diff-Berechnung
        when(scryfallService.getCardsBySet("ttst", null)).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false")
                        .param("showTokens",  "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("tokenOnlyUser"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareCollection_showPromosTrue_loadsPromoSetAndComputesDiff() throws Exception {
        ScryfallCard promoScryfall = cardSc("1");
        promoScryfall.setSetCode("ptst");
        CardWithUserData alicePromo = new CardWithUserData(promoScryfall, 2, 0);

        when(scryfallService.getAllSets(false)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("victor", "tst", null)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("alice",  "tst", null)).thenReturn(List.of());
        when(scryfallService.getCardsBySet("ptst", null)).thenReturn(List.of(promoScryfall));
        when(collectionService.getCardsWithUserData("victor", "ptst", null)).thenReturn(List.of());
        when(collectionService.getCardsWithUserData("alice",  "ptst", null)).thenReturn(List.of(alicePromo));

        when(cardFilterService.getOnlyInLeft(any(), any()))
                .thenReturn(List.of())                  // regular: no diff
                .thenReturn(List.of())
                .thenReturn(List.of())                  // promo: victor has none
                .thenReturn(List.of(alicePromo));       // promo: alice has 1

        var result = mockMvc.perform(get("/compare")
                        .param("set",         "tst")
                        .param("user",        "victor")
                        .param("compareUser", "alice")
                        .param("onlyTradableUser",    "false")
                        .param("onlyTradableCompare", "false")
                        .param("showPromos",  "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("promoOnlyCompare"))
                .andReturn();

        List<CardWithUserData> promoOnlyCompare = (List<CardWithUserData>)
                result.getModelAndView().getModel().get("promoOnlyCompare");
        assertEquals(1, promoOnlyCompare.size());

        verify(scryfallService).getCardsBySet("ptst", null);
    }

    @Test
    void compareCollection_modelAttributesPropagateShowTogglesEvenWithoutResults() throws Exception {
        when(scryfallService.getAllSets(false)).thenReturn(List.of());

        mockMvc.perform(get("/compare")
                        .param("showTokens", "true")
                        .param("showPromos", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showTokens", "true"))
                .andExpect(model().attribute("showPromos", "true"));
    }
}
