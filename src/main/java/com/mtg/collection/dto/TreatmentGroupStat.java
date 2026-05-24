package com.mtg.collection.dto;

/**
 * Carries the per-treatment-group statistics shown as a visual divider in the
 * show-collection card grid (e.g. "Showcase · 5 von 80 fehlen").
 */
public class TreatmentGroupStat {

    private final String label;
    /** Total cards in this group for the selected set (unfiltered). */
    private final int total;
    /** Cards in this group that the user does not own at all. */
    private final int missing;

    public TreatmentGroupStat(String label, int total, int missing) {
        this.label   = label;
        this.total   = total;
        this.missing = missing;
    }

    public String getLabel()   { return label;   }
    public int    getTotal()   { return total;   }
    public int    getMissing() { return missing; }
}
