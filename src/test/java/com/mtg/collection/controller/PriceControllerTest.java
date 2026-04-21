package com.mtg.collection.controller;

import com.mtg.collection.service.PriceHistoryService;
import com.mtg.collection.service.PriceUpdateService;
import com.mtg.collection.service.ScryfallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PriceControllerTest {

    @Mock private PriceUpdateService  priceUpdateService;
    @Mock private ScryfallService     scryfallService;
    @Mock private PriceHistoryService priceHistoryService;

    @InjectMocks
    private PriceController priceController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(priceController).build();
    }

    private Map<String, Object> fullUpdateResult(int total, Map<String, Integer> perUser) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalUpdated", total);
        m.put("perUser", perUser);
        return m;
    }

    @Test
    void triggerPriceUpdate_returns200WithTotals() throws Exception {
        when(priceUpdateService.runFullUpdate())
                .thenReturn(fullUpdateResult(10, Map.of("Andre", 3, "Victor", 7)));
        when(priceHistoryService.snapshotOwnedCardPrices()).thenReturn(42);

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalUpdated").value(10))
                .andExpect(jsonPath("$.snapped").value(42));

        verify(priceUpdateService).runFullUpdate();
        verify(priceHistoryService).snapshotOwnedCardPrices();
    }

    @Test
    void triggerPriceUpdate_noCardsUpdated_returns200WithZero() throws Exception {
        when(priceUpdateService.runFullUpdate())
                .thenReturn(fullUpdateResult(0, Map.of("Andre", 0, "Victor", 0)));
        when(priceHistoryService.snapshotOwnedCardPrices()).thenReturn(0);

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUpdated").value(0));
    }

    @Test
    void triggerPriceUpdate_noUsers_returns200WithEmptyPerUser() throws Exception {
        when(priceUpdateService.runFullUpdate()).thenReturn(fullUpdateResult(0, Map.of()));
        when(priceHistoryService.snapshotOwnedCardPrices()).thenReturn(0);

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUpdated").value(0));
    }
}
