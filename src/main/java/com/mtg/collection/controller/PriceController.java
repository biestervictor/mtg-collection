package com.mtg.collection.controller;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.service.PriceUpdateService;
import com.mtg.collection.service.ScryfallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PriceController {

    private final PriceUpdateService priceUpdateService;
    private final ScryfallService    scryfallService;

    public PriceController(PriceUpdateService priceUpdateService, ScryfallService scryfallService) {
        this.priceUpdateService = priceUpdateService;
        this.scryfallService    = scryfallService;
    }

    /**
     * Manually triggers the price-propagation from the Scryfall cache to all
     * users' UserCard records.  Returns a JSON summary of how many cards were
     * actually updated per user.
     *
     * Example response:
     * { "totalUpdated": 42, "perUser": { "Andre": 20, "Victor": 22 } }
     */
    @PostMapping("/api/prices/update")
    public ResponseEntity<Map<String, Object>> triggerPriceUpdate() {
        Map<String, Integer> perUser = priceUpdateService.runUpdateForAllUsers();
        int total = perUser.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalUpdated", total);
        response.put("perUser", perUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches fresh price data for a single card from the Scryfall API and
     * persists it to the local Scryfall cache.
     *
     * Example response:
     * { "priceRegular": 1.23, "priceFoil": 4.56, "purchaseLink": "https://..." }
     */
    @PostMapping("/api/prices/refresh-card")
    public ResponseEntity<Map<String, Object>> refreshCardPrice(
            @RequestParam String setCode,
            @RequestParam String collectorNumber) {

        ScryfallCard card = scryfallService.refreshSingleCard(setCode.toLowerCase(), collectorNumber);
        Map<String, Object> response = new LinkedHashMap<>();
        if (card != null) {
            response.put("priceRegular", card.getPriceRegular());
            response.put("priceFoil",    card.getPriceFoil());
            response.put("purchaseLink", card.getPurchaseLink());
        } else {
            response.put("error", "Karte nicht in Scryfall gefunden");
        }
        return ResponseEntity.ok(response);
    }
}
