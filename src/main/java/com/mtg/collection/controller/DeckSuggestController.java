package com.mtg.collection.controller;

import com.mtg.collection.dto.DeckSuggestion;
import com.mtg.collection.service.DeckSuggestService;
import com.mtg.collection.service.MetaDeckService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class DeckSuggestController {

    private static final List<String> FORMATS =
            List.of("commander", "modern", "pioneer", "standard", "legacy");

    private final DeckSuggestService deckSuggestService;
    private final MetaDeckService metaDeckService;

    public DeckSuggestController(DeckSuggestService deckSuggestService,
                                  MetaDeckService metaDeckService) {
        this.deckSuggestService = deckSuggestService;
        this.metaDeckService = metaDeckService;
    }

    /**
     * Main page: GET /deck-suggest?format=commander&user=Victor
     */
    @GetMapping("/deck-suggest")
    public String deckSuggestPage(
            @RequestParam(defaultValue = "commander") String format,
            @RequestParam(defaultValue = "Victor")    String user,
            Model model) {

        String normalisedFormat = format.toLowerCase();

        List<DeckSuggestion> suggestions =
                deckSuggestService.getSuggestions(user, normalisedFormat);

        model.addAttribute("suggestions", suggestions);
        model.addAttribute("selectedFormat", normalisedFormat);
        model.addAttribute("selectedUser", user);
        model.addAttribute("formats", FORMATS);
        model.addAttribute("users", List.of("Victor", "Andre"));

        return "deck-suggest";
    }

    /**
     * Force-refresh the meta-deck cache for a given format.
     * POST /api/meta-decks/refresh?format=commander
     */
    @PostMapping("/api/meta-decks/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshMetaDecks(
            @RequestParam(defaultValue = "commander") String format) {

        int count = metaDeckService.refreshMetaDecks(format.toLowerCase()).size();

        return ResponseEntity.ok(Map.of(
                "format", format.toLowerCase(),
                "count",  count
        ));
    }
}
