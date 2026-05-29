package com.mtg.collection.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDeckTest {

    // ── DeckCard helpers ──────────────────────────────────────────────────────

    private UserDeck.DeckCard deckCard(String name, String setCode, String cn,
                                       int qty, boolean foil, double price, String thumb) {
        UserDeck.DeckCard dc = new UserDeck.DeckCard(name, setCode, cn, qty, foil);
        dc.setPrice(price);
        dc.setThumbnailUrl(thumb);
        return dc;
    }

    // ── Board count helpers ───────────────────────────────────────────────────

    @Test
    void getMainboardCount_sumsQuantities() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(deckCard("A", "tst", "1", 4, false, 1.0, null),
                                   deckCard("B", "tst", "2", 2, false, 1.0, null)));
        assertEquals(6, deck.getMainboardCount());
    }

    @Test
    void getSideboardCount_sumsQuantities() {
        UserDeck deck = new UserDeck();
        deck.setSideboard(List.of(deckCard("X", "tst", "3", 3, false, 0.0, null)));
        assertEquals(3, deck.getSideboardCount());
    }

    @Test
    void getExtraboardCount_sumsQuantities() {
        UserDeck deck = new UserDeck();
        deck.setExtraboard(List.of(deckCard("Y", "tst", "4", 1, false, 0.0, null)));
        assertEquals(1, deck.getExtraboardCount());
    }

    @Test
    void getBoardCount_nullBoard_returnsZero() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(null);
        deck.setSideboard(null);
        deck.setExtraboard(null);
        assertEquals(0, deck.getMainboardCount());
        assertEquals(0, deck.getSideboardCount());
        assertEquals(0, deck.getExtraboardCount());
    }

    // ── getTotalValue ─────────────────────────────────────────────────────────

    @Test
    void getTotalValue_sumsAllBoardsAndQuantities() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(deckCard("A", "tst", "1", 4, false, 2.0, null)));  // 8.0
        deck.setSideboard(List.of(deckCard("B", "tst", "2", 2, false, 3.0, null)));  // 6.0
        deck.setExtraboard(List.of(deckCard("C", "tst", "3", 1, false, 5.0, null))); // 5.0
        assertEquals(19.0, deck.getTotalValue(), 0.001);
    }

    @Test
    void getTotalValue_emptyDeck_returnsZero() {
        UserDeck deck = new UserDeck();
        assertEquals(0.0, deck.getTotalValue(), 0.001);
    }

    // ── getCoverArtUrl ────────────────────────────────────────────────────────

    @Test
    void getCoverArtUrl_returnsThumbOfMostExpensiveCard() {
        UserDeck deck = new UserDeck();
        UserDeck.DeckCard cheap     = deckCard("A", "tst", "1", 1, false, 1.0,  "http://cheap.jpg");
        UserDeck.DeckCard expensive = deckCard("B", "tst", "2", 1, false, 50.0, "http://exp.jpg");
        deck.setMainboard(List.of(cheap, expensive));

        assertEquals("http://exp.jpg", deck.getCoverArtUrl());
    }

    @Test
    void getCoverArtUrl_noThumbnails_returnsNull() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(deckCard("A", "tst", "1", 1, false, 5.0, null)));
        assertNull(deck.getCoverArtUrl());
    }

    @Test
    void getCoverArtUrl_emptyDeck_returnsNull() {
        UserDeck deck = new UserDeck();
        assertNull(deck.getCoverArtUrl());
    }

    // ── getCoverImageUrl ──────────────────────────────────────────────────────

    @Test
    void getCoverImageUrl_withThumbnail_returnsThumbnail() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(deckCard("A", "tst", "1", 1, false, 5.0, "http://thumb.jpg")));
        assertEquals("http://thumb.jpg", deck.getCoverImageUrl());
    }

    @Test
    void getCoverImageUrl_noThumbnail_fallsBackToScryfallApiUrl() {
        UserDeck deck = new UserDeck();
        deck.setMainboard(List.of(deckCard("A", "tst", "1", 1, false, 0.0, null)));
        String url = deck.getCoverImageUrl();
        assertNotNull(url);
        assertTrue(url.contains("api.scryfall.com"), "Fallback must be Scryfall API URL");
        assertTrue(url.contains("tst/1"));
    }

    @Test
    void getCoverImageUrl_emptyMainboard_returnsNull() {
        UserDeck deck = new UserDeck();
        assertNull(deck.getCoverImageUrl());
    }

    // ── DeckCard.getEffectiveThumbnailUrl / getEffectiveImageUrl ─────────────

    @Test
    void deckCard_effectiveThumbnailUrl_storedThumb_returnsIt() {
        UserDeck.DeckCard dc = new UserDeck.DeckCard("A", "tst", "1", 1, false);
        dc.setThumbnailUrl("http://stored.jpg");
        assertEquals("http://stored.jpg", dc.getEffectiveThumbnailUrl());
    }

    @Test
    void deckCard_effectiveThumbnailUrl_noThumb_returnsScryfallFallback() {
        UserDeck.DeckCard dc = new UserDeck.DeckCard("A", "tst", "42", 1, false);
        String url = dc.getEffectiveThumbnailUrl();
        assertNotNull(url);
        assertTrue(url.contains("tst/42"));
        assertTrue(url.contains("version=small"));
    }

    @Test
    void deckCard_effectiveImageUrl_storedImage_returnsIt() {
        UserDeck.DeckCard dc = new UserDeck.DeckCard("A", "tst", "1", 1, false);
        dc.setImageUrl("http://full.jpg");
        assertEquals("http://full.jpg", dc.getEffectiveImageUrl());
    }

    @Test
    void deckCard_effectiveImageUrl_noImage_returnsScryfallFallback() {
        UserDeck.DeckCard dc = new UserDeck.DeckCard("A", "tst", "42", 1, false);
        String url = dc.getEffectiveImageUrl();
        assertNotNull(url);
        assertTrue(url.contains("version=normal"));
    }

    @Test
    void deckCard_effectiveUrls_noSetOrCn_returnsNull() {
        UserDeck.DeckCard dc = new UserDeck.DeckCard();
        assertNull(dc.getEffectiveThumbnailUrl());
        assertNull(dc.getEffectiveImageUrl());
    }
}
