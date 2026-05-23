package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.service.SellSuggestionService.SellSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for pre-computed Statistics and Sell Suggestions.
 *
 * <p>On a cache miss the computation runs live and the result is stored.
 * The nightly scheduler ({@link PriceUpdateScheduler}) refreshes every entry
 * after prices and snapshots have been updated.  A "Refresh" button in the UI
 * lets users trigger an immediate recomputation.
 *
 * <p>The cache is intentionally not persisted: after a restart the first
 * request per user is slow (live computation), subsequent ones are fast.
 */
@Service
public class ReportCacheService {

    private static final Logger log = LoggerFactory.getLogger(ReportCacheService.class);

    private final StatisticsService     statisticsService;
    private final SellSuggestionService sellSuggestionService;

    private final ConcurrentHashMap<String, CacheEntry<UserStatistics>>    statsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<SellSuggestion>>> sellCache  = new ConcurrentHashMap<>();

    public ReportCacheService(StatisticsService statisticsService,
                              SellSuggestionService sellSuggestionService) {
        this.statisticsService     = statisticsService;
        this.sellSuggestionService = sellSuggestionService;
    }

    // ── Read (cache-or-compute) ───────────────────────────────────────────────

    /** Returns cached statistics for {@code user}, computing live on cache miss. */
    public UserStatistics getStatistics(String user) {
        CacheEntry<UserStatistics> entry = statsCache.get(user);
        if (entry != null) return entry.data();
        return refreshStatistics(user);
    }

    /** Returns cached sell suggestions for {@code user}, computing live on cache miss. */
    public List<SellSuggestion> getSellSuggestions(String user) {
        CacheEntry<List<SellSuggestion>> entry = sellCache.get(user);
        if (entry != null) return entry.data();
        return refreshSellSuggestions(user);
    }

    /**
     * Returns statistics for all known users from the cache (lazy per-user).
     * Used by the all-users summary table.
     */
    public Map<String, UserStatistics> getAllStatistics() {
        List<String> users = statisticsService.getDistinctUsers();
        Map<String, UserStatistics> result = new LinkedHashMap<>();
        for (String user : users) {
            result.put(user, getStatistics(user));
        }
        return result;
    }

    // ── Force-refresh ─────────────────────────────────────────────────────────

    /** Force-recomputes and caches statistics for {@code user}; returns the new value. */
    public UserStatistics refreshStatistics(String user) {
        log.info("Computing statistics for user '{}'", user);
        long t = System.currentTimeMillis();
        UserStatistics stats = statisticsService.getStatisticsForUser(user);
        statsCache.put(user, new CacheEntry<>(stats, LocalDateTime.now()));
        log.info("Statistics for '{}' computed in {} ms", user, System.currentTimeMillis() - t);
        return stats;
    }

    /** Force-recomputes and caches sell suggestions for {@code user}; returns the new value. */
    public List<SellSuggestion> refreshSellSuggestions(String user) {
        log.info("Computing sell suggestions for user '{}'", user);
        long t = System.currentTimeMillis();
        List<SellSuggestion> suggestions = sellSuggestionService.getSuggestions(user);
        sellCache.put(user, new CacheEntry<>(suggestions, LocalDateTime.now()));
        log.info("Sell suggestions for '{}' computed in {} ms", user, System.currentTimeMillis() - t);
        return suggestions;
    }

    /** Force-recomputes both reports for {@code user}. */
    public void refreshAllForUser(String user) {
        refreshStatistics(user);
        refreshSellSuggestions(user);
    }

    /** Force-recomputes both reports for every known user. Called by the nightly scheduler. */
    public void refreshAll() {
        List<String> users = statisticsService.getDistinctUsers();
        log.info("Refreshing all reports for {} user(s): {}", users.size(), users);
        for (String user : users) {
            try { refreshStatistics(user); }     catch (Exception e) { log.error("Stats refresh failed for '{}'", user, e); }
            try { refreshSellSuggestions(user); } catch (Exception e) { log.error("Sell refresh failed for '{}'", user, e); }
        }
        log.info("All reports refreshed");
    }

    // ── Timestamp accessors ───────────────────────────────────────────────────

    /** Returns when statistics for {@code user} were last computed, or {@code null} if not cached. */
    public LocalDateTime getStatsComputedAt(String user) {
        CacheEntry<UserStatistics> entry = statsCache.get(user);
        return entry != null ? entry.computedAt() : null;
    }

    /** Returns when sell suggestions for {@code user} were last computed, or {@code null} if not cached. */
    public LocalDateTime getSellComputedAt(String user) {
        CacheEntry<List<SellSuggestion>> entry = sellCache.get(user);
        return entry != null ? entry.computedAt() : null;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    public record CacheEntry<T>(T data, LocalDateTime computedAt) {}
}
