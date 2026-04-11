package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class SearchController {

    private final UserCardRepository userCardRepository;
    private final ScryfallCardRepository scryfallCardRepository;

    public SearchController(UserCardRepository userCardRepository, ScryfallCardRepository scryfallCardRepository) {
        this.userCardRepository = userCardRepository;
        this.scryfallCardRepository = scryfallCardRepository;
    }

    @GetMapping("/search")
    public String searchCards(Model model, @RequestParam(required = false) String q, @RequestParam(required = false, defaultValue = "user2") String user) {
        
        String searchUser = user;
        
        if (q != null && !q.trim().isEmpty()) {
            String searchTerm = q.trim().toLowerCase();
            boolean isNumberSearch = searchTerm.matches("\\d+");
            
            List<UserCard> userCards = userCardRepository.findByUser(searchUser);
            
            Map<String, List<UserCard>> groupedBySetAndNumber = userCards.stream()
                    .collect(Collectors.groupingBy(c -> c.getSetCode() + "_" + c.getCollectorNumber()));
            
            List<ScryfallCard> allScryfallCards = scryfallCardRepository.findAll();
            Map<String, ScryfallCard> scryfallMap = allScryfallCards.stream()
                    .collect(Collectors.toMap(c -> c.getSetCode() + "_" + c.getCollectorNumber(), c -> c, (a, b) -> a));
            
            List<CardWithUserData> results = new ArrayList<>();
            
            for (Map.Entry<String, List<UserCard>> entry : groupedBySetAndNumber.entrySet()) {
                String key = entry.getKey();
                List<UserCard> cards = entry.getValue();
                
                ScryfallCard sfCard = scryfallMap.get(key);
                
                int regularQty = cards.stream().filter(c -> !c.isFoil()).mapToInt(UserCard::getQuantity).sum();
                int foilQty = cards.stream().filter(UserCard::isFoil).mapToInt(UserCard::getQuantity).sum();
                
                boolean matches = false;
                if (sfCard != null) {
                    if (isNumberSearch) {
                        matches = sfCard.getCollectorNumber().equals(searchTerm);
                    } else {
                        matches = sfCard.getName().toLowerCase().contains(searchTerm);
                    }
                } else {
                    if (isNumberSearch) {
                        matches = key.split("_")[1].equals(searchTerm);
                    } else {
                        String cardName = cards.get(0).getName().toLowerCase();
                        matches = cardName.contains(searchTerm);
                    }
                }
                
                if (matches) {
                    CardWithUserData cwu = new CardWithUserData(sfCard, regularQty, foilQty);
                    cwu.setSetCode(cards.get(0).getSetCode());
                    cwu.setCardName(cards.get(0).getName());
                    results.add(cwu);
                }
            }
            
            results.sort(Comparator.comparing(c -> c.getCard() != null ? c.getCard().getName() : c.getCardName()));
            
            model.addAttribute("query", q);
            model.addAttribute("results", results);
            model.addAttribute("resultCount", results.size());
            model.addAttribute("searchUser", searchUser);
        }
        
        return "search";
    }
}
