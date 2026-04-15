package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
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
    
    private InventoryImportService importService;

    @BeforeEach
    void setUp() {
        importService = new InventoryImportService(userCardRepository, scryfallCardRepository, scryfallService, importHistoryRepository);
    }

    @Test
    void testImportCsvParsesFoilCorrectly() {
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Test Card",TLA,Avatar,1,NearMint,Foil,English
            TLA,2,0,"Test Card 2",TLA,Avatar,2,NearMint,Normal,English
            TLA,3,0,"Test Card 3",TLA,Avatar,3,NearMint,Foil,English
            """;
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        verify(userCardRepository).deleteByUser("Andre");
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
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
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
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertEquals(8, result.getCardsCount()); // 5 + 3 = 8
        assertEquals(2, result.getNewCardsCount()); // 2 unique cards
    }

    @Test
    void testImportRecognizesAllFoilVariants() {
        // DragonShield exports many special foil treatments – all must be detected as foil
        String csvContent = """
            sep=,
            Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language
            TLA,1,0,"Surge Card",TLA,Avatar,1,NearMint,Surge Foil,English
            TLA,1,0,"Galaxy Card",TLA,Avatar,2,NearMint,Galaxy Foil,English
            TLA,1,0,"Gilded Card",TLA,Avatar,3,NearMint,Gilded Foil,English
            TLA,1,0,"Normal Card",TLA,Avatar,4,NearMint,Normal,English
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "all-folders.csv",
            "text/csv",
            csvContent.getBytes()
        );

        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Collections.emptyList());
        when(scryfallCardRepository.findBySetCode(anyString())).thenReturn(Collections.emptyList());

        importService.importInventory("Victor", file);

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
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "inventory.csv", 
            "text/csv", 
            csvContent.getBytes()
        );
        
        ScryfallCard sfCard = new ScryfallCard();
        sfCard.setCollectorNumber("1");
        sfCard.setSetCode("tla");
        sfCard.setName("Test Card");
        sfCard.setThumbnailFront("http://example.com/card.jpg");
        
        when(scryfallService.getCardsBySet(anyString(), any())).thenReturn(Arrays.asList(sfCard));
        when(scryfallCardRepository.findBySetCode("tla")).thenReturn(Arrays.asList(sfCard));
        
        ImportResult result = importService.importInventory("Andre", file);
        
        assertNotNull(result.getNewCards());
        assertEquals(1, result.getNewCards().size());
        assertEquals(1, result.getNewCards().get(0).getQuantity());
        assertEquals(0, result.getNewCards().get(0).getFoilQuantity());
    }
}
