package com.mtg.collection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateScheduler.class);

    private final ScryfallService     scryfallService;
    private final PriceHistoryService priceHistoryService;

    public PriceUpdateScheduler(ScryfallService scryfallService,
                                PriceHistoryService priceHistoryService) {
        this.scryfallService     = scryfallService;
        this.priceHistoryService = priceHistoryService;
    }

    /** Step 1 (00:02) — refresh all Scryfall card prices from the API. */
    @Scheduled(cron = "0 2 0 * * *")
    public void updatePricesNightly() {
        log.info("Starting nightly Scryfall price update");
        long start = System.currentTimeMillis();
        try {
            scryfallService.updatePricesOnly();
            log.info("Nightly Scryfall price update completed in {} ms",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Nightly Scryfall price update failed", e);
        }
    }

    /** Step 2 (03:00) — snapshot current prices for all owned cards (after propagation at 02:00). */
    @Scheduled(cron = "0 0 3 * * *")
    public void snapshotPricesNightly() {
        log.info("Starting nightly price snapshot");
        try {
            int count = priceHistoryService.snapshotOwnedCardPrices();
            log.info("Nightly price snapshot: {} cards recorded", count);
        } catch (Exception e) {
            log.error("Nightly price snapshot failed", e);
        }
    }
}

