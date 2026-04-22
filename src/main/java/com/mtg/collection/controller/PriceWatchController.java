package com.mtg.collection.controller;

import com.mtg.collection.model.PriceHistory;
import com.mtg.collection.service.PriceHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class PriceWatchController {

    private static final double DEFAULT_MIN_PRICE = 1.0;
    private static final int    TOP_N             = 50;

    private final PriceHistoryService priceHistoryService;

    public PriceWatchController(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping("/price-watch")
    public String priceWatch(Model model) {
        model.addAttribute("winners",          priceHistoryService.getTopWinners(TOP_N, DEFAULT_MIN_PRICE));
        model.addAttribute("losers",           priceHistoryService.getTopLosers(TOP_N, DEFAULT_MIN_PRICE));
        model.addAttribute("sets",             priceHistoryService.getSetSummaries(DEFAULT_MIN_PRICE, TOP_N));
        model.addAttribute("lastSnapshotDate", priceHistoryService.getLastSnapshotDate());
        model.addAttribute("totalTracked",     priceHistoryService.getTotalTrackedCards());
        return "price-watch";
    }

    /**
     * Returns the full price history for a single card as JSON.
     * Used by the card-detail modal to render the Chart.js chart.
     */
    @GetMapping("/api/price-watch/history")
    @ResponseBody
    public ResponseEntity<List<PriceHistory>> getHistory(
            @RequestParam String setCode,
            @RequestParam String cn) {
        return ResponseEntity.ok(priceHistoryService.getPriceHistory(setCode, cn));
    }
}
