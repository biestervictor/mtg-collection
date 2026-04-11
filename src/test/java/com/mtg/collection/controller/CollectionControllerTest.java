package com.mtg.collection.controller;

import com.mtg.collection.service.ScryfallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
}
