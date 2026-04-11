package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private UserCardRepository userCardRepository;
    
    @Mock
    private ScryfallCardRepository scryfallCardRepository;
    
    @Mock
    private Model model;
    
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        searchController = new SearchController(userCardRepository, scryfallCardRepository);
    }

    @Test
    void testSearchWithEmptyQueryReturnsEmpty() {
        String view = searchController.searchCards(model, "", null);
        
        assertEquals("search", view);
        verify(model, never()).addAttribute(eq("results"), any());
    }

    @Test
    void testSearchWithBlankQueryReturnsEmpty() {
        String view = searchController.searchCards(model, "   ", null);
        
        assertEquals("search", view);
        verify(model, never()).addAttribute(eq("results"), any());
    }

    @Test
    void testSearchFindsCardsByName() {
        UserCard card1 = createUserCard("Test Card", "tla", "1", false, 2);
        UserCard card2 = createUserCard("Test Card", "tla", "1", true, 1);
        
        ScryfallCard sfCard = createScryfallCard("Test Card", "tla", "1");
        
        when(userCardRepository.findByUser("user2")).thenReturn(Arrays.asList(card1, card2));
        when(scryfallCardRepository.findAll()).thenReturn(Arrays.asList(sfCard));
        
        String view = searchController.searchCards(model, "Test Card", null);
        
        assertEquals("search", view);
        verify(model).addAttribute("resultCount", 1);
        verify(model).addAttribute(eq("results"), any());
    }

    @Test
    void testSearchFindsCardsByNumber() {
        UserCard card = createUserCard("Test Card", "tla", "42", false, 1);
        ScryfallCard sfCard = createScryfallCard("Test Card", "tla", "42");
        
        when(userCardRepository.findByUser("user2")).thenReturn(Arrays.asList(card));
        when(scryfallCardRepository.findAll()).thenReturn(Arrays.asList(sfCard));
        
        String view = searchController.searchCards(model, "42", null);
        
        assertEquals("search", view);
        verify(model).addAttribute("resultCount", 1);
    }

    @Test
    void testSearchNoResults() {
        when(userCardRepository.findByUser("user2")).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findAll()).thenReturn(Collections.emptyList());
        
        String view = searchController.searchCards(model, "Nonexistent", null);
        
        assertEquals("search", view);
        verify(model).addAttribute("resultCount", 0);
    }

    @Test
    void testSearchAggregatesDuplicateCards() {
        UserCard card1 = createUserCard("Test Card", "tla", "1", false, 2);
        UserCard card2 = createUserCard("Test Card", "tla", "1", true, 3);
        
        ScryfallCard sfCard = createScryfallCard("Test Card", "tla", "1");
        
        when(userCardRepository.findByUser("user2")).thenReturn(Arrays.asList(card1, card2));
        when(scryfallCardRepository.findAll()).thenReturn(Arrays.asList(sfCard));
        
        String view = searchController.searchCards(model, "Test Card", null);
        
        assertEquals("search", view);
        verify(model).addAttribute(eq("results"), argThat(results -> {
            List<CardWithUserData> list = (List<CardWithUserData>) results;
            return list.size() == 1 && 
                   list.get(0).getQuantity() == 2 && 
                   list.get(0).getFoilQuantity() == 3;
        }));
    }

    @Test
    void testSearchWithSpecificUser() {
        when(userCardRepository.findByUser("user1")).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findAll()).thenReturn(Collections.emptyList());
        
        String view = searchController.searchCards(model, "test", "user1");
        
        assertEquals("search", view);
        verify(userCardRepository).findByUser("user1");
        verify(model).addAttribute("searchUser", "user1");
    }

    @Test
    void testSearchCaseInsensitive() {
        UserCard card = createUserCard("Test Card", "tla", "1", false, 1);
        ScryfallCard sfCard = createScryfallCard("Test Card", "tla", "1");
        
        when(userCardRepository.findByUser("user2")).thenReturn(Arrays.asList(card));
        when(scryfallCardRepository.findAll()).thenReturn(Arrays.asList(sfCard));
        
        String view = searchController.searchCards(model, "TEST CARD", null);
        
        assertEquals("search", view);
        verify(model).addAttribute("resultCount", 1);
    }

    private UserCard createUserCard(String name, String setCode, String collectorNumber, boolean foil, int quantity) {
        UserCard card = new UserCard();
        card.setName(name);
        card.setSetCode(setCode);
        card.setCollectorNumber(collectorNumber);
        card.setFoil(foil);
        card.setQuantity(quantity);
        return card;
    }

    private ScryfallCard createScryfallCard(String name, String setCode, String collectorNumber) {
        ScryfallCard card = new ScryfallCard();
        card.setName(name);
        card.setSetCode(setCode);
        card.setCollectorNumber(collectorNumber);
        card.setThumbnailFront("http://example.com/thumb.jpg");
        card.setImageFront("http://example.com/image.jpg");
        return card;
    }
}
