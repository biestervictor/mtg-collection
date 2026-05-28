package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.model.UserDeck;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeckServiceTest {

    @Mock private UserDeckRepository     userDeckRepository;
    @Mock private ScryfallCardRepository scryfallCardRepository;

    private UserDeckService service;

    @BeforeEach
    void setUp() {
        service = new UserDeckService(userDeckRepository, scryfallCardRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserCard card(String folder, String setCode, String cn, int qty, boolean foil) {
        UserCard uc = new UserCard("victor", "Alpha", setCode, cn, qty, foil);
        uc.setFolderName(folder);
        return uc;
    }

    // ── buildAndSaveDecks – folder parsing ───────────────────────────────────

    @Test
    void buildAndSaveDecks_mb_createsMainboardDeck() {
        List<UserCard> cards = List.of(card("MB_Aggro", "TST", "1", 4, false));
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());

        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals("Aggro", saved.getName());
        assertFalse(saved.isCommander());
        assertEquals(1, saved.getMainboard().size());
        assertEquals(4, saved.getMainboard().get(0).getQuantity());
    }

    @Test
    void buildAndSaveDecks_mbCm_createsCommanderDeck() {
        List<UserCard> cards = List.of(card("MB_CM_Slivers", "TST", "1", 1, false));
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());

        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals("Slivers", saved.getName());
        assertTrue(saved.isCommander());
    }

    @Test
    void buildAndSaveDecks_sb_createsSideboard() {
        List<UserCard> cards = List.of(
                card("MB_Control", "TST", "1", 4, false),
                card("SB_Control", "TST", "2", 2, false)
        );
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());

        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals(1, saved.getMainboard().size());
        assertEquals(1, saved.getSideboard().size());
    }

    @Test
    void buildAndSaveDecks_eb_createsExtraboard() {
        List<UserCard> cards = List.of(card("EB_Sidisi", "TST", "1", 1, false));
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());

        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals(1, saved.getExtraboard().size());
    }

    @Test
    void buildAndSaveDecks_unknownFolder_ignored() {
        List<UserCard> cards = List.of(card("Collection", "TST", "1", 1, false));

        service.buildAndSaveDecks("victor", cards);

        verify(userDeckRepository, never()).saveAll(any());
    }

    @Test
    void buildAndSaveDecks_nullFolder_ignored() {
        List<UserCard> cards = List.of(card(null, "TST", "1", 1, false));

        service.buildAndSaveDecks("victor", cards);

        verify(userDeckRepository, never()).saveAll(any());
    }

    @Test
    void buildAndSaveDecks_emptyFolderPrefix_ignored() {
        // "MB_" with no name after prefix → should be skipped
        List<UserCard> cards = List.of(card("MB_", "TST", "1", 1, false));

        service.buildAndSaveDecks("victor", cards);

        verify(userDeckRepository, never()).saveAll(any());
    }

    @Test
    void buildAndSaveDecks_duplicateCardKeys_quantitiesMerged() {
        // Two rows with same setCode+CN+foil in MB_ → should merge to one DeckCard with summed qty
        List<UserCard> cards = List.of(
                card("MB_Aggro", "TST", "1", 2, false),
                card("MB_Aggro", "TST", "1", 3, false)
        );
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());

        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals(1, saved.getMainboard().size(), "Duplicate entries should be merged");
        assertEquals(5, saved.getMainboard().get(0).getQuantity());
    }

    @Test
    void buildAndSaveDecks_enrichesWithScryfallData() {
        ScryfallCard sf = new ScryfallCard();
        sf.setSetCode("tst"); sf.setCollectorNumber("1");
        sf.setThumbnailFront("http://img/thumb.jpg");
        sf.setPriceRegular(3.50);
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(List.of(sf));

        List<UserCard> cards = List.of(card("MB_Aggro", "TST", "1", 1, false));
        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals("http://img/thumb.jpg", saved.getMainboard().get(0).getThumbnailUrl());
        assertEquals(3.50, saved.getMainboard().get(0).getPrice(), 0.001);
    }

    @Test
    void buildAndSaveDecks_foilCard_usesFoilPrice() {
        ScryfallCard sf = new ScryfallCard();
        sf.setSetCode("tst"); sf.setCollectorNumber("1");
        sf.setPriceRegular(1.0); sf.setPriceFoil(8.0);
        when(scryfallCardRepository.findBySetCode("tst")).thenReturn(List.of(sf));

        List<UserCard> cards = List.of(card("MB_Aggro", "TST", "1", 1, true));
        service.buildAndSaveDecks("victor", cards);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDeck>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDeckRepository).saveAll(captor.capture());
        UserDeck saved = captor.getValue().iterator().next();

        assertEquals(8.0, saved.getMainboard().get(0).getPrice(), 0.001, "Foil card should use foil price");
    }

    // ── getDecksForUser ───────────────────────────────────────────────────────

    @Test
    void getDecksForUser_delegatesToRepository() {
        UserDeck deck = new UserDeck();
        deck.setName("Aggro");
        when(userDeckRepository.findByUserOrderByCommanderDescNameAsc("victor"))
                .thenReturn(List.of(deck));

        List<UserDeck> result = service.getDecksForUser("victor");

        assertEquals(1, result.size());
    }

    // ── getDeckById ───────────────────────────────────────────────────────────

    @Test
    void getDeckById_found() {
        UserDeck deck = new UserDeck(); deck.setName("Aggro");
        when(userDeckRepository.findById("victor_Aggro")).thenReturn(Optional.of(deck));

        Optional<UserDeck> result = service.getDeckById("victor_Aggro");

        assertTrue(result.isPresent());
        assertEquals("Aggro", result.get().getName());
    }

    @Test
    void getDeckById_notFound() {
        when(userDeckRepository.findById("victor_Ghost")).thenReturn(Optional.empty());

        Optional<UserDeck> result = service.getDeckById("victor_Ghost");

        assertTrue(result.isEmpty());
    }

    // ── deleteDecksForUser ────────────────────────────────────────────────────

    @Test
    void deleteDecksForUser_delegatesToRepository() {
        service.deleteDecksForUser("victor");
        verify(userDeckRepository).deleteByUser("victor");
    }

    // ── reEnrichDecks ─────────────────────────────────────────────────────────

    @Test
    void reEnrichDecks_noDecks_returns0() {
        when(userDeckRepository.findByUserOrderByCommanderDescNameAsc("victor"))
                .thenReturn(Collections.emptyList());

        int count = service.reEnrichDecks("victor");

        assertEquals(0, count);
        verify(userDeckRepository, never()).saveAll(any());
    }

    @Test
    void reEnrichDecks_withDecks_savesAndReturnsCount() {
        UserDeck deck = new UserDeck();
        deck.setUser("victor"); deck.setName("Aggro");
        when(userDeckRepository.findByUserOrderByCommanderDescNameAsc("victor"))
                .thenReturn(List.of(deck));

        int count = service.reEnrichDecks("victor");

        assertEquals(1, count);
        verify(userDeckRepository).saveAll(any());
    }
}
