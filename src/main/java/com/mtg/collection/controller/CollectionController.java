package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
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
            
            model.addAttribute("selectedSet", set);
            model.addAttribute("selectedUser", user);
            model.addAttribute("cards", cards);
            model.addAttribute("filteredCards", filteredCards);
            model.addAttribute("filteredCount", filteredCards.size());
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
}
