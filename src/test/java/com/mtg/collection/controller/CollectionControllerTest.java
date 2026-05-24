package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.WizardGroup;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.service.CardFilterService;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ScryfallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock private ScryfallService    scryfallService;
    @Mock private CollectionService  collectionService;
    @Mock private CardFilterService  cardFilterService;
    @Mock private UserCardRepository userCardRepository;

    @InjectMocks
    private CollectionController collectionController;

    // ── cache-clear endpoints ────────────────────────────────────────────────

    @Test
    void testClearCacheForSpecificSet() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(collectionController).build();

        mockMvc.perform(post("/api/cache/clear").param("setCode", "3ed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/show?set=3ed"));

        verify(scryfallService).clearCache("3ed");
    }

    @Test
    void testClearAllCache() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(collectionController).build();

        mockMvc.perform(post("/api/cache/clear"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/show"));

        verify(scryfallService).clearAllCache();
    }

    @Test
    void testClearCacheServiceMethods() {
        collectionController.clearCache("tla");
        verify(scryfallService).clearCache("tla");

        collectionController.clearCache(null);
        verify(scryfallService).clearAllCache();
    }

    // ── treatmentGroup helper ────────────────────────────────────────────────

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

    /** Helper: ScryfallCard with minimal fields. */
    private static ScryfallCard sfCard(String name, String cn, String rarity,
                                       Double price, String frameStatus, String borderColor) {
        ScryfallCard sc = new ScryfallCard();
        sc.setName(name);
        sc.setCollectorNumber(cn);
        sc.setRarity(rarity);
        sc.setPriceRegular(price);
        sc.setSetCode("tst");
        sc.setFrameStatus(frameStatus);
        sc.setBorderColor(borderColor);
        return sc;
    }

    /** Helper: missing card (qty = 0). */
    private static CardWithUserData missing(ScryfallCard sc) {
        CardWithUserData c = new CardWithUserData();
        c.setCard(sc);
        c.setQuantity(0);
        c.setFoilQuantity(0);
        return c;
    }

    /** Helper: owned card (qty = 1). */
    private static CardWithUserData owned(ScryfallCard sc) {
        CardWithUserData c = new CardWithUserData();
        c.setCard(sc);
        c.setQuantity(1);
        c.setFoilQuantity(0);
        return c;
    }

    @Test
    void wizard_noMissingCards_returnsEmptyList() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(owned(sc)));
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
        ScryfallCard owned  = sfCard("Alpha", "1", "rare", 2.0, null, "black");
        ScryfallCard missed = sfCard("Beta",  "2", "rare", 3.0, null, "black");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(owned(owned), missing(missed)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCards().size());
        assertEquals("Beta", result.get(0).getCards().get(0).getName());
    }

    @Test
    void wizard_tradableByOtherUser_detected() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));

        // Andre owns 3 copies → 2 tradable
        UserCard andre = new UserCard("Andre", "Alpha", "tst", "1", 3, false);
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of(andre));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertFalse(result.isEmpty());
        List<String> traders = result.get(0).getCards().get(0).getTradableBy();
        assertEquals(1, traders.size());
        assertEquals("Andre", traders.get(0));
    }

    @Test
    void wizard_requestingUserNotListedAsTradable() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));

        // Victor himself has 3 copies — must be excluded
        UserCard victor = new UserCard("Victor", "Alpha", "tst", "1", 3, false);
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of(victor));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertTrue(result.get(0).getCards().get(0).getTradableBy().isEmpty(),
                "The requesting user must not appear as tradable");
    }

    @Test
    void wizard_tradableRequiresMoreThanOneCopy() {
        ScryfallCard sc = sfCard("Alpha", "1", "rare", 5.0, null, "black");

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(sc)));

        // Andre has exactly 1 copy — not tradable
        UserCard andre = new UserCard("Andre", "Alpha", "tst", "1", 1, false);
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of(andre));

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertTrue(result.get(0).getCards().get(0).getTradableBy().isEmpty(),
                "Exactly 1 copy is not tradable (need > 1)");
    }

    @Test
    void wizard_totalCostSumsOnlyPriceRegular() {
        ScryfallCard c1 = sfCard("Alpha", "1", "rare",    3.0,  null, "black");
        ScryfallCard c2 = sfCard("Beta",  "2", "uncommon", null, null, "black"); // no price

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(missing(c1), missing(c2)));
        when(userCardRepository.findBySetCode("tst")).thenReturn(List.of());

        List<WizardGroup> result = collectionController.getMissingWizard("tst", "Victor");
        assertEquals(1, result.size());
        assertEquals(3.0, result.get(0).getTotalCost(), 0.001,
                "Null price should be treated as 0");
    }
}
