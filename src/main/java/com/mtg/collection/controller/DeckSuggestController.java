package com.mtg.collection.controller;

import com.mtg.collection.dto.DeckSuggestion;
import com.mtg.collection.model.MetaDeck;
import com.mtg.collection.repository.MetaDeckRepository;
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
import java.util.Optional;

@Controller
public class DeckSuggestController {

    private static final List<String> FORMATS =
            List.of("commander", "modern", "pioneer", "standard", "legacy");

    private final DeckSuggestService deckSuggestService;
    private final MetaDeckService metaDeckService;
    private final MetaDeckRepository metaDeckRepository;

    public DeckSuggestController(DeckSuggestService deckSuggestService,
                                  MetaDeckService metaDeckService,
                                  MetaDeckRepository metaDeckRepository) {
        this.deckSuggestService = deckSuggestService;
        this.metaDeckService = metaDeckService;
        this.metaDeckRepository = metaDeckRepository;
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
     * Detail page: GET /deck-suggest/detail?format=commander&slug=XYZ&user=Victor
     */
    @GetMapping("/deck-suggest/detail")
    public String deckDetailPage(
            @RequestParam String format,
            @RequestParam String slug,
            @RequestParam(defaultValue = "Victor") String user,
            Model model) {

        String id = format.toLowerCase() + "_" + slug;
        Optional<MetaDeck> deckOpt = metaDeckRepository.findById(id);

        model.addAttribute("selectedFormat", format.toLowerCase());
        model.addAttribute("selectedUser", user);
        model.addAttribute("formats", FORMATS);
        model.addAttribute("users", List.of("Victor", "Andre"));

        if (deckOpt.isEmpty()) {
            model.addAttribute("error", "Deck not found: " + id);
            return "deck-detail";
        }

        MetaDeck deck = deckOpt.get();

        // Re-use suggestions to get ownership + price data for this format,
        // then find the matching entry for this deck by slug
        List<DeckSuggestion> allSuggestions =
                deckSuggestService.getSuggestions(user, format.toLowerCase());
        DeckSuggestion suggestion = allSuggestions.stream()
                .filter(s -> slug.equals(s.getSlug()))
                .findFirst()
                .orElse(null);

        // Build name → MissingCardEntry lookup (O(1) per card in template)
        java.util.Map<String, com.mtg.collection.dto.MissingCardEntry> missingByName =
                suggestion == null ? java.util.Collections.emptyMap()
                : suggestion.getMissingCards().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.mtg.collection.dto.MissingCardEntry::getCardName,
                                m -> m));

        // Separate mainboard into 3 sorted groups for display
        String cmdName = deck.getCommanderName();
        List<MetaDeck.MetaDeckCard> commanderCards = deck.getMainboard().stream()
                .filter(c -> cmdName != null && c.getName().trim().equalsIgnoreCase(cmdName))
                .collect(java.util.stream.Collectors.toList());

        List<MetaDeck.MetaDeckCard> mainCards = deck.getMainboard().stream()
                .filter(c -> !DeckSuggestService.BASIC_LANDS.stream()
                        .anyMatch(b -> b.equalsIgnoreCase(c.getName().trim())))
                .filter(c -> cmdName == null || !c.getName().trim().equalsIgnoreCase(cmdName))
                .sorted(java.util.Comparator.comparing(MetaDeck.MetaDeckCard::getName))
                .collect(java.util.stream.Collectors.toList());

        List<MetaDeck.MetaDeckCard> basicCards = deck.getMainboard().stream()
                .filter(c -> DeckSuggestService.BASIC_LANDS.stream()
                        .anyMatch(b -> b.equalsIgnoreCase(c.getName().trim())))
                .sorted(java.util.Comparator.comparing(MetaDeck.MetaDeckCard::getName))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("deck", deck);
        model.addAttribute("suggestion", suggestion);
        model.addAttribute("missingByName", missingByName);
        model.addAttribute("commanderCards", commanderCards);
        model.addAttribute("mainCards", mainCards);
        model.addAttribute("basicCards", basicCards);

        return "deck-detail";
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
