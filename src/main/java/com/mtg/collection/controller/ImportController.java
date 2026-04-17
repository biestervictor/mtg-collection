package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ImportJobService;
import com.mtg.collection.service.ImportJobStatus;
import com.mtg.collection.service.InventoryImportService;
import com.mtg.collection.service.UserDeckService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ImportController {

    private final CollectionService       collectionService;
    private final InventoryImportService  inventoryImportService;
    private final ImportHistoryRepository importHistoryRepository;
    private final UserDeckService         userDeckService;
    private final ImportJobService        importJobService;

    public ImportController(CollectionService collectionService,
                            InventoryImportService inventoryImportService,
                            ImportHistoryRepository importHistoryRepository,
                            UserDeckService userDeckService,
                            ImportJobService importJobService) {
        this.collectionService      = collectionService;
        this.inventoryImportService = inventoryImportService;
        this.importHistoryRepository = importHistoryRepository;
        this.userDeckService        = userDeckService;
        this.importJobService       = importJobService;
    }

    @GetMapping("/import")
    public String importPage(Model model,
                             @RequestParam(required = false) String format,
                             @RequestParam(required = false, defaultValue = "Victor") String user) {
        model.addAttribute("selectedFormat", format != null ? format : "");
        model.addAttribute("selectedUser", user);
        return "import";
    }

    @PostMapping("/import")
    public String importCards(@RequestParam("file") MultipartFile file,
                             @RequestParam("user") String user,
                             @RequestParam("format") String format,
                             Model model) {

        ImportResult result;

        if ("inventory".equals(format)) {
            result = inventoryImportService.importInventory(user, file);
        } else {
            result = collectionService.importCards(user, file, format);
        }

        model.addAttribute("result", result);
        model.addAttribute("selectedFormat", format);
        model.addAttribute("selectedUser", user);

        // Sort added/removed by set code then collector number for per-set grouping in template
        Comparator<ImportedCardInfo> bySetThenNumber = Comparator
                .comparing(ImportedCardInfo::getSetCode)
                .thenComparingInt(c -> {
                    try { return Integer.parseInt(c.getCollectorNumber()); }
                    catch (NumberFormatException e) { return Integer.MAX_VALUE; }
                })
                .thenComparing(ImportedCardInfo::getCollectorNumber);
        if (result.getAddedCards() != null)   result.getAddedCards().sort(bySetThenNumber);
        if (result.getRemovedCards() != null) result.getRemovedCards().sort(bySetThenNumber);

        // Aggregate newCards by card name (keep most expensive thumbnail), sort by setCode
        if (result.getNewCards() != null && !result.getNewCards().isEmpty()) {
            Map<String, CardWithUserData> aggregated = new LinkedHashMap<>();
            for (CardWithUserData cwu : result.getNewCards()) {
                if (cwu.getCard() == null) continue;
                String name = cwu.getCard().getName();
                CardWithUserData existing = aggregated.get(name);
                if (existing == null) {
                    aggregated.put(name, cwu);
                } else {
                    double existingPrice = maxPrice(existing);
                    double newPrice      = maxPrice(cwu);
                    if (newPrice > existingPrice) {
                        int qty  = existing.getQuantity()     + cwu.getQuantity();
                        int foil = existing.getFoilQuantity() + cwu.getFoilQuantity();
                        cwu.setQuantity(qty);
                        cwu.setFoilQuantity(foil);
                        aggregated.put(name, cwu);
                    } else {
                        existing.setQuantity(existing.getQuantity() + cwu.getQuantity());
                        existing.setFoilQuantity(existing.getFoilQuantity() + cwu.getFoilQuantity());
                    }
                }
            }
            List<CardWithUserData> sorted = aggregated.values().stream()
                    .sorted(Comparator.comparing(c -> c.getCard().getSetCode()))
                    .collect(Collectors.toList());
            result.setNewCards(sorted);
        }

        return "import";
    }

    @GetMapping("/import/history")
    public String importHistoryPage(Model model, @RequestParam(required = false) String user) {
        List<ImportHistory> history;
        if (user != null && !user.isEmpty()) {
            history = importHistoryRepository.findByUserOrderByImportedAtDesc(user);
        } else {
            history = importHistoryRepository.findAll();
        }
        model.addAttribute("history", history);
        model.addAttribute("selectedUser", user);
        return "import-history";
    }

    // ── REST: Async import ────────────────────────────────────────────────────

    @PostMapping("/api/import/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startImportAsync(
            @RequestParam("file")   MultipartFile file,
            @RequestParam("user")   String user,
            @RequestParam("format") String format) {
        try {
            byte[] bytes = file.getBytes();
            String jobId = importJobService.submitJob(user, bytes, file.getOriginalFilename(), format);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jobId", jobId);
            resp.put("user",  user);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Upload failed"));
        }
    }

    @GetMapping("/api/import/status/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importJobStatus(@PathVariable String jobId) {
        return importJobService.getStatus(jobId)
                .map(s -> {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("jobId",        s.getJobId());
                    resp.put("user",         s.getUser());
                    resp.put("state",        s.getState().name());
                    resp.put("cardsCount",   s.getCardsCount());
                    resp.put("addedCount",   s.getAddedCount());
                    resp.put("removedCount", s.getRemovedCount());
                    resp.put("newCardsCount",s.getNewCardsCount());
                    resp.put("errorMessage", s.getErrorMessage());
                    resp.put("duplicatesRemoved", s.getDuplicatesRemoved());
                    resp.put("unknownSetCodes",   s.getUnknownSetCodes());
                    if (s.getFinishedAt() != null) resp.put("finishedAt", s.getFinishedAt().toString());
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> ResponseEntity.notFound().<Map<String, Object>>build());
    }

    // ── REST: User data management ────────────────────────────────────────────

    @PostMapping("/api/user/{user}/reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetUserData(@PathVariable String user) {
        collectionService.deleteUserData(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "All data deleted for: " + user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/user/{user}/rebuild-decks")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rebuildDecks(@PathVariable String user) {
        int count = userDeckService.reEnrichDecks(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "Re-enriched " + count + " deck(s) for: " + user);
        result.put("decks", count);
        return ResponseEntity.ok(result);
    }

    private double maxPrice(CardWithUserData cwu) {
        if (cwu.getCard() == null) return 0;
        double r = cwu.getCard().getPriceRegular() != null ? cwu.getCard().getPriceRegular() : 0;
        double f = cwu.getCard().getPriceFoil()    != null ? cwu.getCard().getPriceFoil()    : 0;
        return Math.max(r, f);
    }
}
