package com.mtg.collection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateScheduler.class);
    
    private final ScryfallService scryfallService;

    public PriceUpdateScheduler(ScryfallService scryfallService) {
        this.scryfallService = scryfallService;
    }

    @Scheduled(cron = "0 2 0 * * *")
    public void updatePricesNightly() {
        log.info("Starting nightly price update job");
        long startTime = System.currentTimeMillis();
        
        try {
            scryfallService.updatePricesOnly();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Nightly price update completed in {} ms", duration);
        } catch (Exception e) {
            log.error("Nightly price update failed", e);
        }
    }
}
