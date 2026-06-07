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
import java.util.Set;

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

    // ── getAllSets – force refresh ─────────────────────────────────────────────

    @Test
    void getAllSets_forceRefresh_fetchesFromApiAndPersists() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Test Alpha",
                    "code": "tla",
                    "set_type": "core",
                    "digital": false,
                    "card_count": 10,
                    "released_at": "1993-08-05",
                    "icon_svg_uri": "http://icons/tla.svg"
                  }]
                }
                """;
        when(setRepository.findAll()).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(setRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScryfallSet> result = scryfallService.getAllSets(true);

        verify(setRepository).deleteAll();
        verify(setRepository).saveAll(any());
        assertEquals(1, result.size());
        assertEquals("tla", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_digitalSetExcluded() throws Exception {
        String apiResponse = """
                {
                  "data": [
                    {"name":"Digital Only","code":"do1","set_type":"core","digital":true,"card_count":5},
                    {"name":"Physical","code":"phy","set_type":"core","digital":false,"card_count":10}
                  ]
                }
                """;
        when(setRepository.findAll()).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(setRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("phy", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_excludedSetTypeFiltered() throws Exception {
        String apiResponse = """
                {
                  "data": [
                    {"name":"Alchemy Set","code":"alc","set_type":"alchemy","digital":false,"card_count":10},
                    {"name":"Normal","code":"nrm","set_type":"expansion","digital":false,"card_count":20}
                  ]
                }
                """;
        when(setRepository.findAll()).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(setRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("nrm", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_zeroCardCount_excluded() throws Exception {
        String apiResponse = """
                {
                  "data": [
                    {"name":"Empty","code":"emp","set_type":"expansion","digital":false,"card_count":0},
                    {"name":"HasCards","code":"hsc","set_type":"expansion","digital":false,"card_count":5}
                  ]
                }
                """;
        when(setRepository.findAll()).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(setRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("hsc", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_tokenAndPromoSetsFiltered() {
        ScryfallSet token = new ScryfallSet();
        token.setSetCode("ttla"); token.setSetType("token");
        ScryfallSet promo = new ScryfallSet();
        promo.setSetCode("ptla"); promo.setSetType("promo");
        ScryfallSet regular = new ScryfallSet();
        regular.setSetCode("tla"); regular.setSetType("expansion");

        when(setRepository.findAll()).thenReturn(List.of(token, promo, regular));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("tla", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_tokenWithoutSetType_filteredByCodeConvention() {
        // Bug-Regression: alte DB-Eintraege haben setType=null. Der Filter muss
        // trotzdem greifen, wenn der Code-Konvention "t" + Haupt-Code folgt.
        ScryfallSet token = new ScryfallSet();
        token.setSetCode("ttdm");   // Token zu "tdm" — setType absichtlich null
        ScryfallSet main = new ScryfallSet();
        main.setSetCode("tdm"); main.setSetType("expansion");

        when(setRepository.findAll()).thenReturn(List.of(token, main));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("tdm", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_promoWithoutSetType_filteredByCodeConvention() {
        ScryfallSet promo = new ScryfallSet();
        promo.setSetCode("pdmu");   // Promo zu "dmu" — setType null
        ScryfallSet main = new ScryfallSet();
        main.setSetCode("dmu"); main.setSetType("expansion");

        when(setRepository.findAll()).thenReturn(List.of(promo, main));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("dmu", result.get(0).getSetCode());
    }

    @Test
    void getAllSets_legitimateSetsStartingWithTorP_notFiltered() {
        // Reale Sets die mit "t" oder "p" beginnen duerfen NICHT ausgefiltert werden.
        // Test: "tor" (Torment), "tsp" (Time Spiral), "pls" (Planeshift)
        ScryfallSet tor = new ScryfallSet(); tor.setSetCode("tor"); tor.setSetType("expansion");
        ScryfallSet tsp = new ScryfallSet(); tsp.setSetCode("tsp"); tsp.setSetType("expansion");
        ScryfallSet pls = new ScryfallSet(); pls.setSetCode("pls"); pls.setSetType("expansion");

        when(setRepository.findAll()).thenReturn(List.of(tor, tsp, pls));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(3, result.size());
    }

    @Test
    void isTokenOrPromoByCodeConvention_helperLogic() {
        Set<String> codes = Set.of("tdm", "dmu", "tor", "tsp");

        // Token: "t" + bekannter Code
        assertTrue(ScryfallService.isTokenOrPromoByCodeConvention("ttdm", codes));
        // Promo: "p" + bekannter Code
        assertTrue(ScryfallService.isTokenOrPromoByCodeConvention("pdmu", codes));
        // Legit: "t" + unbekannter Rest
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention("tor", codes));
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention("tsp", codes));
        // Edge cases
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention(null, codes));
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention("", codes));
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention("a", codes));
        // Code beginnt nicht mit t/p
        assertFalse(ScryfallService.isTokenOrPromoByCodeConvention("xdm", codes));
    }

    @Test
    void getAllSets_apiFails_returnsCachedSets() {
        ScryfallSet cached = new ScryfallSet();
        cached.setSetCode("tla");
        when(setRepository.findAll()).thenReturn(List.of(cached));
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(new RuntimeException("API down"));

        // forceRefresh=true but API fails → should fall through to cached
        List<ScryfallSet> result = scryfallService.getAllSets(true);

        // After API failure, falls back to cached sets filtered for non-digital
        assertFalse(result.isEmpty());
    }

    @Test
    void getAllSets_cachedDigitalSetsFiltered() {
        ScryfallSet digital = new ScryfallSet();
        digital.setSetCode("dig"); digital.setDigital(true);
        ScryfallSet physical = new ScryfallSet();
        physical.setSetCode("phy"); physical.setDigital(false);
        when(setRepository.findAll()).thenReturn(List.of(digital, physical));

        List<ScryfallSet> result = scryfallService.getAllSets(false);

        assertEquals(1, result.size());
        assertEquals("phy", result.get(0).getSetCode());
    }

    // ── mapCardFromJson – field mapping branches ──────────────────────────────

    @Test
    void getCardsBySet_frameEffectsMapped() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Showcase Card",
                    "collector_number": "300",
                    "rarity": "rare",
                    "frame_effects": ["showcase"],
                    "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                  }],
                  "has_more": false
                }
                """;
        when(cardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);
        scryfallService.getCardsBySet("tst", null);
        verify(cardRepository).saveAll(captor.capture());

        assertEquals("showcase", captor.getValue().get(0).getFrameStatus());
    }

    @Test
    void getCardsBySet_borderColorAndFullArtMapped() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Full Art Land",
                    "collector_number": "250",
                    "rarity": "common",
                    "border_color": "borderless",
                    "full_art": true,
                    "frame": "2015",
                    "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                  }],
                  "has_more": false
                }
                """;
        when(cardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);
        scryfallService.getCardsBySet("tst", null);
        verify(cardRepository).saveAll(captor.capture());

        ScryfallCard saved = captor.getValue().get(0);
        assertEquals("borderless", saved.getBorderColor());
        assertTrue(saved.isFullArt());
        assertEquals("2015", saved.getFrame());
    }

    @Test
    void getCardsBySet_pricesMapped() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Pricey Card",
                    "collector_number": "1",
                    "rarity": "mythic",
                    "prices": {"eur": "12.50", "eur_foil": "45.00"},
                    "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                  }],
                  "has_more": false
                }
                """;
        when(cardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);
        scryfallService.getCardsBySet("tst", null);
        verify(cardRepository).saveAll(captor.capture());

        ScryfallCard saved = captor.getValue().get(0);
        assertEquals(12.50, saved.getPriceRegular(), 0.001);
        assertEquals(45.00, saved.getPriceFoil(),    0.001);
    }

    @Test
    void getCardsBySet_purchaseLinkMapped() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "Buy Me",
                    "collector_number": "1",
                    "rarity": "rare",
                    "purchase_uris": {"cardmarket": "https://cardmarket.com/card"},
                    "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                  }],
                  "has_more": false
                }
                """;
        when(cardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);
        scryfallService.getCardsBySet("tst", null);
        verify(cardRepository).saveAll(captor.capture());

        assertEquals("https://cardmarket.com/card", captor.getValue().get(0).getPurchaseLink());
    }

    @Test
    void getCardsBySet_dfcTypeLine_fromFrontFace() throws Exception {
        String apiResponse = """
                {
                  "data": [{
                    "name": "DFC Card",
                    "collector_number": "1",
                    "rarity": "rare",
                    "card_faces": [
                      {"type_line": "Creature — Human", "image_uris": {"small":"http://img/fs.jpg","normal":"http://img/fn.jpg"}},
                      {"type_line": "Creature — Dragon", "image_uris": {"small":"http://img/bs.jpg","normal":"http://img/bn.jpg"}}
                    ]
                  }],
                  "has_more": false
                }
                """;
        when(cardRepository.findBySetCode("tst")).thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScryfallCard>> captor = ArgumentCaptor.forClass(List.class);
        scryfallService.getCardsBySet("tst", null);
        verify(cardRepository).saveAll(captor.capture());

        assertEquals("Creature — Human", captor.getValue().get(0).getTypeLine());
    }

    // ── refreshSingleCard ─────────────────────────────────────────────────────

    @Test
    void refreshSingleCard_cardNotInDb_savesNewCard() throws Exception {
        String apiResponse = """
                {
                  "name": "New Card",
                  "collector_number": "42",
                  "rarity": "uncommon",
                  "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                }
                """;
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.findBySetCodeAndCollectorNumber("tst", "42")).thenReturn(Collections.emptyList());
        ScryfallCard saved = new ScryfallCard();
        saved.setName("New Card");
        when(cardRepository.save(any())).thenReturn(saved);

        ScryfallCard result = scryfallService.refreshSingleCard("TST", "42");

        assertNotNull(result);
        verify(cardRepository).save(any());
    }

    @Test
    void refreshSingleCard_cardExistsInDb_updatesAndSaves() throws Exception {
        String apiResponse = """
                {
                  "name": "Existing",
                  "collector_number": "1",
                  "rarity": "rare",
                  "prices": {"eur": "5.00", "eur_foil": "20.00"},
                  "image_uris": {"small": "http://img/new-s.jpg", "normal": "http://img/new-n.jpg"}
                }
                """;
        ScryfallCard existing = new ScryfallCard();
        existing.setSetCode("tst"); existing.setCollectorNumber("1");
        existing.setPriceRegular(3.0); existing.setPriceFoil(10.0);

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.findBySetCodeAndCollectorNumber("tst", "1")).thenReturn(List.of(existing));
        when(cardRepository.save(any())).thenReturn(existing);

        scryfallService.refreshSingleCard("TST", "1");

        assertEquals(5.0,  existing.getPriceRegular(), 0.001);
        assertEquals(20.0, existing.getPriceFoil(),    0.001);
        verify(cardRepository).save(existing);
    }

    @Test
    void refreshSingleCard_apiReturnsError_returnsNull() throws Exception {
        String errorResponse = """
                {"object": "error", "details": "Card not found"}
                """;
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(errorResponse);

        ScryfallCard result = scryfallService.refreshSingleCard("TST", "999");

        assertNull(result);
        verify(cardRepository, never()).save(any());
    }

    @Test
    void refreshSingleCard_apiThrows_returnsNull() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        ScryfallCard result = scryfallService.refreshSingleCard("TST", "1");

        assertNull(result);
    }

    @Test
    void refreshSingleCard_duplicateDocs_deletesExtras() throws Exception {
        String apiResponse = """
                {
                  "name": "Dupe Card", "collector_number": "1", "rarity": "common",
                  "image_uris": {"small": "http://img/s.jpg", "normal": "http://img/n.jpg"}
                }
                """;
        ScryfallCard first  = new ScryfallCard(); first.setSetCode("tst");  first.setCollectorNumber("1");
        ScryfallCard second = new ScryfallCard(); second.setSetCode("tst"); second.setCollectorNumber("1");

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(apiResponse);
        when(cardRepository.findBySetCodeAndCollectorNumber("tst", "1")).thenReturn(List.of(first, second));
        when(cardRepository.save(any())).thenReturn(first);

        scryfallService.refreshSingleCard("TST", "1");

        verify(cardRepository).deleteAll(List.of(second));
    }

    // ── getCardsBySetWithoutCache ─────────────────────────────────────────────

    @Test
    void getCardsBySetWithoutCache_apiFails_returnsEmpty() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        List<ScryfallCard> result = scryfallService.getCardsBySetWithoutCache("tst", null);

        assertTrue(result.isEmpty());
    }
}

