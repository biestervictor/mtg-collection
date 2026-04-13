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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private UserCardRepository userCardRepository;

    @Mock
    private ImportHistoryRepository importHistoryRepository;

    @Mock
    private ScryfallService scryfallService;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(userCardRepository, importHistoryRepository, scryfallService);
    }

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
        UserCard card = new UserCard("testuser", "Lightning Bolt", "MLP", "1", 2, false);
        card.setPrice(5.0);
        
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(card));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTotalCards());
        assertEquals(10.0, stats.getTotalValue()); // 2 * 5.0
    }

    @Test
    void testGetStatisticsForUser_MostExpensiveCards() {
        UserCard cheapCard = new UserCard("testuser", "Card A", "SET1", "1", 1, false);
        cheapCard.setPrice(1.0);
        
        UserCard expensiveCard = new UserCard("testuser", "Card B", "SET2", "1", 1, false);
        expensiveCard.setPrice(100.0);
        
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(cheapCard, expensiveCard));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getMostExpensiveCards().size());
        assertEquals("Card B", stats.getMostExpensiveCards().get(0).getName());
    }

    @Test
    void testGetStatisticsForUser_TopSetsByCount() {
        UserCard card1 = new UserCard("testuser", "Card A", "SET1", "1", 3, false);
        UserCard card2 = new UserCard("testuser", "Card B", "SET1", "2", 2, false);
        UserCard card3 = new UserCard("testuser", "Card C", "SET2", "1", 5, false);
        
        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(card1, card2, card3));
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTopSetsByCount().size());
        assertEquals("SET2", stats.getTopSetsByCount().get(0).getSetCode());
        assertEquals(5, stats.getTopSetsByCount().get(0).getCount());
    }

    @Test
    void testGetStatisticsForUser_Uploads() {
        ImportHistory import1 = new ImportHistory();
        import1.setUser("testuser");
        import1.setFormat("inventory");
        import1.setImportedAt(LocalDateTime.now().minusDays(1));
        
        ImportHistory import2 = new ImportHistory();
        import2.setUser("testuser");
        import2.setFormat("inventory");
        import2.setImportedAt(LocalDateTime.now());
        
        when(userCardRepository.findByUser("testuser")).thenReturn(Collections.emptyList());
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("testuser")).thenReturn(List.of(import1, import2));
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        UserStatistics stats = statisticsService.getStatisticsForUser("testuser");

        assertEquals(2, stats.getTotalUploads());
        assertNotNull(stats.getLastUpload());
    }

    @Test
    void testGetStatisticsForAllUsers() {
        when(userCardRepository.findAll()).thenReturn(List.of(
                new UserCard("user1", "Card", "SET", "1", 1, false),
                new UserCard("user2", "Card", "SET", "1", 1, false)
        ));
        
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("user1")).thenReturn(Collections.emptyList());
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("user2")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllSets(false)).thenReturn(Collections.emptyList());

        Map<String, UserStatistics> allStats = statisticsService.getStatisticsForAllUsers();

        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("user1"));
        assertTrue(allStats.containsKey("user2"));
    }
}