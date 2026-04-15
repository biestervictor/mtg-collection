package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.service.CardFilterService;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ScryfallService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                                @RequestParam(required = false) String frameStyle) {
        
        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);

        model.addAttribute("state", state);
        model.addAttribute("rarity", rarity);
        model.addAttribute("printing", printing);
        model.addAttribute("search", search);
        model.addAttribute("showBasics", showBasics);
        model.addAttribute("frameStyle", frameStyle);

        if (set != null && !set.isEmpty() && user != null && !user.isEmpty()) {
            List<CardWithUserData> cards = collectionService.getCardsWithUserData(user, set, null);
            List<CardWithUserData> filteredCards = cardFilterService.filterCards(cards, state, printing, rarity, search, showBasics, frameStyle);
            
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
}
