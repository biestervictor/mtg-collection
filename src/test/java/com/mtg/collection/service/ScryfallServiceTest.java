package com.mtg.collection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.ScryfallSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScryfallServiceTest {

    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private ScryfallCardRepository cardRepository;
    
    @Mock
    private ScryfallSetRepository setRepository;
    
    private ObjectMapper objectMapper;
    private ScryfallService scryfallService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        scryfallService = new ScryfallService(restTemplate, objectMapper, cardRepository, setRepository);
    }

    @Test
    void testGetAllSetsReturnsCachedSets() {
        ScryfallSet cachedSet = new ScryfallSet();
        cachedSet.setSetCode("tla");
        cachedSet.setName("Test Set");
        
        when(setRepository.findAll()).thenReturn(Arrays.asList(cachedSet));
        
        List<ScryfallSet> result = scryfallService.getAllSets(false);
        
        assertEquals(1, result.size());
        assertEquals("tla", result.get(0).getSetCode());
    }

    @Test
    void testGetCardsBySetReturnsCachedCards() {
        ScryfallCard cachedCard = new ScryfallCard();
        cachedCard.setCollectorNumber("1");
        cachedCard.setName("Cached Card");
        
        when(cardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(cachedCard));
        
        List<ScryfallCard> result = scryfallService.getCardsBySet("tla", null);
        
        assertEquals(1, result.size());
        assertEquals("Cached Card", result.get(0).getName());
    }

    @Test
    void testGetCardsBySetDoesNotDeleteExistingCards() {
        ScryfallCard existingCard = new ScryfallCard();
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setName("Existing Card");
        existingCard.setThumbnailFront("http://example.com/old.jpg");
        
        when(cardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(existingCard));
        
        scryfallService.getCardsBySet("tla", null);
        
        verify(cardRepository, never()).deleteBySetCode(anyString());
    }

    @Test
    void testGetCardsBySetPreservesImages() {
        ScryfallCard existingCard = new ScryfallCard();
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setName("Existing Card");
        existingCard.setThumbnailFront("http://example.com/preserved.jpg");
        
        when(cardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(existingCard));
        
        List<ScryfallCard> result = scryfallService.getCardsBySet("tla", null);
        
        assertEquals("http://example.com/preserved.jpg", result.get(0).getThumbnailFront());
    }

    @Test
    void testGetCardsBySetAddsNewCardsWithoutDeletingExisting() {
        ScryfallCard existingCard = new ScryfallCard();
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setName("Existing Card");
        
        when(cardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(existingCard));
        
        List<ScryfallCard> result = scryfallService.getCardsBySet("tla", null);
        
        assertEquals(1, result.size());
        verify(cardRepository, never()).deleteBySetCode(anyString());
    }

    @Test
    void testClearCacheRemovesCardsForSet() {
        scryfallService.clearCache("tla");
        
        verify(cardRepository).deleteBySetCode("tla");
    }

    @Test
    void testClearCacheWithNullSetCode() {
        scryfallService.clearCache(null);
        
        verify(cardRepository, never()).deleteBySetCode(anyString());
    }

    @Test
    void testClearCacheWithEmptySetCode() {
        scryfallService.clearCache("");
        
        verify(cardRepository, never()).deleteBySetCode(anyString());
    }

    @Test
    void testClearAllCacheRemovesAllCards() {
        scryfallService.clearAllCache();
        
        verify(cardRepository).deleteAll();
    }

    @Test
    void testGetCardsBySetReturnsEmptyListWhenNoCacheAndApiFails() {
        when(cardRepository.findBySetCode("unknown")).thenReturn(Collections.emptyList());
        
        List<ScryfallCard> result = scryfallService.getCardsBySet("unknown", null);
        
        assertTrue(result.isEmpty());
    }
}
