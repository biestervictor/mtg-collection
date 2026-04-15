package com.mtg.collection.controller;

import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.InventoryImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@Controller
public class ImportController {

    private final CollectionService collectionService;
    private final InventoryImportService inventoryImportService;
    private final ImportHistoryRepository importHistoryRepository;

    public ImportController(CollectionService collectionService, 
                           InventoryImportService inventoryImportService,
                           ImportHistoryRepository importHistoryRepository) {
        this.collectionService = collectionService;
        this.inventoryImportService = inventoryImportService;
        this.importHistoryRepository = importHistoryRepository;
    }

    @GetMapping("/import")
    public String importPage(Model model, @RequestParam(required = false) String format) {
        model.addAttribute("selectedFormat", format != null ? format : "");
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
}
