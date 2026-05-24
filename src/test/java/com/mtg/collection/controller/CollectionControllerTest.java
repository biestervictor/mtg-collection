package com.mtg.collection.controller;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.service.ScryfallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock
    private ScryfallService scryfallService;

    @InjectMocks
    private CollectionController collectionController;

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
        // A card can technically be both showcase and full_art — showcase wins by ORDER_LIST priority.
        ScryfallCard c = new ScryfallCard();
        c.setFrameStatus("showcase");
        c.setFullArt(true);
        assertEquals("Showcase", CollectionController.treatmentGroup(c));
    }
}
