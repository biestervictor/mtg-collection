package com.mtg.collection.service;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private UserCardRepository userCardRepository;
    @Mock private ImportHistoryRepository importHistoryRepository;
    @Mock private ScryfallService scryfallService;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(userCardRepository, importHistoryRepository, scryfallService);
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
        when(userCardRepository.findAll()).thenReturn(List.of(
                new UserCard("Andre",  "Card", "SET", "1", 1, false),
                new UserCard("Victor", "Card", "SET", "1", 1, false)
        ));
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
        when(scryfallService.getAllSets(false)).thenReturn(List.of(scryfallSet("tst", 10)));

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertTrue(stats.getNearComplete60().isEmpty(), "50% set must NOT be in 60%+ list");
        assertFalse(stats.getNearComplete50().isEmpty(),  "50% set must appear in nearComplete50");
        assertEquals(50.0, stats.getNearComplete50().get(0).getPercentage(), 0.01);
    }
}
