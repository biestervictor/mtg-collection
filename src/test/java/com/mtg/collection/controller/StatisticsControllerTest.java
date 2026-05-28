package com.mtg.collection.controller;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.service.ReportCacheService;
import com.mtg.collection.service.ScryfallService;
import com.mtg.collection.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock private StatisticsService  statisticsService;
    @Mock private ScryfallService    scryfallService;
    @Mock private ReportCacheService reportCacheService;

    @InjectMocks
    private StatisticsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // ViewResolver that handles redirect: natively and uses a no-op for templates
        // (standaloneSetup's default InternalResourceViewResolver causes circular path for "statistics").
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(viewName.substring("redirect:".length()), true);
                    }
                    return (model, request, response) -> response.setContentType("text/html");
                })
                .build();
    }

    // ── GET /statistics (no user) ─────────────────────────────────────────────

    @Test
    void statisticsPage_noUser_addsAllStatisticsToModel() throws Exception {
        Map<String, UserStatistics> all = new LinkedHashMap<>();
        all.put("alice", new UserStatistics());
        when(reportCacheService.getAllStatistics()).thenReturn(all);

        mockMvc.perform(get("/statistics"))
                .andExpect(status().isOk())
                .andExpect(view().name("statistics"))
                .andExpect(model().attributeExists("allStatistics"));

        verify(reportCacheService).getAllStatistics();
    }

    // ── GET /statistics?user=victor ───────────────────────────────────────────

    @Test
    void statisticsPage_withUser_addsUserStatisticsToModel() throws Exception {
        when(statisticsService.getDistinctUsers()).thenReturn(List.of("victor", "alice"));
        UserStatistics stats = new UserStatistics();
        when(reportCacheService.getStatistics("victor")).thenReturn(stats);
        when(reportCacheService.getStatsComputedAt("victor")).thenReturn(LocalDateTime.of(2025, 1, 1, 10, 0));

        mockMvc.perform(get("/statistics").param("user", "victor"))
                .andExpect(status().isOk())
                .andExpect(view().name("statistics"))
                .andExpect(model().attribute("selectedUser", "victor"))
                .andExpect(model().attributeExists("userStatistics"))
                .andExpect(model().attributeExists("statsComputedAt"));
    }

    @Test
    void statisticsPage_withUser_nullComputedAt_noTimestampAttribute() throws Exception {
        when(statisticsService.getDistinctUsers()).thenReturn(List.of("victor"));
        when(reportCacheService.getStatistics("victor")).thenReturn(new UserStatistics());
        when(reportCacheService.getStatsComputedAt("victor")).thenReturn(null);

        mockMvc.perform(get("/statistics").param("user", "victor"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("statsComputedAt"));
    }

    @Test
    void statisticsPage_emptyUserParam_treatedAsNoUser() throws Exception {
        when(reportCacheService.getAllStatistics()).thenReturn(new LinkedHashMap<>());

        mockMvc.perform(get("/statistics").param("user", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("statistics"));

        verify(reportCacheService).getAllStatistics();
        verify(reportCacheService, never()).getStatistics(any());
    }

    // ── POST /statistics/refresh-sets ─────────────────────────────────────────

    @Test
    void refreshSets_noUser_redirectsToStatistics() throws Exception {
        mockMvc.perform(post("/statistics/refresh-sets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics"));

        verify(scryfallService).getAllSets(true);
    }

    @Test
    void refreshSets_withUser_redirectsToStatisticsWithUser() throws Exception {
        mockMvc.perform(post("/statistics/refresh-sets").param("user", "victor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics?user=victor"));
    }

    @Test
    void refreshSets_exceptionFromService_redirectsWithErrorFlash() throws Exception {
        doThrow(new RuntimeException("API down")).when(scryfallService).getAllSets(true);

        mockMvc.perform(post("/statistics/refresh-sets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics"));

        verify(scryfallService).getAllSets(true);
    }

    // ── POST /statistics/refresh-reports ──────────────────────────────────────

    @Test
    void refreshReports_specificUser_callsRefreshForUser() throws Exception {
        mockMvc.perform(post("/statistics/refresh-reports").param("user", "victor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics?user=victor"));

        verify(reportCacheService).refreshAllForUser("victor");
        verify(reportCacheService, never()).refreshAll();
    }

    @Test
    void refreshReports_noUser_callsRefreshAll() throws Exception {
        mockMvc.perform(post("/statistics/refresh-reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics"));

        verify(reportCacheService).refreshAll();
        verify(reportCacheService, never()).refreshAllForUser(any());
    }

    @Test
    void refreshReports_exceptionFromService_redirectsWithErrorFlash() throws Exception {
        doThrow(new RuntimeException("DB error")).when(reportCacheService).refreshAll();

        mockMvc.perform(post("/statistics/refresh-reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/statistics"));
    }

    // ── GET /statistics/missing-cards ─────────────────────────────────────────

    @Test
    void missingCards_returnsJsonFromService() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("setCode", "TST");
        response.put("standard", List.of());
        response.put("special", List.of());
        when(statisticsService.getMissingCards("victor", "TST")).thenReturn(response);

        mockMvc.perform(get("/statistics/missing-cards")
                        .param("set", "TST")
                        .param("user", "victor"))
                .andExpect(status().isOk());

        verify(statisticsService).getMissingCards("victor", "TST");
    }
}
