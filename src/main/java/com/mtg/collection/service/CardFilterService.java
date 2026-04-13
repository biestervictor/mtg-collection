package com.mtg.collection.service;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallCard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CardFilterService {

    public List<CardWithUserData> filterCards(List<CardWithUserData> cards, String state,
                                              String printing, String rarity, String search, String showBasics,
                                              String showExtendedArt, String showShowcase) {
        List<CardWithUserData> filtered = cards;

        if (!"true".equals(showBasics)) {
            filtered = filterOutBasicLands(filtered);
        }

        if ("true".equals(showExtendedArt)) {
            filtered = filterToOnlyExtendedArt(filtered);
        } else if ("true".equals(showShowcase)) {
            filtered = filterToOnlyShowcase(filtered);
        }

        if (state != null && !state.isEmpty() && !"all".equals(state)) {
            filtered = filterByState(filtered, state, printing);
        } else if (printing != null && !printing.isEmpty()) {
            filtered = filterByPrinting(filtered, printing);
        }

        if (rarity != null && !rarity.isEmpty()) {
            filtered = filterByRarity(filtered, rarity);
        }

        if (search != null && !search.isEmpty()) {
            filtered = filterBySearch(filtered, search);
        }

        return filtered;
    }

    private List<CardWithUserData> filterByState(List<CardWithUserData> cards, String state, String printing) {
        boolean filterByFoil = "foil".equals(printing);

        switch (state) {
            case "owned":
                if (filterByFoil) {
                    return cards.stream()
                            .filter(c -> c.getFoilQuantity() > 0)
                            .collect(Collectors.toList());
                }
                return cards.stream()
                        .filter(c -> c.getQuantity() > 0 || c.getFoilQuantity() > 0)
                        .collect(Collectors.toList());
                        
            case "missing":
                if (filterByFoil) {
                    return cards.stream()
                            .filter(c -> c.getFoilQuantity() == 0)
                            .collect(Collectors.toList());
                }
                return cards.stream()
                        .filter(c -> c.getQuantity() == 0 && c.getFoilQuantity() == 0)
                        .collect(Collectors.toList());
                        
            case "tradable":
                if (filterByFoil) {
                    return cards.stream()
                            .filter(c -> c.getFoilQuantity() > 1)
                            .map(c -> createWithFoilMinusOne(c))
                            .collect(Collectors.toList());
                }
                return cards.stream()
                        .filter(c -> c.getQuantity() > 1 || c.getFoilQuantity() > 1)
                        .map(c -> createWithTradableQuantities(c))
                        .collect(Collectors.toList());
                        
            default:
                if (filterByFoil) {
                    return filterByPrinting(cards, "foil");
                }
                return cards;
        }
    }

    private List<CardWithUserData> filterByPrinting(List<CardWithUserData> cards, String printing) {
        if ("foil".equals(printing)) {
            return cards.stream()
                    .filter(c -> c.getFoilQuantity() > 0)
                    .collect(Collectors.toList());
        }
        return cards;
    }

    private List<CardWithUserData> filterByRarity(List<CardWithUserData> cards, String rarity) {
        List<String> rarities = java.util.Arrays.asList(rarity.split(","));
        return cards.stream()
                .filter(c -> c.getCard() != null && 
                            rarities.contains(c.getCard().getRarity().toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<CardWithUserData> filterBySearch(List<CardWithUserData> cards, String search) {
        try {
            int number = Integer.parseInt(search);
            return cards.stream()
                    .filter(c -> c.getCard() != null &&
                                c.getCard().getCollectorNumber().equals(String.valueOf(number)))
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            return cards.stream()
                    .filter(c -> c.getCard() != null &&
                                c.getCard().getName().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }
    }

    private List<CardWithUserData> filterOutBasicLands(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() == null || c.getCard().getTypeLine() == null || 
                            !c.getCard().getTypeLine().toLowerCase().contains("basic land"))
                .collect(Collectors.toList());
    }

    private List<CardWithUserData> filterToOnlyBasicLands(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() != null && c.getCard().getTypeLine() != null &&
                            c.getCard().getTypeLine().toLowerCase().contains("basic land"))
                .collect(Collectors.toList());
    }

    private List<CardWithUserData> filterOutExtendedArt(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() == null || !"extendedart".equalsIgnoreCase(c.getCard().getFrameStatus()))
                .collect(Collectors.toList());
    }

    private List<CardWithUserData> filterToOnlyExtendedArt(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() != null && "extendedart".equalsIgnoreCase(c.getCard().getFrameStatus()))
                .collect(Collectors.toList());
    }

    private List<CardWithUserData> filterToOnlyShowcase(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() != null && "extendedart".equalsIgnoreCase(c.getCard().getFrameStatus()))
                .collect(Collectors.toList());
    }

    public List<CardWithUserData> getOnlyInLeft(List<CardWithUserData> left, List<CardWithUserData> right) {
        return left.stream()
                .filter(l -> right.stream().noneMatch(r -> 
                        r.getCard() != null && l.getCard() != null &&
                        r.getCard().getCollectorNumber().equals(l.getCard().getCollectorNumber())))
                .collect(Collectors.toList());
    }

    private CardWithUserData createWithFoilMinusOne(CardWithUserData c) {
        CardWithUserData result = new CardWithUserData(
                c.getCard(),
                c.getQuantity(),
                c.getFoilQuantity() > 0 ? c.getFoilQuantity() - 1 : 0
        );
        return result;
    }

    private CardWithUserData createWithTradableQuantities(CardWithUserData c) {
        CardWithUserData result = new CardWithUserData(
                c.getCard(),
                c.getQuantity() > 0 ? c.getQuantity() - 1 : 0,
                c.getFoilQuantity() > 0 ? c.getFoilQuantity() - 1 : 0
        );
        return result;
    }
}
