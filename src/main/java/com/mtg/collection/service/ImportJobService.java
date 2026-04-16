package com.mtg.collection.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory import jobs and delegates async execution to ImportAsyncRunner.
 */
@Service
public class ImportJobService {

    private final Map<String, ImportJobStatus> jobs        = new ConcurrentHashMap<>();
    private final Map<String, String>          latestByUser = new ConcurrentHashMap<>();
    private final ImportAsyncRunner            asyncRunner;

    public ImportJobService(ImportAsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    /**
     * Creates a new job entry, fires off the async import, and returns the jobId immediately.
     */
    public String submitJob(String user, byte[] fileBytes, String fileName, String format) {
        String jobId = UUID.randomUUID().toString();
        ImportJobStatus status = new ImportJobStatus(jobId, user, format);
        jobs.put(jobId, status);
        latestByUser.put(user, jobId);
        asyncRunner.runAsync(jobId, user, fileBytes, fileName, format, status);
        return jobId;
    }

    public Optional<ImportJobStatus> getStatus(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Optional<ImportJobStatus> getLatestForUser(String user) {
        String jobId = latestByUser.get(user);
        return jobId != null ? getStatus(jobId) : Optional.empty();
    }
}
