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
import static org.mockito.ArgumentMatchers.eq;
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

    // ── Cache / basic behaviour ───────────────────────────────────────────────

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
    void testGetCardsBySetReturnsEmptyListWhenNoCacheAndApiFails() {
        when(cardRepository.findBySetCode("unknown")).thenReturn(Collections.emptyList());

        List<ScryfallCard> result = scryfallService.getCardsBySet("unknown", null);

        assertTrue(result.isEmpty());
    }

    // ── Image extraction – regular card ──────────────────────────────────────

    @Test
    void testFetchCardExtractsImageUrisFromRegularCard() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Lightning Bolt",
                    "collector_number": "150",
                    "rarity": "common",
                    "image_uris": {
                      "small":  "http://img.example.com/small.jpg",
                      "normal": "http://img.example.com/normal.jpg"
                    }
                  }],
                  "has_more": false
                }
                """;

        when(cardRepository.findBySetCode("m10")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.findBySetCode("m10")).thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList()); // first call empty → fetch; result from saveAll

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);

        scryfallService.getCardsBySet("m10", null);

        verify(cardRepository).saveAll(captor.capture());
        ScryfallCard saved = captor.getValue().get(0);

        assertEquals("http://img.example.com/small.jpg",  saved.getThumbnailFront());
        assertEquals("http://img.example.com/normal.jpg", saved.getImageFront());
        assertNull(saved.getThumbnailBack());
        assertNull(saved.getImageBack());
    }

    // ── Image extraction – double-faced card (DFC) ───────────────────────────

    @Test
    void testFetchCardExtractsImageUrisFromDoubleFacedCard() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Delver of Secrets // Insectile Aberration",
                    "collector_number": "51",
                    "rarity": "common",
                    "card_faces": [
                      {
                        "name": "Delver of Secrets",
                        "type_line": "Creature — Human Wizard",
                        "image_uris": {
                          "small":  "http://img.example.com/front-small.jpg",
                          "normal": "http://img.example.com/front-normal.jpg"
                        }
                      },
                      {
                        "name": "Insectile Aberration",
                        "type_line": "Creature — Human Insect",
                        "image_uris": {
                          "small":  "http://img.example.com/back-small.jpg",
                          "normal": "http://img.example.com/back-normal.jpg"
                        }
                      }
                    ]
                  }],
                  "has_more": false
                }
                """;

        when(cardRepository.findBySetCode("isd")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);

        scryfallService.getCardsBySet("isd", null);

        verify(cardRepository).saveAll(captor.capture());
        ScryfallCard saved = captor.getValue().get(0);

        assertEquals("http://img.example.com/front-small.jpg",  saved.getThumbnailFront());
        assertEquals("http://img.example.com/front-normal.jpg", saved.getImageFront());
        assertEquals("http://img.example.com/back-small.jpg",   saved.getThumbnailBack());
        assertEquals("http://img.example.com/back-normal.jpg",  saved.getImageBack());
    }

    // ── Image update on existing card ─────────────────────────────────────────

    @Test
    void testFetchAndSaveUpdatesImageFieldsOnExistingCard() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Lightning Bolt",
                    "collector_number": "150",
                    "rarity": "common",
                    "image_uris": {
                      "small":  "http://img.example.com/new-small.jpg",
                      "normal": "http://img.example.com/new-normal.jpg"
                    }
                  }],
                  "has_more": false
                }
                """;

        ScryfallCard existingCard = new ScryfallCard();
        existingCard.setSetCode("m10");
        existingCard.setCollectorNumber("150");
        existingCard.setName("Lightning Bolt");
        existingCard.setThumbnailFront("http://img.example.com/old-small.jpg");
        existingCard.setImageFront("http://img.example.com/old-normal.jpg");

        // First call: empty cache → triggers fetch. Second call (after saveAll): returns updated card.
        when(cardRepository.findBySetCode("m10"))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList()) // inside fetchAndSaveCardsFromApi (existingCards lookup)
                .thenReturn(Arrays.asList(existingCard));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);

        scryfallService.getCardsBySet("m10", null);

        verify(cardRepository).saveAll(captor.capture());
        ScryfallCard saved = captor.getValue().get(0);

        assertEquals("http://img.example.com/new-small.jpg",  saved.getThumbnailFront());
        assertEquals("http://img.example.com/new-normal.jpg", saved.getImageFront());
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

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
}
