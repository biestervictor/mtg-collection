package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ImportHistoryServiceTest {

    @Mock
    private UserCardRepository userCardRepository;
    
    @Mock
    private ScryfallCardRepository scryfallCardRepository;
    
    @Mock
    private ScryfallService scryfallService;
    
    @Mock
    private ImportHistoryRepository importHistoryRepository;
    
    private InventoryImportService importService;

    @BeforeEach
    void setUp() {
        importService = new InventoryImportService(userCardRepository, scryfallCardRepository, scryfallService, importHistoryRepository);
    }

    @Test
    void testImportCreatesHistoryRecord() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        ArgumentCaptor<ImportHistory> historyCaptor = ArgumentCaptor.forClass(ImportHistory.class);
        verify(importHistoryRepository).save(historyCaptor.capture());
        
        ImportHistory savedHistory = historyCaptor.getValue();
        assertEquals("Andre", savedHistory.getUser());
        assertEquals("inventory", savedHistory.getFormat());
        assertEquals(1, savedHistory.getTotalCardsCount());
    }

    @Test
    void testImportTracksAddedCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"New Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(1, result.getAddedCardsCount());
        assertNotNull(result.getAddedCards());
        assertEquals(1, result.getAddedCards().size());
        assertEquals("New Card", result.getAddedCards().get(0).getName());
    }

    @Test
    void testImportTracksRemovedCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            xyz,1,0,"Remaining Card",xyz,SomeSet,99,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Removed Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setQuantity(1);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(1, result.getRemovedCardsCount());
        assertNotNull(result.getRemovedCards());
        assertEquals("Removed Card", result.getRemovedCards().get(0).getName());
    }

    @Test
    void testImportTracksBothAddedAndRemovedCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"New Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Old Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("2");
        existingCard.setQuantity(1);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(1, result.getAddedCardsCount());
        assertEquals(1, result.getRemovedCardsCount());
    }

    @Test
    void testImportNoChangesWhenSameCards() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Same Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Same Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setQuantity(1);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(0, result.getAddedCardsCount());
        assertEquals(0, result.getRemovedCardsCount());
    }

    @Test
    void testQuantityChangeDoesNotTriggerAddedOrRemoved() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,5,0,"Same Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Same Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setQuantity(1);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(0, result.getAddedCardsCount());
        assertEquals(0, result.getRemovedCardsCount());
        assertEquals(5, result.getCardsCount());
    }

    @Test
    void testRemovedCardMeansNotInImportFile() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            xyz,1,0,"Different Card",xyz,SomeSet,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Existing Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setQuantity(2);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(1, result.getRemovedCardsCount());
        assertEquals(1, result.getAddedCardsCount());
        assertEquals("Existing Card", result.getRemovedCards().get(0).getName());
        assertEquals("Different Card", result.getAddedCards().get(0).getName());
    }

    @Test
    void testNewCardMeansNotInCollection() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,2,0,"Brand New Card",TLA,Avatar,5,NearMint,Normal,English
            TLA,1,0,"Existing Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        UserCard existingCard = new UserCard();
        existingCard.setUser("Andre");
        existingCard.setName("Existing Card");
        existingCard.setSetCode("tla");
        existingCard.setCollectorNumber("1");
        existingCard.setQuantity(1);
        existingCard.setFoil(false);
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Arrays.asList(existingCard));
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(1, result.getAddedCardsCount());
        assertEquals(0, result.getRemovedCardsCount());
        assertEquals("Brand New Card", result.getAddedCards().get(0).getName());
    }

    @Test
    void testCardsCountReflectsTotalQuantityNotUnique() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,5,0,"Card 1",TLA,Avatar,1,NearMint,Normal,English
            TLA,3,0,"Card 2",TLA,Avatar,2,NearMint,Normal,English
            TLA,2,0,"Card 3",TLA,Avatar,3,NearMint,Foil,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(10, result.getCardsCount());
        assertEquals(3, result.getNewCardsCount());
    }

    @Test
    void testImportTracksFoilCardsCorrectly() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,2,0,"Foil Card",TLA,Avatar,1,NearMint,Foil,English
            TLA,3,0,"Normal Card",TLA,Avatar,2,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(2, result.getAddedCardsCount());
        
        ImportedCardInfo foilCard = result.getAddedCards().stream()
            .filter(c -> c.isFoil())
            .findFirst()
            .orElse(null);
        ImportedCardInfo normalCard = result.getAddedCards().stream()
            .filter(c -> !c.isFoil())
            .findFirst()
            .orElse(null);
        
        assertNotNull(foilCard);
        assertNotNull(normalCard);
        assertEquals(2, foilCard.getQuantity());
        assertEquals(3, normalCard.getQuantity());
    }

    @Test
    void testImportSavesHistoryWithCorrectFormat() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Normal,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(userCardRepository.findByUser("Andre")).thenReturn(Collections.emptyList());
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        importService.importInventory("Andre", file);
        
        ArgumentCaptor<ImportHistory> historyCaptor = ArgumentCaptor.forClass(ImportHistory.class);
        verify(importHistoryRepository).save(historyCaptor.capture());
        
        ImportHistory history = historyCaptor.getValue();
        assertEquals("inventory", history.getFormat());
        assertNotNull(history.getImportedAt());
    }
}
