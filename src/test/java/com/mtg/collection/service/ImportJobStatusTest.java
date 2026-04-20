package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult.DuplicateInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImportJobStatus — the in-memory state that drives the
 * "notification badge (1) on the user menu button" after an async import.
 *
 * The badge is shown when state == DONE or state == ERROR.
 * Its text content is always "1" (there is at most one pending import result
 * per browser session).  These tests verify the state-machine transitions
 * and that warning payloads (duplicates removed, unknown set codes) are
 * stored correctly so the modal can render them.
 */
class ImportJobStatusTest {

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialStateIsRunning() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertEquals(ImportJobStatus.State.RUNNING, status.getState());
    }

    @Test
    void initialCountersAreZero() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertEquals(0, status.getCardsCount());
        assertEquals(0, status.getAddedCount());
        assertEquals(0, status.getRemovedCount());
        assertEquals(0, status.getNewCardsCount());
    }

    @Test
    void initialWarningListsAreEmpty() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertNotNull(status.getDuplicatesRemoved());
        assertTrue(status.getDuplicatesRemoved().isEmpty());
        assertNotNull(status.getUnknownSetCodes());
        assertTrue(status.getUnknownSetCodes().isEmpty());
    }

    @Test
    void finishedAtIsNullBeforeCompletion() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertNull(status.getFinishedAt());
    }

    // ── markDone (basic) ─────────────────────────────────────────────────────

    @Test
    void markDoneSetsDoneState() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(100, 5, 3, 80);
        assertEquals(ImportJobStatus.State.DONE, status.getState());
    }

    @Test
    void markDoneStoresCounters() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(100, 5, 3, 80);
        assertEquals(100, status.getCardsCount());
        assertEquals(5,   status.getAddedCount());
        assertEquals(3,   status.getRemovedCount());
        assertEquals(80,  status.getNewCardsCount());
    }

    @Test
    void markDoneRecordsFinishedAt() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(10, 0, 0, 10);
        assertNotNull(status.getFinishedAt());
    }

    @Test
    void markDoneBasicKeepsWarningListsEmpty() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(10, 0, 0, 10);
        assertTrue(status.getDuplicatesRemoved().isEmpty());
        assertTrue(status.getUnknownSetCodes().isEmpty());
    }

    // ── markDone (with warnings) ─────────────────────────────────────────────
    // These are the fields that populate the warning blocks in the import
    // result modal (the modal that the badge's "1" leads the user to open).

    @Test
    void markDoneWithWarningsStoresDuplicates() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        List<DuplicateInfo> dups = List.of(
                new DuplicateInfo("MB_Deck", "Lightning Bolt", "m10", "35", false, 2)
        );
        status.markDone(10, 0, 0, 10, dups, List.of());
        assertEquals(1, status.getDuplicatesRemoved().size());
        DuplicateInfo d = status.getDuplicatesRemoved().get(0);
        assertEquals("Lightning Bolt", d.getCardName());
        assertEquals("m10",            d.getSetCode());
        assertEquals("35",             d.getCollectorNumber());
        assertFalse(d.isFoil());
        assertEquals(2,                d.getOccurrences());
    }

    @Test
    void markDoneWithWarningsStoresUnknownSetCodes() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(10, 0, 0, 10, List.of(), List.of("xyz", "abc"));
        assertEquals(List.of("xyz", "abc"), status.getUnknownSetCodes());
    }

    @Test
    void markDoneWithNullWarningListsUsesDefaults() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(10, 0, 0, 10, null, null);
        // should not throw and should leave lists as-is (empty from init)
        assertNotNull(status.getDuplicatesRemoved());
        assertNotNull(status.getUnknownSetCodes());
    }

    @Test
    void markDoneWithWarningsStillSetsDoneState() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markDone(10, 0, 0, 10,
                List.of(new DuplicateInfo("", "Card", "set", "1", false, 1)),
                List.of("unknownset"));
        assertEquals(ImportJobStatus.State.DONE, status.getState());
    }

    // ── markError ────────────────────────────────────────────────────────────

    @Test
    void markErrorSetsErrorState() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markError("Something went wrong");
        assertEquals(ImportJobStatus.State.ERROR, status.getState());
    }

    @Test
    void markErrorStoresMessage() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markError("File parse failed");
        assertEquals("File parse failed", status.getErrorMessage());
    }

    @Test
    void markErrorRecordsFinishedAt() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        status.markError("oops");
        assertNotNull(status.getFinishedAt());
    }

    @Test
    void errorMessageIsNullInitially() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertNull(status.getErrorMessage());
    }

    // ── Identity fields ──────────────────────────────────────────────────────

    @Test
    void jobIdUserFormatAreStoredCorrectly() {
        ImportJobStatus status = new ImportJobStatus("myJob", "Andre", "dragonshield_app");
        assertEquals("myJob",           status.getJobId());
        assertEquals("Andre",           status.getUser());
        assertEquals("dragonshield_app", status.getFormat());
    }

    @Test
    void startedAtIsSetOnConstruction() {
        ImportJobStatus status = new ImportJobStatus("job1", "Victor", "inventory");
        assertNotNull(status.getStartedAt());
    }
}
