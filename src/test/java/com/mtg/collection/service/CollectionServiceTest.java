package com.mtg.collection.service;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private UserCardRepository      userCardRepository;
    @Mock private ScryfallCardRepository  scryfallCardRepository;
    @Mock private ScryfallService         scryfallService;
    @Mock private ImportHistoryRepository importHistoryRepository;
    @Mock private UserDeckService         userDeckService;

    private CollectionService service;

    @BeforeEach
    void setUp() {
        service = new CollectionService(
                userCardRepository, scryfallCardRepository,
                scryfallService, importHistoryRepository, userDeckService);
    }

    // ── getUserCardsBySet ─────────────────────────────────────────────────────

    @Test
    void getUserCardsBySet_delegatesToRepository() {
        UserCard uc = new UserCard("victor", "Alpha", "TST", "1", 1, false);
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of(uc));

        List<UserCard> result = service.getUserCardsBySet("victor", "TST");

        assertEquals(1, result.size());
        assertEquals("Alpha", result.get(0).getName());
    }

    // ── getAllUserCards ────────────────────────────────────────────────────────

    @Test
    void getAllUserCards_delegatesToRepository() {
        when(userCardRepository.findByUser("victor")).thenReturn(List.of(
                new UserCard("victor", "Alpha", "TST", "1", 2, false)));

        List<UserCard> result = service.getAllUserCards("victor");

        assertEquals(1, result.size());
    }

    // ── saveUserCards ─────────────────────────────────────────────────────────

    @Test
    void saveUserCards_deletesOldAndSavesNew() {
        UserCard uc = new UserCard();
        uc.setName("Beta");

        service.saveUserCards("victor", List.of(uc));

        verify(userCardRepository).deleteByUser("victor");
        verify(userCardRepository).saveAll(anyList());
        assertEquals("victor", uc.getUser());
    }

    // ── deleteUserData ────────────────────────────────────────────────────────

    @Test
    void deleteUserData_deletesCardsDecksAndHistory() {
        service.deleteUserData("victor");

        verify(userCardRepository).deleteByUser("victor");
        verify(userDeckService).deleteDecksForUser("victor");
        verify(importHistoryRepository).deleteByUser("victor");
    }

    // ── getCardsWithUserData ──────────────────────────────────────────────────

    @Test
    void getCardsWithUserData_mergesRegularAndFoilCounts() {
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode("TST"); sc.setCollectorNumber("1"); sc.setName("Alpha");
        when(scryfallService.getCardsBySet("TST", null)).thenReturn(List.of(sc));

        UserCard regular = new UserCard("victor", "Alpha", "TST", "1", 2, false);
        UserCard foil    = new UserCard("victor", "Alpha", "TST", "1", 1, true);
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of(regular, foil));

        List<CardWithUserData> result = service.getCardsWithUserData("victor", "TST", null);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getQuantity());
        assertEquals(1, result.get(0).getFoilQuantity());
    }

    @Test
    void getCardsWithUserData_noUserCards_quantitiesAreZero() {
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode("TST"); sc.setCollectorNumber("1"); sc.setName("Alpha");
        when(scryfallService.getCardsBySet("TST", null)).thenReturn(List.of(sc));
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of());

        List<CardWithUserData> result = service.getCardsWithUserData("victor", "TST", null);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getQuantity());
        assertEquals(0, result.get(0).getFoilQuantity());
    }

    @Test
    void getCardsWithUserData_deduplicatesScryfallCardsByCn() {
        // Two ScryfallCard docs with the same CN (e.g. duplicate DB entries) → only one in result
        ScryfallCard sc1 = new ScryfallCard();
        sc1.setSetCode("TST"); sc1.setCollectorNumber("1"); sc1.setName("Alpha");
        ScryfallCard sc2 = new ScryfallCard();
        sc2.setSetCode("TST"); sc2.setCollectorNumber("1"); sc2.setName("Alpha");
        when(scryfallService.getCardsBySet("TST", null)).thenReturn(List.of(sc1, sc2));
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of());

        List<CardWithUserData> result = service.getCardsWithUserData("victor", "TST", null);

        assertEquals(1, result.size(), "Duplicate CN must be collapsed to one entry");
    }

    @Test
    void getCardsWithUserData_negativeQuantityClamped() {
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode("TST"); sc.setCollectorNumber("1"); sc.setName("Alpha");
        when(scryfallService.getCardsBySet("TST", null)).thenReturn(List.of(sc));

        UserCard broken = new UserCard("victor", "Alpha", "TST", "1", -3, false);
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of(broken));

        List<CardWithUserData> result = service.getCardsWithUserData("victor", "TST", null);

        assertEquals(0, result.get(0).getQuantity(), "Negative quantity must be clamped to 0");
    }

    @Test
    void getCardsWithUserData_duplicateUserCardsSummedByKey() {
        // Two UserCard docs with same setCode+CN+foil — quantities must be summed, not doubled
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode("TST"); sc.setCollectorNumber("1"); sc.setName("Alpha");
        when(scryfallService.getCardsBySet("TST", null)).thenReturn(List.of(sc));

        UserCard uc1 = new UserCard("victor", "Alpha", "TST", "1", 2, false);
        UserCard uc2 = new UserCard("victor", "Alpha", "TST", "1", 3, false);
        when(userCardRepository.findByUserAndSetCode("victor", "TST")).thenReturn(List.of(uc1, uc2));

        List<CardWithUserData> result = service.getCardsWithUserData("victor", "TST", null);

        assertEquals(5, result.get(0).getQuantity(), "Duplicate UserCards should be summed");
    }
}
