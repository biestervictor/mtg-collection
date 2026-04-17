package com.mtg.collection.controller;

import com.mtg.collection.service.SellSuggestionService;
import com.mtg.collection.service.SellSuggestionService.SellSuggestion;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SellSuggestionController {

    private final SellSuggestionService sellSuggestionService;

    public SellSuggestionController(SellSuggestionService sellSuggestionService) {
        this.sellSuggestionService = sellSuggestionService;
    }

    @GetMapping("/sell-suggestions")
    public String sellSuggestions(
            @RequestParam(required = false, defaultValue = "Victor") String user,
            Model model) {

        List<SellSuggestion> suggestions = sellSuggestionService.getSuggestions(user);

        double totalRevenue = suggestions.stream()
                .mapToDouble(SellSuggestion::getTotalValue)
                .sum();

        model.addAttribute("suggestions",    suggestions);
        model.addAttribute("totalRevenue",   totalRevenue);
        model.addAttribute("selectedUser",   user);
        return "sell-suggestions";
    }
}
