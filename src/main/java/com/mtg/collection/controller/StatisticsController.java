package com.mtg.collection.controller;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.service.ReportCacheService;
import com.mtg.collection.service.ScryfallService;
import com.mtg.collection.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StatisticsController {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StatisticsService   statisticsService;
    private final ScryfallService     scryfallService;
    private final ReportCacheService  reportCacheService;

    public StatisticsController(StatisticsService statisticsService,
                                ScryfallService scryfallService,
                                ReportCacheService reportCacheService) {
        this.statisticsService  = statisticsService;
        this.scryfallService    = scryfallService;
        this.reportCacheService = reportCacheService;
    }

    @GetMapping("/statistics")
    public String statisticsPage(Model model, @RequestParam(required = false) String user) {

        if (user != null && !user.isEmpty()) {
            // User selected: serve from cache (compute live on cache miss)
            List<String> users = statisticsService.getDistinctUsers();
            Map<String, UserStatistics> allStats = new LinkedHashMap<>();
            for (String u : users) allStats.put(u, null); // keys only for dropdown
            model.addAttribute("allStatistics", allStats);

            UserStatistics stats = reportCacheService.getStatistics(user);
            model.addAttribute("userStatistics", stats);
            model.addAttribute("selectedUser", user);

            LocalDateTime computedAt = reportCacheService.getStatsComputedAt(user);
            if (computedAt != null) {
                model.addAttribute("statsComputedAt", computedAt.format(TS_FMT));
            }
        } else {
            // No user selected: summary table for all users (served from cache)
            Map<String, UserStatistics> allStats = reportCacheService.getAllStatistics();
            model.addAttribute("allStatistics", allStats);
        }

        return "statistics";
    }

    /**
     * Forces a fresh fetch of all set metadata (incl. cardCount) from Scryfall.
     * Fixes stale cardCount values that cause sets to show as 100% complete when
     * Scryfall has since added cards (e.g. promo variants added after initial import).
     */
    @PostMapping("/statistics/refresh-sets")
    public String refreshSets(@RequestParam(required = false) String user,
                              RedirectAttributes redirectAttributes) {
        try {
            scryfallService.getAllSets(true); // forceRefresh = true
            redirectAttributes.addFlashAttribute("successMessage",
                    "Set counts refreshed from Scryfall. Completion percentages are now up to date.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to refresh set counts: " + e.getMessage());
        }
        if (user != null && !user.isEmpty()) {
            return "redirect:/statistics?user=" + user;
        }
        return "redirect:/statistics";
    }

    /**
     * Triggers an immediate recomputation of Statistics (and Sell Suggestions)
     * for the given user (or all users if none specified) and updates the cache.
     */
    @PostMapping("/statistics/refresh-reports")
    public String refreshReports(@RequestParam(required = false) String user,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (user != null && !user.isEmpty()) {
                reportCacheService.refreshAllForUser(user);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Reports for '" + user + "' recomputed successfully.");
            } else {
                reportCacheService.refreshAll();
                redirectAttributes.addFlashAttribute("successMessage",
                        "Reports for all users recomputed successfully.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to refresh reports: " + e.getMessage());
        }
        if (user != null && !user.isEmpty()) {
            return "redirect:/statistics?user=" + user;
        }
        return "redirect:/statistics";
    }

    /**
     * AJAX endpoint: returns missing cards for a given user + set, split into standard and special-frame.
     */
    @GetMapping("/statistics/missing-cards")
    @ResponseBody
    public Map<String, Object> missingCards(@RequestParam String set,
                                             @RequestParam String user) {
        return statisticsService.getMissingCards(user, set);
    }
}
