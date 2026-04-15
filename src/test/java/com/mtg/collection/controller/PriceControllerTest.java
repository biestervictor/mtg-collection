package com.mtg.collection.controller;

import com.mtg.collection.service.PriceUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PriceControllerTest {

    @Mock
    private PriceUpdateService priceUpdateService;

    @InjectMocks
    private PriceController priceController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(priceController).build();
    }

    @Test
    void triggerPriceUpdate_returns200WithTotals() throws Exception {
        when(priceUpdateService.runUpdateForAllUsers())
                .thenReturn(Map.of("Andre", 3, "Victor", 7));

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalUpdated").value(10))
                .andExpect(jsonPath("$.perUser.Andre").value(3))
                .andExpect(jsonPath("$.perUser.Victor").value(7));

        verify(priceUpdateService).runUpdateForAllUsers();
    }

    @Test
    void triggerPriceUpdate_noCardsUpdated_returns200WithZero() throws Exception {
        when(priceUpdateService.runUpdateForAllUsers())
                .thenReturn(Map.of("Andre", 0, "Victor", 0));

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUpdated").value(0));
    }

    @Test
    void triggerPriceUpdate_noUsers_returns200WithEmptyPerUser() throws Exception {
        when(priceUpdateService.runUpdateForAllUsers()).thenReturn(Map.of());

        mockMvc().perform(post("/api/prices/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUpdated").value(0));
    }
}
