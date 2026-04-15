package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceUpdateServiceTest {

    @Mock
    private UserCardRepository userCardRepository;

    @Mock
    private ScryfallService scryfallService;

    private PriceUpdateService priceUpdateService;

    @BeforeEach
    void setUp() {
        priceUpdateService = new PriceUpdateService(userCardRepository, scryfallService);
    }

    @Test
    void testUpdatePricesForUser_NoCards() {
        when(userCardRepository.findByUser("testuser")).thenReturn(Collections.emptyList());
        when(scryfallService.getAllCards(false)).thenReturn(Collections.emptyList());

        int updated = priceUpdateService.updatePricesForUser("testuser");

        assertEquals(0, updated);
        verify(userCardRepository, never()).save(any());
    }

    @Test
    void testUpdatePricesForUser_WithPriceChange() {
        UserCard card = new UserCard("testuser", "Lightning Bolt", "MLP", "1", 1, false);
        card.setPrice(5.0);

        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(card));

        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setSetCode("MLP");
        sfCard.setCollectorNumber("1");
        sfCard.setPriceRegular(10.0);

        when(scryfallService.getAllCards(false)).thenReturn(List.of(sfCard));

        int updated = priceUpdateService.updatePricesForUser("testuser");

        assertEquals(1, updated);
        verify(userCardRepository).save(any(UserCard.class));
    }

    @Test
    void testUpdatePricesForUser_NoChange() {
        UserCard card = new UserCard("testuser", "Lightning Bolt", "MLP", "1", 1, false);
        card.setPrice(10.0);
        card.setPriceUpdatedAt(LocalDate.now().minusDays(1));

        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(card));

        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setSetCode("MLP");
        sfCard.setCollectorNumber("1");
        sfCard.setPriceRegular(10.0);

        when(scryfallService.getAllCards(false)).thenReturn(List.of(sfCard));

        int updated = priceUpdateService.updatePricesForUser("testuser");

        assertEquals(0, updated);
        verify(userCardRepository, never()).save(any());
    }

    @Test
    void testUpdatePricesForUser_FoilPrice() {
        UserCard card = new UserCard("testuser", "Lightning Bolt", "MLP", "1", 1, true);
        card.setPrice(5.0);

        when(userCardRepository.findByUser("testuser")).thenReturn(List.of(card));

        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setSetCode("MLP");
        sfCard.setCollectorNumber("1");
        sfCard.setPriceFoil(15.0);

        when(scryfallService.getAllCards(false)).thenReturn(List.of(sfCard));

        int updated = priceUpdateService.updatePricesForUser("testuser");

        assertEquals(1, updated);
        verify(userCardRepository).save(argThat(c -> c.getPrice() == 15.0));
    }

    @Test
    void runUpdateForAllUsers_aggregatesPerUserCounts() {
        // Two users: Andre has 1 card to update, Victor has 0
        UserCard andreCard = new UserCard("Andre", "Bolt", "SET", "1", 1, false);
        andreCard.setPrice(1.0);
        UserCard victorCard = new UserCard("Victor", "Bolt", "SET", "1", 1, false);
        victorCard.setPrice(5.0); // same as Scryfall → no update

        when(userCardRepository.findAll()).thenReturn(List.of(andreCard, victorCard));
        when(userCardRepository.findByUser("Andre")).thenReturn(List.of(andreCard));
        when(userCardRepository.findByUser("Victor")).thenReturn(List.of(victorCard));

        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setSetCode("SET");
        sfCard.setCollectorNumber("1");
        sfCard.setPriceRegular(5.0);

        when(scryfallService.getAllCards(false)).thenReturn(List.of(sfCard));

        Map<String, Integer> result = priceUpdateService.runUpdateForAllUsers();

        assertEquals(2, result.size());
        assertTrue(result.containsKey("Andre"));
        assertTrue(result.containsKey("Victor"));
        assertEquals(1, result.get("Andre"));  // price changed 1.0 → 5.0
        assertEquals(0, result.get("Victor")); // price unchanged
    }
}
