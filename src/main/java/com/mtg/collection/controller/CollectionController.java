package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.TreatmentGroupStat;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.service.CardFilterService;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ScryfallService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CollectionController {

    private final CollectionService collectionService;
    private final ScryfallService scryfallService;
    private final CardFilterService cardFilterService;

    public CollectionController(CollectionService collectionService,
                              ScryfallService scryfallService,
                              CardFilterService cardFilterService) {
        this.collectionService = collectionService;
        this.scryfallService = scryfallService;
        this.cardFilterService = cardFilterService;
    }

    @GetMapping("/show")
    public String showCollection(Model model,
                                @RequestParam(required = false) String set,
                                @RequestParam(required = false) String user,
                                @RequestParam(required = false, defaultValue = "all") String state,
                                @RequestParam(required = false) String rarity,
                                @RequestParam(required = false) String printing,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) String showBasics,
                                @RequestParam(required = false) String frameStyle,
                                @RequestParam(required = false) String hideTokens) {
        
        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);

        model.addAttribute("state", state);
        model.addAttribute("rarity", rarity);
        model.addAttribute("printing", printing);
        model.addAttribute("search", search);
        model.addAttribute("showBasics", showBasics);
        model.addAttribute("frameStyle", frameStyle);
        model.addAttribute("hideTokens", hideTokens);

        if (set != null && !set.isEmpty() && user != null && !user.isEmpty()) {
            List<CardWithUserData> cards = collectionService.getCardsWithUserData(user, set, null);
            List<CardWithUserData> filteredCards = cardFilterService.filterCards(cards, state, printing, rarity, search, showBasics, frameStyle, hideTokens);

            // Sort filtered results by treatment group so Normal comes first, then Showcase, etc.
            List<CardWithUserData> sortedCards = sortByTreatmentGroup(filteredCards);

            // Build per-group stats from the UNFILTERED full-set cards so the divider always
            // shows the set-wide total and missing count, regardless of active filters.
            Map<Integer, TreatmentGroupStat> groupDividers = computeGroupDividers(sortedCards, cards);

            model.addAttribute("selectedSet", set);
            model.addAttribute("selectedUser", user);
            model.addAttribute("cards", cards);
            model.addAttribute("filteredCards", sortedCards);
            model.addAttribute("groupDividers", groupDividers);
            model.addAttribute("filteredCount", sortedCards.size());
            model.addAttribute("totalCount", cards.size());
        } else {
            model.addAttribute("selectedSet", set);
            model.addAttribute("selectedUser", user);
        }

        return "show";
    }

    @GetMapping("/compare")
    public String compareCollection(Model model,
                                   @RequestParam(required = false) String set,
                                   @RequestParam(required = false) String user,
                                   @RequestParam(required = false) String compareUser) {
        
        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);

        if (set != null && !set.isEmpty() && user != null && !user.isEmpty() && 
            compareUser != null && !compareUser.isEmpty()) {
            
            List<CardWithUserData> userCards = collectionService.getCardsWithUserData(user, set, null);
            List<CardWithUserData> compareCards = collectionService.getCardsWithUserData(compareUser, set, null);
            
            List<CardWithUserData> onlyUser = cardFilterService.getOnlyInLeft(userCards, compareCards);
            List<CardWithUserData> onlyCompare = cardFilterService.getOnlyInLeft(compareCards, userCards);
            
            model.addAttribute("selectedSet", set);
            model.addAttribute("compareUser", compareUser);
            model.addAttribute("onlyUser", onlyUser);
            model.addAttribute("onlyCompare", onlyCompare);
        }

        return "compare";
    }
    
    @PostMapping("/api/cache/clear")
    public String clearCache(@RequestParam(required = false) String setCode) {
        if (setCode != null && !setCode.isEmpty()) {
            scryfallService.clearCache(setCode);
            return "redirect:/show?set=" + setCode;
        } else {
            scryfallService.clearAllCache();
            return "redirect:/show";
        }
    }

    @GetMapping("/api/set/{setCode}/top-cards")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> getTopCardsForSet(@PathVariable String setCode) {
        List<ScryfallCard> allCards = scryfallService.getCardsBySet(setCode, null);

        String[] rarities = {"mythic", "rare", "uncommon", "common"};
        Map<String, List<ScryfallCard>> byRarity = new LinkedHashMap<>();

        for (String rarity : rarities) {
            final String r = rarity;
            List<ScryfallCard> group = allCards.stream()
                    .filter(c -> r.equalsIgnoreCase(c.getRarity()))
                    .filter(c -> c.getPriceRegular() != null || c.getPriceFoil() != null)
                    .sorted((a, b) -> Double.compare(maxCardPrice(b), maxCardPrice(a)))
                    .collect(Collectors.toList());
            byRarity.put(rarity, group);
        }

        // If a lower rarity's top 10 are all < 10 cents → give +5 extra slots to the next higher rarity
        int[] limits = {10, 10, 10, 10};
        for (int i = rarities.length - 1; i >= 1; i--) {
            List<ScryfallCard> top10 = byRarity.get(rarities[i]).stream().limit(10).collect(Collectors.toList());
            boolean allCheap = !top10.isEmpty() && top10.stream().allMatch(c -> maxCardPrice(c) < 0.10);
            if (allCheap) limits[i - 1] += 5;
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < rarities.length; i++) {
            final int limit = limits[i];
            String rarity = rarities[i];
            List<Map<String, Object>> cardList = byRarity.get(rarity).stream()
                    .limit(limit)
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", c.getName());
                        m.put("thumbnail", c.getThumbnailFront());
                        m.put("priceRegular", c.getPriceRegular());
                        m.put("priceFoil", c.getPriceFoil());
                        return m;
                    })
                    .collect(Collectors.toList());
            result.put(rarity, cardList);
        }
        return result;
    }

    private double maxCardPrice(ScryfallCard c) {
        double r = c.getPriceRegular() != null ? c.getPriceRegular() : 0;
        double f = c.getPriceFoil() != null ? c.getPriceFoil() : 0;
        return Math.max(r, f);
    }

    // ── Treatment-group helpers ──────────────────────────────────────────────

    private static final List<String> GROUP_ORDER =
            Arrays.asList("Normal", "Showcase", "Extended Art", "Borderless", "Full Art");

    static String treatmentGroup(ScryfallCard card) {
        if (card == null) return "Normal";
        String fs = card.getFrameStatus();
        if (fs != null && fs.contains("showcase"))    return "Showcase";
        if (fs != null && fs.contains("extendedart")) return "Extended Art";
        if ("borderless".equalsIgnoreCase(card.getBorderColor())) return "Borderless";
        if (card.isFullArt())                          return "Full Art";
        return "Normal";
    }

    private List<CardWithUserData> sortByTreatmentGroup(List<CardWithUserData> cards) {
        return cards.stream()
                .sorted(Comparator.comparingInt(c -> {
                    int idx = GROUP_ORDER.indexOf(treatmentGroup(c.getCard()));
                    return idx < 0 ? GROUP_ORDER.size() : idx;
                }))
                .collect(Collectors.toList());
    }

    /**
     * Returns a map: index-in-sortedCards → TreatmentGroupStat for every position where
     * a new treatment group starts. Stats (total / missing) are derived from allCards
     * (the unfiltered set) so they reflect set-wide completeness regardless of filters.
     */
    private Map<Integer, TreatmentGroupStat> computeGroupDividers(
            List<CardWithUserData> sortedCards, List<CardWithUserData> allCards) {

        // Aggregate total + missing per group from the full unfiltered set
        Map<String, int[]> groupStats = new LinkedHashMap<>(); // [total, missing]
        for (CardWithUserData c : allCards) {
            if (c.getCard() == null) continue;
            String g = treatmentGroup(c.getCard());
            groupStats.computeIfAbsent(g, k -> new int[2]);
            groupStats.get(g)[0]++;
            if (c.getQuantity() == 0 && c.getFoilQuantity() == 0) groupStats.get(g)[1]++;
        }

        // Walk the sorted (possibly filtered) list and record where each group starts
        Map<Integer, TreatmentGroupStat> dividers = new LinkedHashMap<>();
        String currentGroup = null;
        for (int i = 0; i < sortedCards.size(); i++) {
            String g = treatmentGroup(sortedCards.get(i).getCard());
            if (!g.equals(currentGroup)) {
                currentGroup = g;
                int[] s = groupStats.getOrDefault(g, new int[]{0, 0});
                dividers.put(i, new TreatmentGroupStat(g, s[0], s[1]));
            }
        }
        return dividers;
    }
}
