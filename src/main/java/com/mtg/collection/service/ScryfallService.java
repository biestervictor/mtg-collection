package com.mtg.collection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.ScryfallSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScryfallService {

    private static final Logger log = LoggerFactory.getLogger(ScryfallService.class);
    private static final String SCRYFALL_API = "https://api.scryfall.com";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ScryfallCardRepository cardRepository;
    private final ScryfallSetRepository setRepository;

    public ScryfallService(RestTemplate restTemplate, ObjectMapper objectMapper,
                          ScryfallCardRepository cardRepository, ScryfallSetRepository setRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.cardRepository = cardRepository;
        this.setRepository = setRepository;
    }

    public List<ScryfallSet> getAllSets(boolean forceRefresh) {
        List<ScryfallSet> sets = setRepository.findAll();
        
        if (sets.isEmpty() || forceRefresh) {
            try {
                List<ScryfallSet> fetchedSets = fetchSetsFromApi();
                if (!fetchedSets.isEmpty()) {
                    setRepository.deleteAll();
                    setRepository.saveAll(fetchedSets);
                    return fetchedSets;
                }
            } catch (Exception e) {
                log.error("Failed to fetch sets from API", e);
            }
        }
        
        // Filter out digital sets that may have been cached before this check was added
        return sets.stream().filter(s -> !s.isDigital()).collect(Collectors.toList());
    }

    private List<ScryfallSet> fetchSetsFromApi() throws Exception {
        String responseBody = restTemplate.getForObject(URI.create(SCRYFALL_API + "/sets"), String.class);

        List<ScryfallSet> sets = new ArrayList<>();
        Set<String> excludeTypes = Set.of("alchemy", "minigame", "memorabilia", "vanguard", "digital");

        if (responseBody != null) {
            JsonNode response = objectMapper.readTree(responseBody);
            if (response.has("data")) {
                for (JsonNode setNode : response.get("data")) {
                    String setType = setNode.has("set_type") ? setNode.get("set_type").asText() : "";
                    boolean isDigital = setNode.has("digital") && setNode.get("digital").asBoolean();
                    int cardCount = setNode.has("card_count") ? setNode.get("card_count").asInt() : 0;

                    if (!isDigital && !excludeTypes.contains(setType) && cardCount > 0) {
                        ScryfallSet set = new ScryfallSet();
                        set.setName(setNode.get("name").asText());
                        set.setSetCode(setNode.get("code").asText());
                        set.setCardCount(cardCount);
                        set.setReleasedAt(setNode.has("released_at") ? setNode.get("released_at").asText() : null);
                        set.setIcon(setNode.has("icon_svg_uri") ? setNode.get("icon_svg_uri").asText() : null);
                        set.setDigital(false);
                        set.setSetType(setType);
                        sets.add(set);
                    }
                }
            }
        }

        log.info("Fetched {} sets from Scryfall API", sets.size());
return sets;
    }

    public List<ScryfallCard> getAllCards(boolean forceRefresh) {
        List<ScryfallSet> sets = getAllSets(forceRefresh);
        List<ScryfallCard> allCards = new ArrayList<>();
        
        for (ScryfallSet set : sets) {
            allCards.addAll(getCardsBySet(set.getSetCode(), null));
        }
        
        return allCards;
    }
    
    public List<ScryfallCard> getCardsBySet(String setCode, List<String> filters) {
        List<ScryfallCard> cachedCards = cardRepository.findBySetCode(setCode);
        
        if (cachedCards.isEmpty()) {
            try {
                cachedCards = fetchAndSaveCardsFromApi(setCode, filters);
            } catch (Exception e) {
                log.error("Failed to fetch cards for set: {}", setCode, e);
            }
        }
        
        return cachedCards;
    }

    private List<ScryfallCard> fetchAndSaveCardsFromApi(String setCode, List<String> filters) throws Exception {
        List<ScryfallCard> fetchedCards = fetchCardsFromApi(setCode, filters);
        
        if (!fetchedCards.isEmpty()) {
            Map<String, ScryfallCard> existingCards = cardRepository.findBySetCode(setCode).stream()
                    .collect(Collectors.toMap(
                            c -> c.getSetCode() + "_" + c.getCollectorNumber(),
                            c -> c,
                            (a, b) -> a
                    ));

            List<ScryfallCard> cardsToSave = new ArrayList<>();
            
            for (ScryfallCard newCard : fetchedCards) {
                String key = newCard.getSetCode() + "_" + newCard.getCollectorNumber();
                ScryfallCard existing = existingCards.get(key);
                
                if (existing != null) {
                    existing.setName(newCard.getName());
                    existing.setRarity(newCard.getRarity());
                    existing.setTypeLine(newCard.getTypeLine());
                    existing.setFrameStatus(newCard.getFrameStatus());
                    existing.setBorderColor(newCard.getBorderColor());
                    existing.setFullArt(newCard.isFullArt());
                    existing.setThumbnailFront(newCard.getThumbnailFront());
                    existing.setImageFront(newCard.getImageFront());
                    existing.setThumbnailBack(newCard.getThumbnailBack());
                    existing.setImageBack(newCard.getImageBack());
                    existing.setPriceRegular(newCard.getPriceRegular());
                    existing.setPriceFoil(newCard.getPriceFoil());
                    existing.setPurchaseLink(newCard.getPurchaseLink());
                    cardsToSave.add(existing);
                } else {
                    cardsToSave.add(newCard);
                }
            }
            
            cardRepository.saveAll(cardsToSave);
            log.info("Saved {} cards for set {} ({} new, {} updated)", 
                    cardsToSave.size(), setCode, 
                    fetchedCards.size() - existingCards.size(),
                    existingCards.size());
        }
        
        return cardRepository.findBySetCode(setCode);
    }

    private List<ScryfallCard> fetchCardsFromApi(String setCode, List<String> filters) throws Exception {
        List<ScryfallCard> cards = new ArrayList<>();
        
        StringBuilder query = new StringBuilder("set:" + setCode);
        
        if (filters != null && filters.contains("excludeBasicLands")) {
            query.append(" -t:\"basic land\"");
        }
        if (filters != null && !filters.contains("extendedArt")) {
            query.append(" -frame:extendedart");
        }
        query.append(" -is:digital order:set direction:asc");
        
        String encodedQuery = java.net.URLEncoder.encode(query.toString(), "UTF-8");
        // unique=prints: return every print (collector number) of each card, not just one per name.
        // Without this the API defaults to unique=cards (1 result per oracle id),
        // which only returns draft cards and misses all alternate treatments
        // (borderless, showcase, extended art, full-art, etc.).
        String nextPageUri = SCRYFALL_API + "/cards/search?q=" + encodedQuery + "&unique=prints";
        
        while (nextPageUri != null) {
            String responseBody = restTemplate.getForObject(URI.create(nextPageUri), String.class);

            if (responseBody != null) {
                JsonNode response = objectMapper.readTree(responseBody);
                if (response.has("data")) {
                    for (JsonNode cardNode : response.get("data")) {
                        ScryfallCard card = mapCardFromJson(cardNode, setCode);
                        cards.add(card);
                    }
                }
                nextPageUri = response.has("next_page") && !response.get("next_page").isNull() 
                    ? response.get("next_page").asText() : null;
            } else {
                break;
            }

            // Rate limiting: Scryfall allows max 10 req/sec
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limit sleep interrupted", ie);
            }
        }

        log.info("Fetched {} cards for set {} from Scryfall API", cards.size(), setCode);
        return cards;
    }

    public List<ScryfallCard> getCardsBySetWithoutCache(String setCode, List<String> filters) {
        try {
            return fetchCardsFromApi(setCode, filters);
        } catch (Exception e) {
            log.error("Failed to fetch cards for set: {}", setCode, e);
            return new ArrayList<>();
        }
    }

    private ScryfallCard mapCardFromJson(JsonNode cardNode, String setCode) {
        ScryfallCard card = new ScryfallCard();
        card.setName(cardNode.get("name").asText());
        card.setSetCode(setCode);
        card.setCollectorNumber(cardNode.get("collector_number").asText());
        card.setRarity(cardNode.has("rarity") ? cardNode.get("rarity").asText() : "common");
        
        if (cardNode.has("type_line")) {
            card.setTypeLine(cardNode.get("type_line").asText());
        } else if (cardNode.has("card_faces") && cardNode.get("card_faces").size() > 0) {
            JsonNode front = cardNode.get("card_faces").get(0);
            if (front.has("type_line")) {
                card.setTypeLine(front.get("type_line").asText());
            }
        }

        // frame_effects is an array in the Scryfall API (e.g. ["extendedart"], ["showcase"])
        if (cardNode.has("frame_effects")) {
            List<String> effects = new ArrayList<>();
            for (JsonNode effect : cardNode.get("frame_effects")) {
                effects.add(effect.asText());
            }
            if (!effects.isEmpty()) {
                card.setFrameStatus(String.join(",", effects));
            }
        }
        // border_color: "black", "borderless", "white", "silver", "gold"
        if (cardNode.has("border_color")) {
            card.setBorderColor(cardNode.get("border_color").asText());
        }
        // full_art: true for full-art cards (e.g. full-art lands)
        if (cardNode.has("full_art")) {
            card.setFullArt(cardNode.get("full_art").asBoolean());
        }

        // Extract image URLs: regular card vs. double-faced card (DFC)
        if (cardNode.has("image_uris")) {
            JsonNode imageUris = cardNode.get("image_uris");
            if (imageUris.has("small"))  card.setThumbnailFront(imageUris.get("small").asText());
            if (imageUris.has("normal")) card.setImageFront(imageUris.get("normal").asText());
        } else if (cardNode.has("card_faces")) {
            JsonNode faces = cardNode.get("card_faces");
            if (faces.size() > 0 && faces.get(0).has("image_uris")) {
                JsonNode front = faces.get(0).get("image_uris");
                if (front.has("small"))  card.setThumbnailFront(front.get("small").asText());
                if (front.has("normal")) card.setImageFront(front.get("normal").asText());
            }
            if (faces.size() > 1 && faces.get(1).has("image_uris")) {
                JsonNode back = faces.get(1).get("image_uris");
                if (back.has("small"))  card.setThumbnailBack(back.get("small").asText());
                if (back.has("normal")) card.setImageBack(back.get("normal").asText());
            }
        }

        if (cardNode.has("prices")) {
            JsonNode prices = cardNode.get("prices");
            if (prices.has("eur") && !prices.get("eur").isNull()) {
                card.setPriceRegular(parseDouble(prices.get("eur").asText()));
            }
            if (prices.has("eur_foil") && !prices.get("eur_foil").isNull()) {
                card.setPriceFoil(parseDouble(prices.get("eur_foil").asText()));
            }
        }

        if (cardNode.has("purchase_uris") && cardNode.get("purchase_uris").has("cardmarket")) {
            card.setPurchaseLink(cardNode.get("purchase_uris").get("cardmarket").asText());
        }

        return card;
    }

    /**
     * Refreshes Scryfall prices for a specific subset of set codes.
     * Useful for targeted updates (e.g. only sets the user owns cards from).
     * A 150 ms sleep between sets keeps the request rate well below Scryfall's
     * hard limit of 10 req/s.
     */
    public void updatePricesForSets(Collection<String> setCodes) {
        log.info("Updating Scryfall prices for {} sets", setCodes.size());
        for (String setCode : setCodes) {
            try {
                updatePricesForSet(setCode);
            } catch (Exception e) {
                log.error("Failed to update prices for set: {}", setCode, e);
            }
            try { Thread.sleep(150); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("updatePricesForSets interrupted");
                break;
            }
        }
        log.info("Targeted price update completed for {} sets", setCodes.size());
    }

    public void updatePricesOnly() {
        List<ScryfallSet> sets = setRepository.findAll();
        log.info("Starting price update for {} sets", sets.size());

        for (ScryfallSet set : sets) {
            try {
                updatePricesForSet(set.getSetCode());
            } catch (Exception e) {
                log.error("Failed to update prices for set: {}", set.getSetCode(), e);
            }
            try { Thread.sleep(150); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("updatePricesOnly interrupted");
                break;
            }
        }

        log.info("Price update completed");
    }

    private void updatePricesForSet(String setCode) throws Exception {
        List<ScryfallCard> fetchedCards = fetchCardsFromApi(setCode, null);
        List<ScryfallCard> existingCards = cardRepository.findBySetCode(setCode);
        
        Map<String, ScryfallCard> cardMap = existingCards.stream()
                .collect(Collectors.toMap(
                        c -> c.getSetCode() + "_" + c.getCollectorNumber(),
                        c -> c,
                        (a, b) -> a
                ));

        for (ScryfallCard fetchedCard : fetchedCards) {
            String key = fetchedCard.getSetCode() + "_" + fetchedCard.getCollectorNumber();
            ScryfallCard existing = cardMap.get(key);
            
            if (existing != null) {
                existing.setPriceRegular(fetchedCard.getPriceRegular());
                existing.setPriceFoil(fetchedCard.getPriceFoil());
            }
        }
        
        cardRepository.saveAll(existingCards);
        log.info("Updated prices for {} cards in set {}", existingCards.size(), setCode);
    }

    public void clearCache(String setCode) {
        if (setCode != null && !setCode.isEmpty()) {
            cardRepository.deleteBySetCode(setCode);
            log.info("Cleared cache for set: {}", setCode);
        }
    }
    
    public void clearAllCache() {
        cardRepository.deleteAll();
        log.info("Cleared all Scryfall card cache");
    }

    /**
     * Fetches fresh data for a single card from the Scryfall API by set code +
     * collector number, updates (or inserts) the record in MongoDB, and returns the
     * updated ScryfallCard.  Returns null if the card cannot be found.
     */
    public ScryfallCard refreshSingleCard(String setCode, String collectorNumber) {
        String lower = setCode.toLowerCase();
        String url = SCRYFALL_API + "/cards/" + lower + "/" + collectorNumber;
        try {
            String responseBody = restTemplate.getForObject(URI.create(url), String.class);
            if (responseBody == null) return null;
            JsonNode cardNode = objectMapper.readTree(responseBody);
            if (cardNode.has("object") && "error".equals(cardNode.get("object").asText())) {
                log.warn("Scryfall returned error for {}/{}: {}", lower, collectorNumber,
                        cardNode.has("details") ? cardNode.get("details").asText() : "unknown");
                return null;
            }
            ScryfallCard fresh = mapCardFromJson(cardNode, lower);
            // Upsert with deduplication: if duplicate documents exist (e.g. from prior
            // imports with mixed setCode casing) keep the first, delete the rest, then update.
            List<ScryfallCard> existing = cardRepository.findBySetCodeAndCollectorNumber(lower, collectorNumber);
            ScryfallCard toSave;
            if (existing.isEmpty()) {
                toSave = fresh;
            } else {
                toSave = existing.get(0);
                toSave.setPriceRegular(fresh.getPriceRegular());
                toSave.setPriceFoil(fresh.getPriceFoil());
                toSave.setPurchaseLink(fresh.getPurchaseLink());
                toSave.setThumbnailFront(fresh.getThumbnailFront());
                toSave.setImageFront(fresh.getImageFront());
                if (existing.size() > 1) {
                    log.warn("Deduplicating {} entries for {}/{} in Scryfall cache",
                            existing.size(), lower, collectorNumber);
                    cardRepository.deleteAll(existing.subList(1, existing.size()));
                }
            }
            return cardRepository.save(toSave);
        } catch (Exception e) {
            log.error("Failed to refresh card {}/{} from Scryfall", lower, collectorNumber, e);
            return null;
        }
    }
    
    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
