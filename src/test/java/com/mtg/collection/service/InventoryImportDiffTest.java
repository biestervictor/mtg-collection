package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryImportDiffTest {

    @Mock private UserCardRepository userCardRepository;
    @Mock private ScryfallCardRepository scryfallCardRepository;
    @Mock private ScryfallService scryfallService;
    @Mock private ImportHistoryRepository importHistoryRepository;

    private InventoryImportService importService;

    @BeforeEach
    void setUp() {
        importService = new InventoryImportService(
                userCardRepository, scryfallCardRepository,
                scryfallService, importHistoryRepository);
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
    }

    // --- helpers ---

    private MockMultipartFile csvFile(String classpathResource) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            assertNotNull(is, "Test resource not found: " + classpathResource);
            return new MockMultipartFile("file", classpathResource, "text/csv", is.readAllBytes());
        }
    }

    private UserCard existingCard(String name, String setCode, String number, boolean foil, int qty) {
        UserCard c = new UserCard("Victor", name, setCode, number, qty, foil);
        return c;
    }

    // --- fresh import (no prior collection) ---

    @Test
    void freshImport_noExistingCards_allAreAdded() throws IOException {
        when(userCardRepository.findByUser("Victor")).thenReturn(Collections.emptyList());

        ImportResult result = importService.importInventory("Victor", csvFile("inventory-base.csv"));

        // All 10 cards are new → addedCardsCount == 10, removedCardsCount == 0
        assertEquals(10, result.getAddedCardsCount(), "All cards should be added on fresh import");
        assertEquals(0,  result.getRemovedCardsCount());
        assertFalse(result.getAddedCards().isEmpty());
        assertTrue(result.getRemovedCards().isEmpty());
    }

    // --- import with one new card (plus1) ---

    @Test
    void importPlus1_detectsOneNewCard() throws IOException {
        // Existing collection = base (10 cards)
        List<UserCard> existing = List.of(
            existingCard("Lightning Bolt",  "dmu", "142", false, 2),
            existingCard("Counterspell",    "dmu", "56",  true,  1),
            existingCard("Serra Angel",     "neo", "25",  false, 3),
            existingCard("Dark Ritual",     "mh2", "83",  true,  1),
            existingCard("Giant Growth",    "znr", "170", false, 2),
            existingCard("Llanowar Elves",  "bro", "175", false, 1),
            existingCard("Opt",             "snc", "56",  true,  1),
            existingCard("Shock",           "vow", "159", false, 4),
            existingCard("Divination",      "mid", "48",  true,  1),
            existingCard("Naturalize",      "afr", "186", false, 2)
        );
        when(userCardRepository.findByUser("Victor")).thenReturn(existing);

        // Import the plus1 file (base + Witch's Oven WOE/335/Foil)
        ImportResult result = importService.importInventory("Victor", csvFile("inventory-plus1.csv"));

        assertEquals(1, result.getAddedCardsCount(),   "Exactly one card should be detected as added");
        assertEquals(0, result.getRemovedCardsCount(), "No card should be removed");

        ImportedCardInfo added = result.getAddedCards().get(0);
        assertEquals("Witch's Oven", added.getName());
        assertEquals("woe", added.getSetCode());
        assertEquals("335", added.getCollectorNumber());
        assertTrue(added.isFoil());
    }

    // --- import with one card missing (minus1) ---

    @Test
    void importMinus1_detectsOneRemovedCard() throws IOException {
        // Existing collection = base (10 cards, including Divination MID/48/Foil)
        List<UserCard> existing = List.of(
            existingCard("Lightning Bolt",  "dmu", "142", false, 2),
            existingCard("Counterspell",    "dmu", "56",  true,  1),
            existingCard("Serra Angel",     "neo", "25",  false, 3),
            existingCard("Dark Ritual",     "mh2", "83",  true,  1),
            existingCard("Giant Growth",    "znr", "170", false, 2),
            existingCard("Llanowar Elves",  "bro", "175", false, 1),
            existingCard("Opt",             "snc", "56",  true,  1),
            existingCard("Shock",           "vow", "159", false, 4),
            existingCard("Divination",      "mid", "48",  true,  1),
            existingCard("Naturalize",      "afr", "186", false, 2)
        );
        when(userCardRepository.findByUser("Victor")).thenReturn(existing);

        // Import minus1 file (base without Divination)
        ImportResult result = importService.importInventory("Victor", csvFile("inventory-minus1.csv"));

        assertEquals(0, result.getAddedCardsCount(),   "No card should be added");
        assertEquals(1, result.getRemovedCardsCount(), "Exactly one card should be detected as removed");

        ImportedCardInfo removed = result.getRemovedCards().get(0);
        assertEquals("Divination", removed.getName());
        assertEquals("mid", removed.getSetCode());
        assertEquals("48",  removed.getCollectorNumber());
        assertTrue(removed.isFoil());
    }

    // --- foil vs. normal are treated as distinct entries ---

    @Test
    void foilAndNormalSameCard_treatedAsDistinct() throws IOException {
        // Only the normal Counterspell in DB, but CSV has the foil version
        List<UserCard> existing = List.of(
            existingCard("Counterspell", "dmu", "56", false, 1) // normal
        );
        when(userCardRepository.findByUser("Victor")).thenReturn(existing);

        String csv = """
                "sep=,"
                Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
                Sets,1,0,"Counterspell",DMU,Dominaria United,56,NearMint,Foil,English
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", csv.getBytes());

        ImportResult result = importService.importInventory("Victor", file);

        // Foil Counterspell is new → added; normal Counterspell is gone → removed
        assertEquals(1, result.getAddedCardsCount(),   "Foil variant should be detected as added");
        assertEquals(1, result.getRemovedCardsCount(), "Normal variant should be detected as removed");
    }

    // --- no change when same collection is imported again ---

    @Test
    void reimportSameCollection_noChanges() throws IOException {
        List<UserCard> existing = List.of(
            existingCard("Lightning Bolt", "dmu", "142", false, 2),
            existingCard("Counterspell",   "dmu", "56",  true,  1)
        );
        when(userCardRepository.findByUser("Victor")).thenReturn(existing);

        String csv = """
                "sep=,"
                Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
                Sets,2,0,"Lightning Bolt",DMU,Dominaria United,142,NearMint,Normal,English
                Sets,1,0,"Counterspell",DMU,Dominaria United,56,NearMint,Foil,English
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", csv.getBytes());

        ImportResult result = importService.importInventory("Victor", file);

        assertEquals(0, result.getAddedCardsCount(),   "No new cards on re-import of same collection");
        assertEquals(0, result.getRemovedCardsCount(), "No removed cards on re-import of same collection");
    }

    // --- multiple adds and removes in one import ---

    @Test
    void importWithMultipleChanges_detectsAllDifferences() throws IOException {
        List<UserCard> existing = List.of(
            existingCard("Lightning Bolt", "dmu", "142", false, 2), // stays
            existingCard("Old Card A",     "eld", "100", false, 1), // will be removed
            existingCard("Old Card B",     "m20", "200", true,  1)  // will be removed
        );
        when(userCardRepository.findByUser("Victor")).thenReturn(existing);

        String csv = """
                "sep=,"
                Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
                Sets,2,0,"Lightning Bolt",DMU,Dominaria United,142,NearMint,Normal,English
                Sets,1,0,"New Card X",NEO,Kamigawa,55,NearMint,Normal,English
                Sets,1,0,"New Card Y",MH2,Modern Horizons 2,77,NearMint,Foil,English
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", csv.getBytes());

        ImportResult result = importService.importInventory("Victor", file);

        assertEquals(2, result.getAddedCardsCount(),   "Two new cards should be detected");
        assertEquals(2, result.getRemovedCardsCount(), "Two removed cards should be detected");
    }
}
