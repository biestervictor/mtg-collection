package com.mtg.collection.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtg.collection.dto.*;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.TradeWizardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TradeWizardControllerTest {

    @Mock private CollectionService collectionService;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Real service, mocked collection layer → exercises full request → service → response path.
        TradeWizardService service = new TradeWizardService(collectionService);
        TradeWizardController controller = new TradeWizardController(service);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    // ── Happy paths ─────────────────────────────────────────────────────

    @Test
    void greedyMode_returnsPairsAndFairness() throws Exception {
        // Victor: 2× Bolt @ 1.50 EUR  (Andre: 0)
        // Andre:  2× Path  @ 1.40 EUR (Victor: 0)
        ScryfallCard bolt = card("v1", "Lightning Bolt", 1.50);
        ScryfallCard path = card("a1", "Path to Exile",  1.40);

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(new CardWithUserData(bolt, 2, 0),
                                    new CardWithUserData(path, 0, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(new CardWithUserData(bolt, 0, 0),
                                    new CardWithUserData(path, 2, 0)));

        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tst"), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("greedy"))
                .andExpect(jsonPath("$.pairs.length()").value(1))
                .andExpect(jsonPath("$.pairs[0].fromA.name").value("Lightning Bolt"))
                .andExpect(jsonPath("$.pairs[0].fromB.name").value("Path to Exile"))
                .andExpect(jsonPath("$.totalA").value(1.50))
                .andExpect(jsonPath("$.totalB").value(1.40))
                .andExpect(jsonPath("$.fairnessScore").exists());
    }

    @Test
    void bundleMode_returnsAllCardsAndSums() throws Exception {
        ScryfallCard mox = card("v1", "Mox",  100.0);
        ScryfallCard lot = card("a1", "Lotus", 95.0);

        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(new CardWithUserData(mox, 2, 0),
                                    new CardWithUserData(lot, 0, 0)));
        when(collectionService.getCardsWithUserData("Andre",  "tst", null))
                .thenReturn(List.of(new CardWithUserData(mox, 0, 0),
                                    new CardWithUserData(lot, 2, 0)));

        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tst"), "bundle", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("bundle"))
                .andExpect(jsonPath("$.bundle.aSide.length()").value(1))
                .andExpect(jsonPath("$.bundle.bSide.length()").value(1))
                .andExpect(jsonPath("$.totalA").value(100.0))
                .andExpect(jsonPath("$.totalB").value(95.0));
    }

    @Test
    void multiSet_unionsPoolsAcrossSets() throws Exception {
        ScryfallCard c1 = card("v1", "C1", 5.0);
        ScryfallCard c2 = card("v2", "C2", 6.0);
        ScryfallCard c3 = card("a1", "C3", 11.0);

        when(collectionService.getCardsWithUserData("Victor", "tdm", null))
                .thenReturn(List.of(new CardWithUserData(c1, 2, 0)));
        when(collectionService.getCardsWithUserData("Victor", "dmu", null))
                .thenReturn(List.of(new CardWithUserData(c2, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tdm", null))
                .thenReturn(List.of(new CardWithUserData(c3, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "dmu", null))
                .thenReturn(List.of());

        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tdm", "dmu"), "bundle", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundle.aSide.length()").value(1))  // Rarity-based 1:1 matching
                .andExpect(jsonPath("$.bundle.bSide.length()").value(1))
                .andExpect(jsonPath("$.skippedA.length()").value(1));  // One card skipped (no match in B)
    }

    @Test
    void belowMinValue_cardSkippedWithReason() throws Exception {
        ScryfallCard cheap = card("v1", "Cheapo", 0.10);
        when(collectionService.getCardsWithUserData("Victor", "tst", null))
                .thenReturn(List.of(new CardWithUserData(cheap, 2, 0)));
        when(collectionService.getCardsWithUserData("Andre", "tst", null))
                .thenReturn(List.of());

        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tst"), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs.length()").value(0))
                .andExpect(jsonPath("$.skippedA.length()").value(1))
                .andExpect(jsonPath("$.skippedA[0].reason").value("below_min_value"));
    }

    // ── Validation errors ───────────────────────────────────────────────

    @Test
    void emptySets_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of(), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidMode_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tst"), "linear-programming", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUserA_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "Hackerman", "Andre", List.of("tst"), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameUserAandB_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Victor", List.of("tst"), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankUserA_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "", "Andre", List.of("tst"), "greedy", 15.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toleranceOutOfRange_returns400() throws Exception {
        TradeWizardRequest req = new TradeWizardRequest(
                "Victor", "Andre", List.of("tst"), "greedy", 150.0, 0.50, false, false);

        mockMvc.perform(post("/api/compare/trade-wizard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static ScryfallCard card(String id, String name, Double price) {
        ScryfallCard sc = new ScryfallCard();
        sc.setId(id);
        sc.setName(name);
        sc.setSetCode("tst");
        sc.setCollectorNumber("1");
        sc.setPriceRegular(price);
        sc.setRarity("rare");  // Required for bundle matching
        return sc;
    }
}
