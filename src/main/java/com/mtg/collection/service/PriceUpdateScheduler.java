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
    private final ReportCacheService  reportCacheService;

    public PriceUpdateScheduler(ScryfallService scryfallService,
                                PriceHistoryService priceHistoryService,
                                ReportCacheService reportCacheService) {
        this.scryfallService     = scryfallService;
        this.priceHistoryService = priceHistoryService;
        this.reportCacheService  = reportCacheService;
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

    /**
     * Step 3 (03:30) — pre-compute Statistics and Sell Suggestions for all users.
     * Runs after prices are updated (00:02) and snapshotted (03:00), so the cache
     * always reflects the freshest data when users open the pages in the morning.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void precomputeReportsNightly() {
        log.info("Starting nightly report pre-computation");
        try {
            reportCacheService.refreshAll();
            log.info("Nightly report pre-computation completed");
        } catch (Exception e) {
            log.error("Nightly report pre-computation failed", e);
        }
    }
}

