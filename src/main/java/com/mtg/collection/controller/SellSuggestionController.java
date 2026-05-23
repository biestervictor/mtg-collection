package com.mtg.collection.controller;

import com.mtg.collection.service.ReportCacheService;
import com.mtg.collection.service.SellSuggestionService;
import com.mtg.collection.service.SellSuggestionService.SellSuggestion;
import com.mtg.collection.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class SellSuggestionController {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ReportCacheService reportCacheService;
    private final StatisticsService  statisticsService;

    public SellSuggestionController(ReportCacheService reportCacheService,
                                    StatisticsService statisticsService) {
        this.reportCacheService = reportCacheService;
        this.statisticsService  = statisticsService;
    }

    @GetMapping("/sell-suggestions")
    public String sellSuggestions(
            @RequestParam(required = false, defaultValue = "Victor") String user,
            Model model) {

        List<SellSuggestion> suggestions = reportCacheService.getSellSuggestions(user);

        double totalRevenue = suggestions.stream()
                .mapToDouble(SellSuggestion::getTotalValue)
                .sum();

        model.addAttribute("suggestions",  suggestions);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("selectedUser", user);
        model.addAttribute("users",        statisticsService.getDistinctUsers());

        LocalDateTime computedAt = reportCacheService.getSellComputedAt(user);
        if (computedAt != null) {
            model.addAttribute("sellComputedAt", computedAt.format(TS_FMT));
        }

        return "sell-suggestions";
    }

    /**
     * Triggers an immediate recomputation of sell suggestions (and statistics)
     * for the given user and updates the cache.
     */
    @PostMapping("/sell-suggestions/refresh")
    public String refreshSellSuggestions(
            @RequestParam(required = false, defaultValue = "Victor") String user,
            RedirectAttributes redirectAttributes) {
        try {
            reportCacheService.refreshAllForUser(user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Verkaufsvorschläge für '" + user + "' neu berechnet.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Fehler beim Neuberechnen: " + e.getMessage());
        }
        return "redirect:/sell-suggestions?user=" + user;
    }
}
