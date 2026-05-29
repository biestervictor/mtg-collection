package com.mtg.collection.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportHistoryTest {

    private ImportHistory.ImportedCardInfo info(String name, String setCode, String cn) {
        return new ImportHistory.ImportedCardInfo(name, setCode, cn, 1, false);
    }

    // ── getAddedCardsBySet ────────────────────────────────────────────────────

    @Test
    void getAddedCardsBySet_null_returnsEmptyMap() {
        ImportHistory h = new ImportHistory();
        h.setAddedCards(null);
        assertTrue(h.getAddedCardsBySet().isEmpty());
    }

    @Test
    void getAddedCardsBySet_empty_returnsEmptyMap() {
        ImportHistory h = new ImportHistory();
        h.setAddedCards(Collections.emptyList());
        assertTrue(h.getAddedCardsBySet().isEmpty());
    }

    @Test
    void getAddedCardsBySet_groupsBySetCode() {
        ImportHistory h = new ImportHistory();
        h.setAddedCards(Arrays.asList(
                info("Alpha", "tst", "1"),
                info("Beta",  "tst", "2"),
                info("Gamma", "m10", "5")
        ));

        Map<String, List<ImportHistory.ImportedCardInfo>> result = h.getAddedCardsBySet();

        assertEquals(2, result.size());
        assertEquals(2, result.get("tst").size());
        assertEquals(1, result.get("m10").size());
    }

    @Test
    void getAddedCardsBySet_sortedBySetCodeThenNumberNumeric() {
        ImportHistory h = new ImportHistory();
        h.setAddedCards(Arrays.asList(
                info("Ten",   "tst", "10"),
                info("Two",   "tst", "2"),
                info("Alpha", "abc", "1")
        ));

        Map<String, List<ImportHistory.ImportedCardInfo>> result = h.getAddedCardsBySet();
        List<String> keys = List.copyOf(result.keySet());

        assertEquals("abc", keys.get(0), "Sets sorted alphabetically");
        assertEquals("tst", keys.get(1));
        List<ImportHistory.ImportedCardInfo> tstCards = result.get("tst");
        assertEquals("2",  tstCards.get(0).getCollectorNumber(), "CN sorted numerically: 2 before 10");
        assertEquals("10", tstCards.get(1).getCollectorNumber());
    }

    // ── ImportedCardInfo ──────────────────────────────────────────────────────

    @Test
    void importedCardInfo_getters() {
        ImportHistory.ImportedCardInfo c = new ImportHistory.ImportedCardInfo("Bolt", "m10", "150", 4, true);
        assertEquals("Bolt", c.getName());
        assertEquals("m10",  c.getSetCode());
        assertEquals("150",  c.getCollectorNumber());
        assertEquals(4,      c.getQuantity());
        assertTrue(c.isFoil());
    }

    // ── Constructor sets importedAt ───────────────────────────────────────────

    @Test
    void constructor_setsImportedAt() {
        ImportHistory h = new ImportHistory();
        assertNotNull(h.getImportedAt());
    }
}
