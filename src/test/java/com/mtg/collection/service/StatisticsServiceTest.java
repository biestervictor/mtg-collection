package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private UserCardRepository userCardRepository;
    @Mock private ImportHistoryRepository importHistoryRepository;
    @Mock private ScryfallService scryfallService;
    @Mock private ScryfallCardRepository scryfallCardRepository;
    @Mock private MongoTemplate mongoTemplate;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(userCardRepository, importHistoryRepository,
                scryfallService, scryfallCardRepository, mongoTemplate);
        // By default, Scryfall lookups return no extra data → effective price = UserCard.price
        lenient().when(scryfallCardRepository.findBySetCodeIn(any())).thenReturn(Collections.emptyList());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserCard card(String set, String number, boolean foil, int qty, double price) {
        UserCard c = new UserCard("testuser", "Card " + set + number, set, number, qty, foil);
        c.setPrice(price);
        return c;
    }

    private ScryfallSet scryfallSet(String code, int total) {
        ScryfallSet s = new ScryfallSet();
        s.setSetCode(code);
        s.setCardCount(total);
        return s;
    }

    private void noImports() {
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser"))
                .thenReturn(Collections.emptyList());
    }

    /**
     * Mocks scryfallCardRepository.findBySetCodeIn() to return one ScryfallCard per
     * unique (setCode, collectorNumber) pair found in {@code cards}.  This ensures
     * that the Scryfall-filter in setUniqueCardCounts / setUniqueNameCounts lets the
     * test cards pass through, just as they would in production when Scryfall data exists.
     */
    private void mockScryfallCards(List<UserCard> cards) {
        Map<String, ScryfallCard> seen = new LinkedHashMap<>();
        for (UserCard uc : cards) {
            String key = uc.getSetCode() + "_" + uc.getCollectorNumber();
            if (!seen.containsKey(key)) {
                ScryfallCard sc = new ScryfallCard();
                sc.setSetCode(uc.getSetCode());
                sc.setCollectorNumber(uc.getCollectorNumber());
                sc.setName(uc.getName());
                seen.put(key, sc);
            }
        }
        lenient().when(scryfallCardRepository.findBySetCodeIn(any()))
                .thenReturn(new ArrayList<>(seen.values()));
    }

    // ── existing tests ────────────────────────────────────────────────────────

    @Test
    void testGetStatisticsForUser_NoCards() {
        when(userCardRepository.findByUser("testuser")).thenReturn(Collections.emptyList());
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals("testuser", stats.getUser());
        assertEquals(0, stats.getTotalUploads());
        assertEquals(0, stats.getTotalCards());
        assertEquals(0.0, stats.getTotalValue());
    }

    @Test
    void testGetStatisticsForUser_WithCards() {
        UserCard c = new UserCard("testuser", "Lightning Bolt", "MLP", "1", 2, false);
        c.setPrice(5.0);
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(c));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTotalCards());
        assertEquals(10.0, stats.getTotalValue());
    }

    @Test
    void testGetStatisticsForUser_MostExpensiveCards() {
        UserCard cheap = new UserCard("testuser", "Card A", "SET1", "1", 1, false);
        cheap.setPrice(1.0);
        UserCard expensive = new UserCard("testuser", "Card B", "SET2", "1", 1, false);
        expensive.setPrice(100.0);
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(cheap, expensive));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getMostExpensiveCards().size());
        assertEquals("Card B", stats.getMostExpensiveCards().get(0).getName());
    }

    @Test
    void testGetStatisticsForUser_TopSetsByCount() {
        // SET1: 3+2 = 5 physical cards; SET2: 5 physical cards
        UserCard c1 = new UserCard("testuser", "Card A", "SET1", "1", 3, false);
        UserCard c2 = new UserCard("testuser", "Card B", "SET1", "2", 2, false);
        UserCard c3 = new UserCard("testuser", "Card C", "SET2", "1", 5, false);
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(c1, c2, c3));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTopSetsByCount().size());
        long set1Count = stats.getTopSetsByCount().stream()
                .filter(s -> s.getSetCode().equals("SET1")).findFirst().orElseThrow().getCount();
        long set2Count = stats.getTopSetsByCount().stream()
                .filter(s -> s.getSetCode().equals("SET2")).findFirst().orElseThrow().getCount();
        assertEquals(5, set1Count);
        assertEquals(5, set2Count);
    }

    @Test
    void testGetStatisticsForUser_Uploads() {
        ImportHistory i1 = new ImportHistory();
        i1.setUser("testuser"); i1.setFormat("inventory"); i1.setImportedAt(LocalDateTime.now().minusDays(1));
        ImportHistory i2 = new ImportHistory();
        i2.setUser("testuser"); i2.setFormat("inventory"); i2.setImportedAt(LocalDateTime.now());
        when(userCardRepository.findByUser("testuser")).thenReturn(Collections.emptyList());
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(List.of(i1, i2));
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTotalUploads());
        assertNotNull(stats.getLastUpload());
    }

    @Test
    void testGetStatisticsForAllUsers() {
        when(mongoTemplate.findDistinct(any(Query.class), eq("user"), eq(UserCard.class), eq(String.class)))
                .thenReturn(List.of("Andre", "Victor"));
        when(userCardRepository.findByUser("Andre")).thenReturn(List.of(
                new UserCard("Andre", "Card", "SET", "1", 1, false)));
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(
                new UserCard("Victor", "Card", "SET", "1", 1, false)));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("Andre")).thenReturn(Collections.emptyList());
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("Victor")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        Map<String, UserStatistics> allStats = statisticsService.getStatisticsForAllUsers();

        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("Andre"));
        assertTrue(allStats.containsKey("Victor"));
    }

    // ── set-completion tests ──────────────────────────────────────────────────

    @Test
    void setCompletion_usesUniqueCollectorNumbers_notQuantitySum() {
        // 3x card #1 + 2x card #2 = 5 physical cards, but only 2 unique cards
        // Set has 10 total → completion must be 2/10 = 20%, not 5/10 = 50%
        List<UserCard> cards = List.of(
                card("DMU", "1", false, 3, 1.0),
                card("DMU", "2", false, 2, 1.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("dmu", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        StatisticsService.SetValue sv = stats.getTopSetsByValue().stream()
                .filter(s -> s.getSetCode().equalsIgnoreCase("dmu"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DMU not found in topSetsByValue"));
        assertEquals(2, sv.getOwnedCards(),
                "ownedCards must count unique collector numbers, not sum of quantities");
    }

    @Test
    void setCompletion_foilAndNormalSameCard_countAsOne() {
        // Card #5 owned as both foil AND normal → still only 1 unique card for completion
        List<UserCard> cards = List.of(
                card("NEO", "5", false, 1, 2.0),
                card("NEO", "5", true,  1, 3.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("neo", 5)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        StatisticsService.SetValue sv = stats.getTopSetsByValue().stream()
                .filter(s -> s.getSetCode().equalsIgnoreCase("neo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("NEO not found in topSetsByValue"));
        assertEquals(1, sv.getOwnedCards(),
                "Foil + Normal of same collector number must count as 1 unique card");
    }

    @Test
    void setCompletion_completeSet_detectedCorrectly() {
        // Own all 3 unique cards in a 3-card set (one with multiple copies) → complete
        List<UserCard> cards = List.of(
                card("TST", "1", false, 2, 1.0),
                card("TST", "2", false, 1, 1.0),
                card("TST", "3", true,  1, 1.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 3)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertFalse(stats.getCompleteSets().isEmpty(),
                "Set where all cards are owned must appear in completeSets");
        assertEquals(100.0, stats.getCompleteSets().get(0).getPercentage(), 0.01);
    }

    @Test
    void setCompletion_incompleteSet_notInCompleteSets() {
        // Own 2 of 5 cards → 40% → neither complete nor near-complete
        List<UserCard> cards = List.of(
                card("TST", "1", false, 1, 1.0),
                card("TST", "2", false, 1, 1.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 5)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getCompleteSets().isEmpty(),     "40% set must not be in completeSets");
        assertTrue(stats.getNearCompleteSets().isEmpty(), "40% set must not be in nearCompleteSets (90%+)");
        assertTrue(stats.getNearComplete80().isEmpty(),   "40% set must not be in nearComplete80");
        assertTrue(stats.getNearComplete70().isEmpty(),   "40% set must not be in nearComplete70");
        assertTrue(stats.getNearComplete60().isEmpty(),   "40% set must not be in nearComplete60");
        assertTrue(stats.getNearComplete50().isEmpty(),   "40% set must not be in nearComplete50");
    }

    @Test
    void setCompletion_nearCompleteSet_detectedAt80Percent() {
        // Own 8 of 10 cards → exactly 80% → nearComplete80
        List<UserCard> cards = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            cards.add(card("TST", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getNearCompleteSets().isEmpty(), "80% set must NOT be in 90%+ list");
        assertFalse(stats.getNearComplete80().isEmpty(),  "80% set must appear in nearComplete80");
        assertEquals(80.0, stats.getNearComplete80().get(0).getPercentage(), 0.01);
    }

    @Test
    void setCompletion_nearCompleteSet_detectedAt90Percent() {
        // Own 9 of 10 cards → exactly 90% → nearComplete
        List<UserCard> cards = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            cards.add(card("TST", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertFalse(stats.getNearCompleteSets().isEmpty(),
                "90% set must appear in nearCompleteSets");
        assertEquals(90.0, stats.getNearCompleteSets().get(0).getPercentage(), 0.01);
    }

    // ── most-valuable-sets $0-filter tests ────────────────────────────────────

    @Test
    void topSetsByValue_excludesSetsWithZeroValue() {
        // SET_A priced, SET_B all $0
        List<UserCard> cards = List.of(
                card("SET_A", "1", false, 1, 5.0),
                card("SET_B", "1", false, 1, 0.0),
                card("SET_B", "2", false, 2, 0.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        boolean hasSetB = stats.getTopSetsByValue().stream()
                .anyMatch(sv -> sv.getSetCode().equals("SET_B"));
        assertFalse(hasSetB, "Sets with $0 total value must not appear in topSetsByValue");
        assertTrue(stats.getTopSetsByValue().stream().anyMatch(sv -> sv.getSetCode().equals("SET_A")));
    }

    @Test
    void topSetsByValue_sortedByValueDescending() {
        List<UserCard> cards = List.of(
                card("LOW",  "1", false, 1,  1.0),
                card("HIGH", "1", false, 1, 10.0),
                card("MID",  "1", false, 1,  5.0)
        );
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        List<StatisticsService.SetValue> sets = stats.getTopSetsByValue();
        assertEquals("HIGH", sets.get(0).getSetCode());
        assertEquals("MID",  sets.get(1).getSetCode());
        assertEquals("LOW",  sets.get(2).getSetCode());
    }

    @Test
    void setCompletion_nearCompleteSet_detectedAt60Percent() {
        // Own 6 of 10 cards → exactly 60% → nearComplete60
        List<UserCard> cards = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            cards.add(card("TST", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getNearComplete70().isEmpty(), "60% set must NOT be in 70%+ list");
        assertFalse(stats.getNearComplete60().isEmpty(),  "60% set must appear in nearComplete60");
        assertEquals(60.0, stats.getNearComplete60().get(0).getPercentage(), 0.01);
    }

    @Test
    void setCompletion_nearCompleteSet_detectedAt50Percent() {
        // Own 5 of 10 cards → exactly 50% → nearComplete50
        List<UserCard> cards = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            cards.add(card("TST", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        mockScryfallCards(cards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getNearComplete60().isEmpty(), "50% set must NOT be in 60%+ list");
        assertFalse(stats.getNearComplete50().isEmpty(),  "50% set must appear in nearComplete50");
        assertEquals(50.0, stats.getNearComplete50().get(0).getPercentage(), 0.01);
    }

    // ── token-set exclusion heuristic tests ───────────────────────────────────

    @Test
    void setCompletion_tokenSetByHeuristic_isExcluded() {
        // "tone" = 4-char code starting with 't' → token set heuristic
        // ScryfallSet present but WITHOUT a setType field set
        List<UserCard> cards = List.of(card("tone", "1", false, 1, 1.0));
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        // Provide a ScryfallSet for "tone" but leave setType null (simulates old DB docs)
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tone", 50)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getCompleteSets().isEmpty(),      "token set must not appear in completeSets");
        assertTrue(stats.getNearCompleteSets().isEmpty(),  "token set must not appear in nearCompleteSets");
        assertTrue(stats.getNearComplete80().isEmpty(),    "token set must not appear in nearComplete80");
        assertTrue(stats.getNearComplete70().isEmpty(),    "token set must not appear in nearComplete70");
        assertTrue(stats.getNearComplete60().isEmpty(),    "token set must not appear in nearComplete60");
        assertTrue(stats.getNearComplete50().isEmpty(),    "token set must not appear in nearComplete50");
    }

    @Test
    void topSetsByValue_tokenSetByHeuristic_isExcluded() {
        // "ttdm" = 4-char code starting with 't' → must not appear in topSetsByValue
        List<UserCard> cards = List.of(card("ttdm", "1", false, 1, 5.0));
        when(userCardRepository.findByUser("testuser")).thenReturn(cards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("ttdm", 80)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertFalse(stats.getTopSetsByValue().stream().anyMatch(sv -> sv.getSetCode().equalsIgnoreCase("ttdm")),
                "Token set (4-char 't' heuristic) must not appear in topSetsByValue");
    }

    @Test
    void setCompletion_scryfallSetCardCount_usedAsDenominator() {
        // ScryfallSet.cardCount (5) is the authoritative denominator, NOT the count of ScryfallCards in DB (7).
        // User owns all 5 unique named cards → 5/5 = 100% complete, regardless of DB having 7 entries.
        List<UserCard> userCards = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            userCards.add(card("PW1", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(userCards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("pw1", 5)));

        // DB has 7 ScryfallCards – these must NOT inflate the denominator
        List<ScryfallCard> sfCards = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            ScryfallCard sc = new ScryfallCard();
            sc.setSetCode("PW1");
            sc.setCollectorNumber(String.valueOf(i));
            sfCards.add(sc);
        }
        when(scryfallCardRepository.findBySetCodeIn(any())).thenReturn(sfCards);

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertFalse(stats.getCompleteSets().isEmpty(),
                "User owns all 5 named cards of a 5-card set → must be 100% complete");
        assertEquals(100.0, stats.getCompleteSets().get(0).getPercentage(), 0.01);
    }

    @Test
    void setCompletion_noFalseHundredPercent_whenOwnedNamesLessThanSetCardCount() {
        // Regression: old bug caused denominator = count of user's ScryfallCards in DB,
        // which equalled the owned count, producing false 100%.
        // New behaviour: ScryfallSet.cardCount (10) is the denominator → 5/10 = 50%.
        List<UserCard> userCards = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            userCards.add(card("MH2", String.valueOf(i), false, 1, 5.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(userCards);
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("mh2", 10)));

        // DB intentionally has only 5 ScryfallCards (old bug: denominator=5=numerator → 100%)
        List<ScryfallCard> sfCards = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ScryfallCard sc = new ScryfallCard();
            sc.setSetCode("MH2");
            sc.setCollectorNumber(String.valueOf(i));
            sfCards.add(sc);
        }
        when(scryfallCardRepository.findBySetCodeIn(any())).thenReturn(sfCards);

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getCompleteSets().isEmpty(),
                "5/10 = 50% — must NOT show as 100% just because DB also has exactly 5 ScryfallCards");
        assertFalse(stats.getNearComplete50().isEmpty(),
                "5/10 = 50% must appear in nearComplete50");
        assertEquals(50.0, stats.getNearComplete50().get(0).getPercentage(), 0.01);
    }

    @Test
    void calculateDailyChanges_duplicateCardKey_doesNotThrow() {
        // Two UserCard entries with identical setCode+collectorNumber+foil but priceUpdatedAt before yesterday
        // This previously caused IllegalStateException: Duplicate key in Collectors.toMap()
        LocalDate twoDaysAgo = LocalDate.now().minusDays(2);

        UserCard c1 = card("NCC", "363", false, 1, 0.17);
        c1.setPriceUpdatedAt(twoDaysAgo);
        UserCard c2 = card("NCC", "363", false, 1, 0.17);
        c2.setPriceUpdatedAt(twoDaysAgo);

        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(c1, c2));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        // Must not throw IllegalStateException
        assertDoesNotThrow(() -> statisticsService.getStatisticsForUser("testuser"));
    }

    @Test
    void setCompletion_allArtworksStats_populatedFromScryfallCache() {
        // Set has 3 standard cards (cardCount = 3), plus 2 Showcase variants (same names as cn=1,2).
        // So total ScryfallCard docs = 5, but only 3 distinct names exist.
        //
        // User owns:
        //   cn=1 "Alpha" (standard)
        //   cn=2 "Beta"  (standard)
        //   cn=4 "Alpha" (showcase variant of Alpha – same name, different cn)
        //
        // Standard completion: 2 unique names / 3 = 66.7% → nearComplete60
        // All-artworks:        3 unique cnumbers / 5 = 60%
        // Special frames:      1 owned showcase / 2 total showcase = 50%

        UserCard uc1 = new UserCard("testuser", "Alpha", "TST", "1", 1, false);
        UserCard uc2 = new UserCard("testuser", "Beta",  "TST", "2", 1, false);
        UserCard uc4 = new UserCard("testuser", "Alpha", "TST", "4", 1, false); // showcase, same name

        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(uc1, uc2, uc4));
        noImports();
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 3)));

        // 5 ScryfallCard docs: cn 1-3 normal, cn 4-5 showcase
        List<ScryfallCard> sfCards = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ScryfallCard sc = new ScryfallCard();
            sc.setSetCode("TST");
            sc.setCollectorNumber(String.valueOf(i));
            sfCards.add(sc);
        }
        for (int i = 4; i <= 5; i++) {
            ScryfallCard sc = new ScryfallCard();
            sc.setSetCode("TST");
            sc.setCollectorNumber(String.valueOf(i));
            sc.setFrameStatus("showcase");   // special frame
            sfCards.add(sc);
        }
        when(scryfallCardRepository.findBySetCodeIn(any())).thenReturn(sfCards);

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        // Standard track: 2 unique names / 3 → 66.7% → nearComplete60
        assertFalse(stats.getNearComplete60().isEmpty(), "2/3 = 66.7% must land in nearComplete60");
        StatisticsService.SetCompletion sc = stats.getNearComplete60().get(0);
        assertEquals(2, sc.getOwnedCards());
        assertEquals(3, sc.getTotalCards());
        assertEquals(66.6, sc.getPercentage(), 0.2);

        // All-artworks track: 3 unique collector numbers / 5 Scryfall docs → 60%
        assertEquals(3, sc.getOwnedAllArtworks(), "3 distinct collector numbers owned");
        assertEquals(5, sc.getTotalAllArtworks(), "5 ScryfallCard docs in cache");
        assertEquals(60.0, sc.getPercentageAllArtworks(), 0.1);

        // Special-frame track: 1 owned showcase (cn=4) / 2 total showcase (cn=4,5) → 50%
        assertEquals(1, sc.getOwnedSpecialFrames(), "only 1 showcase card owned");
        assertEquals(2, sc.getTotalSpecialFrames(), "2 showcase cards in cache");
        assertEquals(50.0, sc.getPercentageSpecialFrames(), 0.1);
    }

    @Test
    void isSpecialFrame_showcase_returnsTrue() {
        ScryfallCard sc = new ScryfallCard();
        sc.setFrameStatus("showcase");
        assertTrue(StatisticsService.isSpecialFrame(sc));
    }

    @Test
    void isSpecialFrame_extendedart_returnsTrue() {
        ScryfallCard sc = new ScryfallCard();
        sc.setFrameStatus("extendedart,legendary");
        assertTrue(StatisticsService.isSpecialFrame(sc));
    }

    @Test
    void isSpecialFrame_borderless_returnsTrue() {
        ScryfallCard sc = new ScryfallCard();
        sc.setBorderColor("borderless");
        assertTrue(StatisticsService.isSpecialFrame(sc));
    }

    @Test
    void isSpecialFrame_retroFrame1997_returnsTrue() {
        ScryfallCard sc = new ScryfallCard();
        sc.setFrame("1997");
        assertTrue(StatisticsService.isSpecialFrame(sc));
    }

    @Test
    void isSpecialFrame_normalCard_returnsFalse() {
        ScryfallCard sc = new ScryfallCard();
        sc.setFrame("2015");
        sc.setBorderColor("black");
        assertFalse(StatisticsService.isSpecialFrame(sc));
    }

    @Test
    void isSpecialFrame_fullArtOnly_returnsFalse() {
        // Full-art cards (e.g. full-art basics) are in the standard card count; not a special frame.
        ScryfallCard sc = new ScryfallCard();
        sc.setFullArt(true);
        assertFalse(StatisticsService.isSpecialFrame(sc));
    }

    // ── SetCompletion computed standard-card getters ──────────────────────────

    @Test
    void setCompletion_unmatchedUserCards_notCountedTowardCompletion() {
        // pw11-like scenario: user has 7 UserCards but only 2 have ScryfallCard matches.
        // Scryfall knows 3 cards for the set → user is at 2/3 = 66%, NOT 7/3 = 233%.
        List<UserCard> allUserCards = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            allUserCards.add(card("PW11", String.valueOf(i), false, 1, 1.0));
        }
        when(userCardRepository.findByUser("testuser")).thenReturn(allUserCards);
        noImports();
        // Only CNs 1 and 2 have ScryfallCard matches (3 is missing, 4-7 unknown to Scryfall)
        List<UserCard> matchedCards = allUserCards.subList(0, 2); // CN 1 and 2
        mockScryfallCards(matchedCards);
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("pw11", 3)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        // Should be in 60%+ tier (2/3 = 66.7%), not in completeSets
        assertTrue(stats.getCompleteSets().isEmpty(),    "2/3 matched must NOT appear in completeSets");
        assertTrue(stats.getNearCompleteSets().isEmpty(),"2/3 must NOT appear in nearCompleteSets (90%+)");
        assertTrue(stats.getNearComplete80().isEmpty(),  "2/3 must NOT appear in nearComplete80");
        assertFalse(stats.getNearComplete60().isEmpty(), "2/3 = 66% must appear in nearComplete60");
        assertEquals(2.0 / 3.0 * 100.0, stats.getNearComplete60().get(0).getPercentage(), 0.01,
                "percentage must be 66.7%, not 233%");
    }

    @Test
    void setCompletion_standardGetters_computedFromAllArtworksMinusSpecial() {
        StatisticsService.SetCompletion sc = new StatisticsService.SetCompletion("TST", 5, 10, 50.0);
        sc.setAllArtworksStats(12, 15);  // owned=12, total=15
        sc.setSpecialFrameStats(3, 5);   // owned=3, total=5

        // Standard = AllArtworks - Special
        assertEquals(12 - 3, sc.getOwnedStandardCards(),  "ownedStandardCards = ownedAllArtworks - ownedSpecialFrames");
        assertEquals(15 - 5, sc.getTotalStandardCards(),  "totalStandardCards = totalAllArtworks - totalSpecialFrames");
        assertEquals(9.0 / 10.0 * 100.0, sc.getPercentageStandard(), 0.01, "percentageStandard = 90%");
    }

    @Test
    void setCompletion_standardGetters_zeroTotalAllArtworks_returnsZeroPercent() {
        StatisticsService.SetCompletion sc = new StatisticsService.SetCompletion("TST", 0, 5, 0.0);
        // Neither setter called → both remain 0
        assertEquals(0, sc.getOwnedStandardCards());
        assertEquals(0, sc.getTotalStandardCards());
        assertEquals(0.0, sc.getPercentageStandard(), 0.001, "Must not divide by zero");
    }

    @Test
    void setCompletion_standardGetters_noSpecialFrames_equalsAllArtworks() {
        StatisticsService.SetCompletion sc = new StatisticsService.SetCompletion("TST", 8, 10, 80.0);
        sc.setAllArtworksStats(8, 10);
        sc.setSpecialFrameStats(0, 0);  // no special frames

        assertEquals(8, sc.getOwnedStandardCards());
        assertEquals(10, sc.getTotalStandardCards());
        assertEquals(80.0, sc.getPercentageStandard(), 0.01);
    }

    // ── getMissingCards tests ─────────────────────────────────────────────────

    @Test
    void getMissingCards_splitsByStandardAndSpecial() {
        // User owns cn=1 (standard). Missing: cn=2 (standard) + cn=3 (showcase)
        UserCard owned = new UserCard("victor", "Alpha", "MH2", "1", 1, false);
        when(userCardRepository.findByUserAndSetCode("victor", "MH2")).thenReturn(List.of(owned));

        ScryfallCard sc1 = new ScryfallCard(); sc1.setSetCode("MH2"); sc1.setCollectorNumber("1"); sc1.setName("Alpha");
        ScryfallCard sc2 = new ScryfallCard(); sc2.setSetCode("MH2"); sc2.setCollectorNumber("2"); sc2.setName("Beta");
        ScryfallCard sc3 = new ScryfallCard(); sc3.setSetCode("MH2"); sc3.setCollectorNumber("3"); sc3.setName("Alpha SC"); sc3.setFrameStatus("showcase");
        when(scryfallCardRepository.findBySetCode("MH2")).thenReturn(List.of(sc1, sc2, sc3));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = statisticsService.getMissingCards("victor", "MH2");

        List<?> standard = (List<?>) result.get("standard");
        List<?> special  = (List<?>) result.get("special");

        assertEquals(1, standard.size(), "Beta (cn=2) is missing standard");
        assertEquals(1, special.size(),  "Alpha SC (cn=3) is missing special");
    }

    @Test
    void getMissingCards_ownedCardNotInMissingList() {
        UserCard owned = new UserCard("victor", "Alpha", "TST", "1", 1, false);
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of(owned));

        ScryfallCard sc1 = new ScryfallCard(); sc1.setSetCode("TST"); sc1.setCollectorNumber("1"); sc1.setName("Alpha");
        when(scryfallCardRepository.findBySetCode("TST")).thenReturn(List.of(sc1));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = statisticsService.getMissingCards("victor", "TST");

        assertEquals(0, ((List<?>) result.get("standard")).size(), "No missing standard cards");
        assertEquals(0, ((List<?>) result.get("special")).size(),  "No missing special cards");
    }

    @Test
    void getMissingCards_sortedByCollectorNumberNumeric() {
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(Collections.emptyList());

        ScryfallCard sc10 = new ScryfallCard(); sc10.setSetCode("TST"); sc10.setCollectorNumber("10"); sc10.setName("Ten");
        ScryfallCard sc2  = new ScryfallCard(); sc2.setSetCode("TST");  sc2.setCollectorNumber("2");  sc2.setName("Two");
        ScryfallCard sc1  = new ScryfallCard(); sc1.setSetCode("TST");  sc1.setCollectorNumber("1");  sc1.setName("One");
        when(scryfallCardRepository.findBySetCode("TST")).thenReturn(List.of(sc10, sc2, sc1));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = statisticsService.getMissingCards("victor", "TST");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> standard = (List<Map<String, Object>>) result.get("standard");

        assertEquals("1",  standard.get(0).get("number"));
        assertEquals("2",  standard.get(1).get("number"));
        assertEquals("10", standard.get(2).get("number"));
    }
}
