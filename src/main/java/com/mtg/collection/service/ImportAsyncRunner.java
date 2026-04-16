package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Runs the actual CSV import in a Spring-managed async thread.
 * Kept as a separate bean so that @Async proxying works correctly.
 */
@Component
public class ImportAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportAsyncRunner.class);

    private final CollectionService       collectionService;
    private final InventoryImportService  inventoryImportService;

    public ImportAsyncRunner(CollectionService collectionService,
                             InventoryImportService inventoryImportService) {
        this.collectionService      = collectionService;
        this.inventoryImportService = inventoryImportService;
    }

    @Async
    public void runAsync(String jobId, String user, byte[] fileBytes,
                         String fileName, String format, ImportJobStatus status) {
        log.info("Async import job {} started for user '{}' (format={})", jobId, user, format);
        try {
            MultipartFile file = new ByteArrayMultipartFile(fileBytes, fileName);

            ImportResult result = "inventory".equals(format)
                    ? inventoryImportService.importInventory(user, file)
                    : collectionService.importCards(user, file, format);

            status.markDone(
                    result.getCardsCount(),
                    result.getAddedCardsCount(),
                    result.getRemovedCardsCount(),
                    result.getNewCardsCount()
            );
            log.info("Async import job {} done: {} cards, +{} added, -{} removed",
                    jobId, result.getCardsCount(),
                    result.getAddedCardsCount(), result.getRemovedCardsCount());

        } catch (Exception e) {
            log.error("Async import job {} failed", jobId, e);
            status.markError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ── Simple MultipartFile backed by a byte array ────────────────────────────
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;

        ByteArrayMultipartFile(byte[] content, String filename) {
            this.content  = content;
            this.filename = filename != null ? filename : "upload.csv";
        }

        @Override public String  getName()             { return "file"; }
        @Override public String  getOriginalFilename() { return filename; }
        @Override public String  getContentType()      { return "text/csv"; }
        @Override public boolean isEmpty()             { return content.length == 0; }
        @Override public long    getSize()             { return content.length; }
        @Override public byte[]  getBytes()            { return content; }
        @Override public InputStream getInputStream()  { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
