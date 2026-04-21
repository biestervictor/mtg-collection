package com.mtg.collection.controller;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.service.PriceHistoryService;
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

    private final PriceUpdateService  priceUpdateService;
    private final ScryfallService     scryfallService;
    private final PriceHistoryService priceHistoryService;

    public PriceController(PriceUpdateService  priceUpdateService,
                           ScryfallService     scryfallService,
                           PriceHistoryService priceHistoryService) {
        this.priceUpdateService  = priceUpdateService;
        this.scryfallService     = scryfallService;
        this.priceHistoryService = priceHistoryService;
    }

    /**
     * Manually triggers a full price update:
     * <ol>
     *   <li>Fetches fresh prices from Scryfall for all sets the users own cards from.</li>
     *   <li>Propagates the updated prices to all users' UserCard records.</li>
     *   <li>Takes a price-history snapshot for the PriceWatch page.</li>
     * </ol>
     *
     * Example response:
     * { "totalUpdated": 42, "perUser": { "Andre": 20, "Victor": 22 }, "snapped": 135 }
     */
    @PostMapping("/api/prices/update")
    public ResponseEntity<Map<String, Object>> triggerPriceUpdate() {
        Map<String, Object> result = priceUpdateService.runFullUpdate();

        int snapped = priceHistoryService.snapshotOwnedCardPrices();
        result.put("snapped", snapped);

        return ResponseEntity.ok(result);
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
