package com.mtg.collection.service;

import com.mtg.collection.model.PriceHistory;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.PriceHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.ScryfallSetRepository;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import com.mtg.collection.service.PriceHistoryService.PriceChange;
import com.mtg.collection.service.PriceHistoryService.SetSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private UserCardRepository     userCardRepository;
    @Mock private ScryfallCardRepository scryfallCardRepository;
    @Mock private ScryfallSetRepository  scryfallSetRepository;
    @Mock private UserDeckRepository     userDeckRepository;

    private PriceHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PriceHistoryService(
                priceHistoryRepository, userCardRepository,
                scryfallCardRepository, scryfallSetRepository,
                userDeckRepository);

        // Lenient defaults so tests that don't care about these calls don't NPE
        lenient().when(userCardRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(userDeckRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(scryfallCardRepository.findBySetCodeIn(anyCollection())).thenReturn(Collections.emptyList());
        lenient().when(scryfallSetRepository.findAll()).thenReturn(Collections.emptyList());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserCard userCard(String setCode, String cn, String name) {
        return new UserCard("Victor", name, setCode, cn, 1, false);
    }

    private ScryfallCard sfCard(String setCode, String cn, Double regular, Double foil) {
        ScryfallCard sc = new ScryfallCard();
        sc.setSetCode(setCode);
        sc.setCollectorNumber(cn);
        sc.setPriceRegular(regular);
        sc.setPriceFoil(foil);
        sc.setThumbnailFront("https://example.com/thumb.jpg");
        return sc;
    }

    private PriceHistory ph(String setCode, String cn, String name, LocalDate date,
                            Double regular, Double foil) {
        return new PriceHistory(setCode, cn, name, null, date, regular, foil);
    }

    // ── snapshotOwnedCardPrices ───────────────────────────────────────────────

    @Test
    void snapshot_noUserCards_returnsZero() {
        when(userCardRepository.findAll()).thenReturn(Collections.emptyList());

        int result = service.snapshotOwnedCardPrices();

        assertEquals(0, result);
        verify(priceHistoryRepository, never()).saveAll(any());
    }

    @Test
    void snapshot_withCards_savesOneDocumentPerDistinctCard() {
        UserCard c1 = userCard("stx", "1", "Pop Quiz");
        UserCard c2 = userCard("stx", "1", "Pop Quiz"); // same card, different user
        UserCard c3 = userCard("stx", "2", "Test Card");
        when(userCardRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        ScryfallCard sf1 = sfCard("stx", "1", 2.50, 5.00);
        ScryfallCard sf2 = sfCard("stx", "2", 1.00, null);
        when(scryfallCardRepository.findBySetCodeIn(anyCollection())).thenReturn(List.of(sf1, sf2));

        when(priceHistoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.snapshotOwnedCardPrices();

        // 2 distinct cards (c1 and c2 are the same setCode+cn)
        assertEquals(2, count);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceHistoryRepository).saveAll(captor.capture());
        List<PriceHistory> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(p -> p.getCollectorNumber().equals("1") && p.getPriceRegular() == 2.50));
        assertTrue(saved.stream().anyMatch(p -> p.getCollectorNumber().equals("2") && p.getPriceRegular() == 1.00));
    }

    @Test
    void snapshot_cardWithNoScryfallData_isSkipped() {
        UserCard c1 = userCard("stx", "99", "Unknown Card");
        when(userCardRepository.findAll()).thenReturn(List.of(c1));
        when(scryfallCardRepository.findBySetCodeIn(anyCollection())).thenReturn(Collections.emptyList());
        when(priceHistoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.snapshotOwnedCardPrices();
        assertEquals(0, count);
    }

    @Test
    void snapshot_prunesOldHistory() {
        UserCard c = userCard("stx", "1", "Pop Quiz");
        when(userCardRepository.findAll()).thenReturn(List.of(c));
        when(scryfallCardRepository.findBySetCodeIn(anyCollection()))
                .thenReturn(List.of(sfCard("stx", "1", 2.50, null)));
        when(priceHistoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.snapshotOwnedCardPrices();

        // Verify pruning is called with a date 90 days in the past
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(priceHistoryRepository).deleteByDateBefore(dateCaptor.capture());
        assertEquals(LocalDate.now().minusDays(90), dateCaptor.getValue());
    }

    // ── getTopWinners / getTopLosers ──────────────────────────────────────────

    @Test
    void getTopWinners_noSnapshots_returnsEmpty() {
        // existsByDate always false → no dates found
        when(priceHistoryRepository.existsByDate(any())).thenReturn(false);

        List<PriceChange> winners = service.getTopWinners(10, 1.0);
        assertTrue(winners.isEmpty());
    }

    @Test
    void getTopWinners_onlyOneDate_returnsEmpty() {
        LocalDate today = LocalDate.now();
        // Only today exists; all other dates return false (Mockito default for boolean)
        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);

        List<PriceChange> winners = service.getTopWinners(10, 1.0);
        assertTrue(winners.isEmpty());
    }

    @Test
    void getTopWinners_returnsCardsWithPriceIncrease() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.existsByDate(yesterday)).thenReturn(true);

        PriceHistory todayCard    = ph("stx", "1", "Winner Card", today,     5.00, null);
        PriceHistory yesterdayCard= ph("stx", "1", "Winner Card", yesterday, 4.00, null);
        PriceHistory todayLoser   = ph("stx", "2", "Loser Card",  today,     2.00, null);
        PriceHistory yestLoser    = ph("stx", "2", "Loser Card",  yesterday, 3.00, null);

        when(priceHistoryRepository.findByDate(today)).thenReturn(List.of(todayCard, todayLoser));
        when(priceHistoryRepository.findByDate(yesterday)).thenReturn(List.of(yesterdayCard, yestLoser));

        List<PriceChange> winners = service.getTopWinners(10, 1.0);

        assertEquals(1, winners.size());
        assertEquals("Winner Card", winners.get(0).getCardName());
        assertEquals(25.0, winners.get(0).getPercentChange(), 0.01);
        assertEquals(1.0,  winners.get(0).getAbsoluteChange(), 0.001);
    }

    @Test
    void getTopLosers_returnsCardsWithPriceDecrease() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.existsByDate(yesterday)).thenReturn(true);

        PriceHistory todayCard    = ph("stx", "1", "Loser Card",  today,     3.00, null);
        PriceHistory yesterdayCard= ph("stx", "1", "Loser Card",  yesterday, 4.00, null);

        when(priceHistoryRepository.findByDate(today)).thenReturn(List.of(todayCard));
        when(priceHistoryRepository.findByDate(yesterday)).thenReturn(List.of(yesterdayCard));

        List<PriceChange> losers = service.getTopLosers(10, 1.0);

        assertEquals(1, losers.size());
        assertEquals("Loser Card", losers.get(0).getCardName());
        assertTrue(losers.get(0).getAbsoluteChange() < 0);
        assertEquals(-25.0, losers.get(0).getPercentChange(), 0.01);
    }

    @Test
    void getTopWinners_filtersCardsBelowMinPrice() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.existsByDate(yesterday)).thenReturn(true);

        // Cheap card: price today 0.50, yesterday 0.30 — should be filtered out (minPrice=1.0)
        PriceHistory cheap     = ph("stx", "99", "Cheap",  today,     0.50, null);
        PriceHistory cheapPrev = ph("stx", "99", "Cheap",  yesterday, 0.30, null);

        when(priceHistoryRepository.findByDate(today)).thenReturn(List.of(cheap));
        when(priceHistoryRepository.findByDate(yesterday)).thenReturn(List.of(cheapPrev));

        List<PriceChange> winners = service.getTopWinners(10, 1.0);
        assertTrue(winners.isEmpty());
    }

    @Test
    void getTopWinners_limitsResults() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.existsByDate(yesterday)).thenReturn(true);

        // Build 5 cards all increasing in price
        List<PriceHistory> todayList     = new java.util.ArrayList<>();
        List<PriceHistory> yesterdayList = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            todayList.add(ph("stx", String.valueOf(i), "Card " + i, today,     i * 2.0, null));
            yesterdayList.add(ph("stx", String.valueOf(i), "Card " + i, yesterday, i * 1.0, null));
        }
        when(priceHistoryRepository.findByDate(today)).thenReturn(todayList);
        when(priceHistoryRepository.findByDate(yesterday)).thenReturn(yesterdayList);

        List<PriceChange> winners = service.getTopWinners(3, 1.0);
        assertEquals(3, winners.size());
    }

    // ── getSetSummaries ───────────────────────────────────────────────────────

    @Test
    void getSetSummaries_noSnapshots_returnsEmpty() {
        when(priceHistoryRepository.existsByDate(any())).thenReturn(false);
        assertTrue(service.getSetSummaries(1.0, 10).isEmpty());
    }

    @Test
    void getSetSummaries_groupsBySet() {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.existsByDate(yesterday)).thenReturn(true);

        when(priceHistoryRepository.findByDate(today)).thenReturn(List.of(
                ph("stx", "1", "STX Card A",  today, 5.0, null),
                ph("mh2", "1", "MH2 Card",    today, 8.0, null)
        ));
        when(priceHistoryRepository.findByDate(yesterday)).thenReturn(List.of(
                ph("stx", "1", "STX Card A",  yesterday, 4.0, null),
                ph("mh2", "1", "MH2 Card",    yesterday, 9.0, null)
        ));

        ScryfallSet stxSet = new ScryfallSet();
        stxSet.setSetCode("stx");
        stxSet.setName("Strixhaven");
        ScryfallSet mh2Set = new ScryfallSet();
        mh2Set.setSetCode("mh2");
        mh2Set.setName("Modern Horizons 2");
        when(scryfallSetRepository.findAll()).thenReturn(List.of(stxSet, mh2Set));

        List<SetSummary> summaries = service.getSetSummaries(1.0, 10);
        assertEquals(2, summaries.size());

        SetSummary stx = summaries.stream().filter(s -> s.getSetCode().equals("stx")).findFirst().orElseThrow();
        assertEquals("Strixhaven", stx.getSetName());
        assertEquals(1, stx.getTopWinners().size());
        assertTrue(stx.getTopLosers().isEmpty());

        SetSummary mh2 = summaries.stream().filter(s -> s.getSetCode().equals("mh2")).findFirst().orElseThrow();
        assertEquals(1, mh2.getTopLosers().size());
        assertTrue(mh2.getTopWinners().isEmpty());
    }

    // ── getPriceHistory ───────────────────────────────────────────────────────

    @Test
    void getPriceHistory_delegatesToRepository() {
        String setCode = "stx", cn = "1";
        List<PriceHistory> expected = List.of(ph(setCode, cn, "Pop Quiz", LocalDate.now(), 2.5, null));
        when(priceHistoryRepository.findBySetCodeAndCollectorNumberOrderByDateAsc(setCode, cn))
                .thenReturn(expected);

        List<PriceHistory> result = service.getPriceHistory(setCode, cn);
        assertEquals(expected, result);
    }

    @Test
    void getPriceHistory_normalisesSetCodeToLowercase() {
        service.getPriceHistory("STX", "1");
        verify(priceHistoryRepository).findBySetCodeAndCollectorNumberOrderByDateAsc("stx", "1");
    }

    // ── getLastSnapshotDate / getTotalTrackedCards ─────────────────────────────

    @Test
    void getLastSnapshotDate_noData_returnsNull() {
        when(priceHistoryRepository.existsByDate(any())).thenReturn(false);
        assertNull(service.getLastSnapshotDate());
    }

    @Test
    void getLastSnapshotDate_returnsLatestDate() {
        LocalDate today = LocalDate.now();
        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);

        assertEquals(today, service.getLastSnapshotDate());
    }

    @Test
    void getTotalTrackedCards_noData_returnsZero() {
        when(priceHistoryRepository.existsByDate(any())).thenReturn(false);
        assertEquals(0, service.getTotalTrackedCards());
    }

    @Test
    void getTotalTrackedCards_returnsCountOfLatestSnapshot() {
        LocalDate today = LocalDate.now();
        when(priceHistoryRepository.existsByDate(today)).thenReturn(true);
        when(priceHistoryRepository.findByDate(today)).thenReturn(
                List.of(ph("stx", "1", "Card A", today, 2.0, null),
                        ph("stx", "2", "Card B", today, 3.0, null)));

        assertEquals(2, service.getTotalTrackedCards());
    }
}
