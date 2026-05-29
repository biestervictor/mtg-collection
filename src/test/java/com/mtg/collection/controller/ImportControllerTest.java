package com.mtg.collection.controller;

import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ImportJobService;
import com.mtg.collection.service.ImportJobStatus;
import com.mtg.collection.service.InventoryImportService;
import com.mtg.collection.service.UserDeckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    @Mock private CollectionService       collectionService;
    @Mock private InventoryImportService  inventoryImportService;
    @Mock private ImportHistoryRepository importHistoryRepository;
    @Mock private UserDeckService         userDeckService;
    @Mock private ImportJobService        importJobService;

    @InjectMocks
    private ImportController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(viewName.substring("redirect:".length()), true);
                    }
                    return (model, request, response) -> response.setContentType("text/html");
                })
                .build();
    }

    // ── GET /import ───────────────────────────────────────────────────────────

    @Test
    void importPage_noParams_returnsImportView() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attribute("selectedFormat", ""))
                .andExpect(model().attribute("selectedUser", "Victor"));
    }

    @Test
    void importPage_withFormat_setsFormatInModel() throws Exception {
        mockMvc.perform(get("/import").param("format", "dragonshield_web").param("user", "alice"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFormat", "dragonshield_web"))
                .andExpect(model().attribute("selectedUser", "alice"));
    }

    // ── POST /import ──────────────────────────────────────────────────────────

    @Test
    void importCards_dragonshieldFormat_callsCollectionService() throws Exception {
        ImportResult result = new ImportResult();
        result.setCardsCount(10);
        when(collectionService.importCards(eq("victor"), any(), eq("dragonshield_web")))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
                MediaType.TEXT_PLAIN_VALUE, "Card Name,Quantity\n".getBytes());

        mockMvc.perform(multipart("/import")
                        .file(file)
                        .param("user", "victor")
                        .param("format", "dragonshield_web"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"));

        verify(collectionService).importCards(eq("victor"), any(), eq("dragonshield_web"));
        verify(inventoryImportService, never()).importInventory(any(), any());
    }

    @Test
    void importCards_inventoryFormat_callsInventoryService() throws Exception {
        ImportResult result = new ImportResult();
        when(inventoryImportService.importInventory(eq("victor"), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "inv.csv",
                MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        mockMvc.perform(multipart("/import")
                        .file(file)
                        .param("user", "victor")
                        .param("format", "inventory"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"));

        verify(inventoryImportService).importInventory(eq("victor"), any());
        verify(collectionService, never()).importCards(any(), any(), any());
    }

    @Test
    void importCards_sortsAddedAndRemovedBySetThenNumber() throws Exception {
        ImportResult result = new ImportResult();
        result.setAddedCards(java.util.Arrays.asList(
                new ImportHistory.ImportedCardInfo("Zzz", "tst", "10", 1, false),
                new ImportHistory.ImportedCardInfo("Aaa", "tst", "2",  1, false)
        ));
        result.setRemovedCards(java.util.Arrays.asList(
                new ImportHistory.ImportedCardInfo("M", "abc", "5", 1, false),
                new ImportHistory.ImportedCardInfo("A", "abc", "1", 1, false)
        ));
        when(collectionService.importCards(any(), any(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "t.csv",
                MediaType.TEXT_PLAIN_VALUE, "x".getBytes());

        mockMvc.perform(multipart("/import")
                        .file(file)
                        .param("user", "victor")
                        .param("format", "dragonshield_web"))
                .andExpect(status().isOk());

        // After sorting, added[0] should be cn=2 (numeric sort)
        org.junit.jupiter.api.Assertions.assertEquals("2",
                result.getAddedCards().get(0).getCollectorNumber());
        // Removed: cn=1 comes before cn=5
        org.junit.jupiter.api.Assertions.assertEquals("1",
                result.getRemovedCards().get(0).getCollectorNumber());
    }

    // ── GET /import/history ───────────────────────────────────────────────────

    @Test
    void importHistory_noUser_returnsAllHistory() throws Exception {
        when(importHistoryRepository.findAll()).thenReturn(List.of(new ImportHistory()));

        mockMvc.perform(get("/import/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("import-history"))
                .andExpect(model().attributeExists("history"));

        verify(importHistoryRepository).findAll();
    }

    @Test
    void importHistory_withUser_returnsFilteredHistory() throws Exception {
        when(importHistoryRepository.findByUserOrderByImportedAtDesc("victor"))
                .thenReturn(List.of(new ImportHistory()));

        mockMvc.perform(get("/import/history").param("user", "victor"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedUser", "victor"));

        verify(importHistoryRepository).findByUserOrderByImportedAtDesc("victor");
        verify(importHistoryRepository, never()).findAll();
    }

    // ── POST /api/import/start ────────────────────────────────────────────────

    @Test
    void startImportAsync_success_returnsJobId() throws Exception {
        when(importJobService.submitJob(eq("victor"), any(), any(), eq("dragonshield_web")))
                .thenReturn("job-123");

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
                MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        mockMvc.perform(multipart("/api/import/start")
                        .file(file)
                        .param("user", "victor")
                        .param("format", "dragonshield_web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.user").value("victor"));
    }

    @Test
    void startImportAsync_fileReadError_returns500() throws Exception {
        when(importJobService.submitJob(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("IO error"));

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
                MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        mockMvc.perform(multipart("/api/import/start")
                        .file(file)
                        .param("user", "victor")
                        .param("format", "dragonshield_web"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("IO error"));
    }

    // ── GET /api/import/status/{jobId} ────────────────────────────────────────

    @Test
    void importJobStatus_found_returnsJobDetails() throws Exception {
        ImportJobStatus status = new ImportJobStatus("job-123", "victor", "dragonshield_web");
        status.markDone(42, 3, 1, 2);
        when(importJobService.getStatus("job-123")).thenReturn(Optional.of(status));

        mockMvc.perform(get("/api/import/status/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.user").value("victor"))
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.cardsCount").value(42));
    }

    @Test
    void importJobStatus_notFound_returns404() throws Exception {
        when(importJobService.getStatus("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/import/status/ghost"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/user/{user}/reset ───────────────────────────────────────────

    @Test
    void resetUserData_deletesDataAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/user/victor/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(collectionService).deleteUserData("victor");
    }

    // ── POST /api/user/{user}/rebuild-decks ───────────────────────────────────

    @Test
    void rebuildDecks_returnsCountAndOk() throws Exception {
        when(userDeckService.reEnrichDecks("victor")).thenReturn(3);

        mockMvc.perform(post("/api/user/victor/rebuild-decks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.decks").value(3));
    }
}
