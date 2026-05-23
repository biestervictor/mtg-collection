package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.service.SellSuggestionService.SellSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportCacheServiceTest {

    @Mock StatisticsService     statisticsService;
    @Mock SellSuggestionService sellSuggestionService;

    ReportCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new ReportCacheService(statisticsService, sellSuggestionService);
    }

    // ── getStatistics ─────────────────────────────────────────────────────────

    @Test
    void getStatistics_cacheMiss_computesAndStores() {
        UserStatistics expected = new UserStatistics();
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(expected);

        UserStatistics result = cacheService.getStatistics("Victor");

        assertThat(result).isSameAs(expected);
        verify(statisticsService).getStatisticsForUser("Victor");
    }

    @Test
    void getStatistics_cacheHit_doesNotRecompute() {
        UserStatistics stats = new UserStatistics();
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(stats);

        cacheService.getStatistics("Victor"); // miss → compute
        cacheService.getStatistics("Victor"); // hit → no recompute

        verify(statisticsService, times(1)).getStatisticsForUser("Victor");
    }

    @Test
    void getStatistics_differentUsers_areStoredSeparately() {
        UserStatistics statsV = new UserStatistics();
        UserStatistics statsA = new UserStatistics();
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(statsV);
        when(statisticsService.getStatisticsForUser("Andre")).thenReturn(statsA);

        assertThat(cacheService.getStatistics("Victor")).isSameAs(statsV);
        assertThat(cacheService.getStatistics("Andre")).isSameAs(statsA);
    }

    // ── getSellSuggestions ────────────────────────────────────────────────────

    @Test
    void getSellSuggestions_cacheMiss_computesAndStores() {
        List<SellSuggestion> expected = List.of();
        when(sellSuggestionService.getSuggestions("Victor")).thenReturn(expected);

        List<SellSuggestion> result = cacheService.getSellSuggestions("Victor");

        assertThat(result).isSameAs(expected);
        verify(sellSuggestionService).getSuggestions("Victor");
    }

    @Test
    void getSellSuggestions_cacheHit_doesNotRecompute() {
        when(sellSuggestionService.getSuggestions("Victor")).thenReturn(List.of());

        cacheService.getSellSuggestions("Victor"); // miss
        cacheService.getSellSuggestions("Victor"); // hit

        verify(sellSuggestionService, times(1)).getSuggestions("Victor");
    }

    // ── refreshStatistics ─────────────────────────────────────────────────────

    @Test
    void refreshStatistics_forcesRecompute() {
        UserStatistics first  = new UserStatistics();
        UserStatistics second = new UserStatistics();
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(first, second);

        cacheService.getStatistics("Victor");        // stores first
        UserStatistics refreshed = cacheService.refreshStatistics("Victor"); // stores second

        assertThat(refreshed).isSameAs(second);
        assertThat(cacheService.getStatistics("Victor")).isSameAs(second); // cache now holds second
        verify(statisticsService, times(2)).getStatisticsForUser("Victor");
    }

    // ── refreshSellSuggestions ────────────────────────────────────────────────

    @Test
    void refreshSellSuggestions_forcesRecompute() {
        when(sellSuggestionService.getSuggestions("Victor")).thenReturn(List.of(), List.of());

        cacheService.getSellSuggestions("Victor");
        cacheService.refreshSellSuggestions("Victor");

        verify(sellSuggestionService, times(2)).getSuggestions("Victor");
    }

    // ── refreshAll ────────────────────────────────────────────────────────────

    @Test
    void refreshAll_recomputesBothReportsForAllUsers() {
        when(statisticsService.getDistinctUsers()).thenReturn(List.of("Victor", "Andre"));
        when(statisticsService.getStatisticsForUser(anyString())).thenReturn(new UserStatistics());
        when(sellSuggestionService.getSuggestions(anyString())).thenReturn(List.of());

        cacheService.refreshAll();

        verify(statisticsService).getStatisticsForUser("Victor");
        verify(statisticsService).getStatisticsForUser("Andre");
        verify(sellSuggestionService).getSuggestions("Victor");
        verify(sellSuggestionService).getSuggestions("Andre");
    }

    @Test
    void refreshAll_continuesIfOneUserFails() {
        when(statisticsService.getDistinctUsers()).thenReturn(List.of("Victor", "Andre"));
        when(statisticsService.getStatisticsForUser("Victor")).thenThrow(new RuntimeException("DB error"));
        when(statisticsService.getStatisticsForUser("Andre")).thenReturn(new UserStatistics());
        when(sellSuggestionService.getSuggestions(anyString())).thenReturn(List.of());

        // should not throw
        cacheService.refreshAll();

        verify(statisticsService).getStatisticsForUser("Andre");
        verify(sellSuggestionService).getSuggestions("Andre");
    }

    // ── getAllStatistics ──────────────────────────────────────────────────────

    @Test
    void getAllStatistics_returnsMapForAllUsers() {
        UserStatistics statsV = new UserStatistics();
        UserStatistics statsA = new UserStatistics();
        when(statisticsService.getDistinctUsers()).thenReturn(List.of("Victor", "Andre"));
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(statsV);
        when(statisticsService.getStatisticsForUser("Andre")).thenReturn(statsA);

        Map<String, UserStatistics> all = cacheService.getAllStatistics();

        assertThat(all).containsKeys("Victor", "Andre");
        assertThat(all.get("Victor")).isSameAs(statsV);
        assertThat(all.get("Andre")).isSameAs(statsA);
    }

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Test
    void getStatsComputedAt_nullBeforeCompute() {
        assertThat(cacheService.getStatsComputedAt("Victor")).isNull();
    }

    @Test
    void getStatsComputedAt_nonNullAfterCompute() {
        when(statisticsService.getStatisticsForUser("Victor")).thenReturn(new UserStatistics());
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        cacheService.getStatistics("Victor");

        LocalDateTime computedAt = cacheService.getStatsComputedAt("Victor");
        assertThat(computedAt).isNotNull().isAfter(before);
    }

    @Test
    void getSellComputedAt_nullBeforeCompute() {
        assertThat(cacheService.getSellComputedAt("Victor")).isNull();
    }

    @Test
    void getSellComputedAt_nonNullAfterCompute() {
        when(sellSuggestionService.getSuggestions("Victor")).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        cacheService.getSellSuggestions("Victor");

        LocalDateTime computedAt = cacheService.getSellComputedAt("Victor");
        assertThat(computedAt).isNotNull().isAfter(before);
    }
}
