package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.dto.ImportResult.DuplicateInfo;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryImportServiceTest {

    @Mock
    private UserCardRepository userCardRepository;
    
    @Mock
    private ScryfallCardRepository scryfallCardRepository;
    
    @Mock
    private ScryfallService scryfallService;
    
    @Mock
    private ImportHistoryRepository importHistoryRepository;

    @Mock
    private UserDeckService userDeckService;
    
    private InventoryImportService importService;

    @BeforeEach
    void setUp() {
        importService = new InventoryImportService(userCardRepository, scryfallCardRepository, scryfallService, importHistoryRepository, userDeckService);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "inventory.csv", "text/csv", content.getBytes());
    }

    private void stubEmptyScryfall() {
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
    }

    // ── existing tests ────────────────────────────────────────────────────────

    @Test
    void testImportCsvParsesFoilCorrectly() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Foil,English
            TLA,2,0,"Test Card 2",TLA,Avatar,2,NearMint,Normal,English
            TLA,3,0,"Test Card 3",TLA,Avatar,3,NearMint,Foil,English
            """;
        stubEmptyScryfall();
        importService.importInventory("Andre", csv(csvContent));
        verify(userCardRepository).saveAll(argThat(cards -> {
            List<UserCard> cardList = (List<UserCard>) cards;
            long foilCount = cardList.stream().filter(UserCard::isFoil).count();
            long normalCount = cardList.stream().filter(c -> !c.isFoil()).count();
            return cardList.size() == 3 && foilCount == 2 && normalCount == 1;
        }));
    }

    @Test
    void testImportAggregatesDuplicateCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Foil,English
            TLA,2,0,"Test Card",TLA,Avatar,1,NearMint,Foil,English
            TLA,3,0,"Test Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        stubEmptyScryfall();
        importService.importInventory("Andre", csv(csvContent));
        verify(userCardRepository).saveAll(argThat(cards -> {
            List<UserCard> cardList = (List<UserCard>) cards;
            UserCard foilCard = cardList.stream()
                .filter(c -> c.isFoil() && c.getCollectorNumber().equals("1"))
                .findFirst().orElse(null);
            UserCard normalCard = cardList.stream()
                .filter(c -> !c.isFoil() && c.getCollectorNumber().equals("1"))
                .findFirst().orElse(null);
            return foilCard != null && foilCard.getQuantity() == 3 &&
                   normalCard != null && normalCard.getQuantity() == 3;
        }));
    }

    @Test
    void testImportReturnsCorrectCardCount() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,5,0,"Card 1",TLA,Avatar,1,NearMint,Normal,English
            TLA,3,0,"Card 2",TLA,Avatar,2,NearMint,Foil,English
            """;
        stubEmptyScryfall();
        ImportResult result = importService.importInventory("Andre", csv(csvContent));
        assertEquals(8, result.getCardsCount()); // 5 + 3 = 8
        assertEquals(2, result.getNewCardsCount()); // 2 unique cards
    }

    @Test
    void testImportRecognizesAllFoilVariants() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Surge Card",TLA,Avatar,1,NearMint,Surge Foil,English
            TLA,1,0,"Galaxy Card",TLA,Avatar,2,NearMint,Galaxy Foil,English
            TLA,1,0,"Gilded Card",TLA,Avatar,3,NearMint,Gilded Foil,English
            TLA,1,0,"Normal Card",TLA,Avatar,4,NearMint,Normal,English
            """;
        stubEmptyScryfall();
        importService.importInventory("Victor", csv(csvContent));
        verify(userCardRepository).saveAll(argThat(cards -> {
            List<UserCard> cardList = (List<UserCard>) cards;
            long foilCount   = cardList.stream().filter(UserCard::isFoil).count();
            long normalCount = cardList.stream().filter(c -> !c.isFoil()).count();
            return foilCount == 3 && normalCount == 1;
        }));
    }

    @Test
    void testImportWithScryfallCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setCollectorNumber("1");
        sfCard.setSetCode("tla");
        sfCard.setName("Test Card");
        sfCard.setThumbnailFront("http://example.com/card.jpg");
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Arrays.asList(sfCard));
        when(scryfallCardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(sfCard));
        ImportResult result = importService.importInventory("Andre", csv(csvContent));
        assertNotNull(result.getNewCards());
        assertEquals(1, result.getNewCards().size());
        assertEquals(1, result.getNewCards().get(0).getQuantity());
        assertEquals(0, result.getNewCards().get(0).getFoilQuantity());
    }

    // ── duplicate detection tests ─────────────────────────────────────────────
    // These cover the data flow that populates the warning block shown after
    // the import badge "1" is clicked in the user menu.

    @Test
    void exactDuplicateLinesAreDetectedAndReported() throws Exception {
        // Row 3 is a byte-for-byte repeat of row 1 → should be detected as duplicate
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,2,0,"Counterspell",mmq,Mercadian Masques,61,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertEquals(1, result.duplicates.size(), "one distinct duplicate row expected");
        DuplicateInfo dup = result.duplicates.get(0);
        assertEquals("Lightning Bolt", dup.getCardName());
        assertEquals("m10",            dup.getSetCode());
        assertEquals(1,                dup.getOccurrences(), "one extra copy removed");
    }

    @Test
    void triplicateLinesCountTwoOccurrences() throws Exception {
        // Same row appearing 3× → occurrences = 2 (two extras removed, one kept)
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertEquals(1, result.duplicates.size());
        assertEquals(2, result.duplicates.get(0).getOccurrences());
    }

    @Test
    void distinctRowsWithSameCardAreNotDuplicates() throws Exception {
        // Same card but different purchase price or date → different raw line → NOT a duplicate
        // (These are legitimate multi-purchase rows and get qty-summed by folder-aware dedup)
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,2,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertTrue(result.duplicates.isEmpty(), "different qty → different raw line → not a duplicate");
        // folder-aware dedup sums them: 1 unique card with qty=3
        assertEquals(1, result.cards.size());
        assertEquals(3, result.cards.get(0).getQuantity());
    }

    @Test
    void noDuplicatesInCleanFile() throws Exception {
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Card A",m10,Magic 2010,1,NearMint,Normal,English
            Deck,1,0,"Card B",m10,Magic 2010,2,NearMint,Normal,English
            Deck,1,0,"Card C",m10,Magic 2010,3,NearMint,Foil,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertTrue(result.duplicates.isEmpty());
        assertEquals(3, result.cards.size());
    }

    @Test
    void multipleDuplicateGroupsAreAllReported() throws Exception {
        // Two distinct rows each appearing twice → two DuplicateInfo entries
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Card A",m10,Magic 2010,1,NearMint,Normal,English
            Deck,1,0,"Card A",m10,Magic 2010,1,NearMint,Normal,English
            Deck,1,0,"Card B",m10,Magic 2010,2,NearMint,Normal,English
            Deck,1,0,"Card B",m10,Magic 2010,2,NearMint,Normal,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertEquals(2, result.duplicates.size());
        assertEquals(2, result.cards.size(), "only two unique cards kept");
    }

    @Test
    void duplicateLinesDoNotInflateCardQuantity() throws Exception {
        // qty=1 row appearing 3× must NOT result in qty=3 — it stays qty=1
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            """;
        InventoryImportService.ParseResult result = importService.parseInventoryFile(
                new MockMultipartFile("file", "inv.csv", "text/csv", csv.getBytes()));
        assertEquals(1, result.cards.size());
        assertEquals(1, result.cards.get(0).getQuantity(),
                "exact duplicates must not add to quantity");
    }

    @Test
    void importResultContainsDuplicateWarnings() {
        // End-to-end: importInventory populates ImportResult.duplicatesRemoved
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            Deck,1,0,"Lightning Bolt",m10,Magic 2010,35,NearMint,Normal,English
            """;
        stubEmptyScryfall();
        ImportResult result = importService.importInventory("Victor", csv(csv));
        assertNotNull(result.getDuplicatesRemoved());
        assertEquals(1, result.getDuplicatesRemoved().size());
        assertEquals("Lightning Bolt", result.getDuplicatesRemoved().get(0).getCardName());
    }

    @Test
    void importResultContainsUnknownSetCodeWarnings() {
        // End-to-end: set codes with no Scryfall data appear in unknownSetCodes
        String csv = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            Deck,1,0,"Mystery Card",xyz,Unknown Set,1,NearMint,Normal,English
            """;
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode("xyz")).thenReturn(Collections.emptyList());
        ImportResult result = importService.importInventory("Victor", csv(csv));
        assertNotNull(result.getUnknownSetCodes());
        assertFalse(result.getUnknownSetCodes().isEmpty());
        assertEquals("xyz", result.getUnknownSetCodes().get(0).getSetCode());
        assertTrue(result.getUnknownSetCodes().get(0).getCardNames().contains("Mystery Card"));
    }
}
