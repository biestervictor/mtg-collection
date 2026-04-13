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
import java.util.List;

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

        priceUpdateService.updatePricesForUser("testuser");

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

        priceUpdateService.updatePricesForUser("testuser");

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

        priceUpdateService.updatePricesForUser("testuser");

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

        priceUpdateService.updatePricesForUser("testuser");

        verify(userCardRepository).save(argThat(c -> c.getPrice() == 15.0));
    }
}