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
}
