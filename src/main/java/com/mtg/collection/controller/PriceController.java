package com.mtg.collection.controller;

import com.mtg.collection.service.PriceUpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PriceController {

    private final PriceUpdateService priceUpdateService;

    public PriceController(PriceUpdateService priceUpdateService) {
        this.priceUpdateService = priceUpdateService;
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
}
