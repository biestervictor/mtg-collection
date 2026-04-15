package com.mtg.collection.service;

import com.mtg.collection.model.MetaDeck;
import com.mtg.collection.repository.MetaDeckRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches and caches meta-deck data from MTGGoldfish.
 *
 * <p>Cache strategy: if the DB already contains decks for a format that were
 * fetched today, return those directly.  Otherwise scrape MTGGoldfish and
 * persist the results.</p>
 */
@Service
public class MetaDeckService {

    private static final Logger log = LoggerFactory.getLogger(MetaDeckService.class);

    private static final String GOLDFISH_BASE = "https://www.mtggoldfish.com";
    private static final int TOP_DECKS = 15;
    /** Milliseconds to wait between consecutive HTTP requests (polite scraping). */
    private static final int RATE_LIMIT_MS = 300;

    private final MetaDeckRepository metaDeckRepository;

    public MetaDeckService(MetaDeckRepository metaDeckRepository) {
        this.metaDeckRepository = metaDeckRepository;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns meta-decks for the given format.
     * Uses the cached data from MongoDB if it is still from today;
     * otherwise fetches fresh data from MTGGoldfish.
     */
    public List<MetaDeck> getMetaDecks(String format) {
        List<MetaDeck> cached = metaDeckRepository.findByFormat(format.toLowerCase());
        if (!cached.isEmpty() && LocalDate.now().equals(cached.get(0).getFetchedAt())) {
            log.info("Returning {} cached meta-decks for format '{}'", cached.size(), format);
            return cached;
        }
        return fetchAndCache(format);
    }

    /**
     * Forces a re-scrape regardless of cache age, then persists results.
     * Returns the freshly fetched list (may be empty on error, in which case
     * the old cache is left intact).
     */
    public List<MetaDeck> refreshMetaDecks(String format) {
        log.info("Force-refreshing meta-decks for format '{}'", format);
        return fetchAndCache(format);
    }

    // ── Scraping logic ─────────────────────────────────────────────────────────

    private List<MetaDeck> fetchAndCache(String format) {
        String normalised = format.toLowerCase();
        List<MetaDeck> decks = new ArrayList<>();

        try {
            List<TileInfo> tiles = scrapeMetagamePage(normalised);
            log.info("Found {} archetype tiles for format '{}'", tiles.size(), format);

            for (TileInfo tile : tiles) {
                try {
                    MetaDeck deck = scrapeArchetypePage(normalised, tile);
                    if (deck != null) {
                        decks.add(deck);
                    }
                    Thread.sleep(RATE_LIMIT_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate-limit sleep interrupted while scraping '{}'", tile.slug);
                    break;
                } catch (Exception e) {
                    log.warn("Failed to scrape archetype '{}': {}", tile.slug, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to scrape metagame page for format '{}': {}", format, e.getMessage());
        }

        if (!decks.isEmpty()) {
            metaDeckRepository.deleteByFormat(normalised);
            metaDeckRepository.saveAll(decks);
            log.info("Cached {} meta-decks for format '{}'", decks.size(), normalised);
        } else {
            // On total failure return the old cache rather than an empty list
            List<MetaDeck> old = metaDeckRepository.findByFormat(normalised);
            if (!old.isEmpty()) {
                log.warn("Scraping failed – returning stale cache ({} decks) for '{}'", old.size(), normalised);
                return old;
            }
        }
        return decks;
    }

    // ── Step 1: parse the /metagame/{format}/full overview page ───────────────

    private List<TileInfo> scrapeMetagamePage(String format) throws Exception {
        String url = GOLDFISH_BASE + "/metagame/" + format + "/full";
        log.info("Fetching metagame page: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; MTGCollectionBot/1.0)")
                .timeout(15_000)
                .get();

        Elements tiles = doc.select("div.archetype-tile");
        List<TileInfo> result = new ArrayList<>();

        for (Element tile : tiles) {
            if (result.size() >= TOP_DECKS) break;

            // Deck name + slug from the paper-price link
            Element anchor = tile.selectFirst("div.archetype-tile-title span.deck-price-paper a");
            if (anchor == null) continue;

            String href = anchor.attr("href");      // e.g. /archetype/Mono-Red+Aggro
            String name = anchor.text().trim();
            if (href.isEmpty() || name.isEmpty()) continue;

            String slug = href.replaceFirst("^/archetype/", "");

            // META% – first text node of the statistic value element (skip nested <span>)
            double playRate = 0.0;
            Element pctEl = tile.selectFirst(
                    "div.archetype-tile-statistic.metagame-percentage div.archetype-tile-statistic-value");
            if (pctEl != null) {
                String raw = pctEl.ownText().trim().replace("%", "").replace(",", ".");
                try { playRate = Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
            }

            result.add(new TileInfo(name, slug, playRate));
        }
        return result;
    }

    // ── Step 2: parse an individual archetype page ─────────────────────────────

    private MetaDeck scrapeArchetypePage(String format, TileInfo tile) throws Exception {
        String url = GOLDFISH_BASE + "/archetype/" + tile.slug;
        log.debug("Fetching archetype page: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; MTGCollectionBot/1.0)")
                .timeout(15_000)
                .get();

        // The deck list is stored URL-encoded in a hidden input
        Element deckInput = doc.selectFirst("input[name='deck_input[deck]']");
        if (deckInput == null) {
            log.warn("No deck_input[deck] found on archetype page for slug '{}'", tile.slug);
            return null;
        }

        String rawValue = deckInput.val();
        String decoded = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);

        List<MetaDeck.MetaDeckCard> mainboard = parseDeckList(decoded);
        if (mainboard.isEmpty()) {
            log.warn("Parsed empty mainboard for slug '{}'", tile.slug);
            return null;
        }

        // Commander name (only meaningful for the commander format)
        String commanderName = null;
        Element cmdInput = doc.selectFirst("input[name='deck_input[commander]']");
        if (cmdInput != null && !cmdInput.val().isBlank()) {
            commanderName = URLDecoder.decode(cmdInput.val(), StandardCharsets.UTF_8).trim();
        }

        MetaDeck deck = new MetaDeck();
        deck.setId(format + "_" + tile.slug);
        deck.setFormat(format);
        deck.setName(tile.name);
        deck.setSlug(tile.slug);
        deck.setPlayRate(tile.playRate);
        deck.setCommanderName(commanderName);
        deck.setMainboard(mainboard);
        deck.setFetchedAt(LocalDate.now());
        return deck;
    }

    // ── Deck list parser ───────────────────────────────────────────────────────

    /**
     * Parses a deck list in the form {@code "4 Lightning Bolt\n1 Mountain\n..."}.
     */
    private List<MetaDeck.MetaDeckCard> parseDeckList(String raw) {
        List<MetaDeck.MetaDeckCard> cards = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int spaceIdx = line.indexOf(' ');
            if (spaceIdx < 1) continue;

            String qtyStr = line.substring(0, spaceIdx).trim();
            String cardName = line.substring(spaceIdx + 1).trim();
            if (cardName.isEmpty()) continue;

            int qty;
            try {
                qty = Integer.parseInt(qtyStr);
            } catch (NumberFormatException e) {
                log.debug("Could not parse quantity from line: '{}'", line);
                continue;
            }
            cards.add(new MetaDeck.MetaDeckCard(cardName, qty));
        }
        return cards;
    }

    // ── Helper record ─────────────────────────────────────────────────────────

    private static class TileInfo {
        final String name;
        final String slug;
        final double playRate;

        TileInfo(String name, String slug, double playRate) {
            this.name = name;
            this.slug = slug;
            this.playRate = playRate;
        }
    }
}
