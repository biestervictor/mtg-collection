package com.mtg.collection.model.health;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pre-aggregated daily health statistics.
 *
 * <p>The document ID is the ISO date string (e.g. "2024-03-15"), ensuring one
 * document per day.  High-frequency metrics (steps, energy, heart rate) are
 * accumulated here during import rather than stored as millions of individual
 * records.</p>
 */
@Document(collection = "health_daily")
public class HealthDailyMetric {

    @Id
    private String date; // "yyyy-MM-dd"

    /** Total step count for the day. */
    private long steps;

    /** Total active energy burned (kcal). */
    private double activeEnergyKcal;

    /** Total basal (resting) energy burned (kcal). */
    private double basalEnergyKcal;

    /** Total sleep hours (all sleep stages combined). */
    private double sleepHours;

    /** Average heart rate (bpm). */
    private double avgHeartRate;

    /** Minimum heart rate (bpm). */
    private double minHeartRate;

    /** Maximum heart rate (bpm). */
    private double maxHeartRate;

    /** Heart rate sample count (used for computing running average). */
    private int heartRateSamples;

    /** Average heart-rate variability (SDNN, ms). */
    private double avgHrv;

    /** HRV sample count. */
    private int hrvSamples;

    /** Average respiratory rate (breaths/min). */
    private double avgRespiratoryRate;

    /** Number of respiratory-rate samples. */
    private int respiratorySamples;

    /** Average blood-oxygen saturation (%). */
    private double avgOxygenSaturation;

    /** Oxygen saturation sample count. */
    private int oxygenSamples;

    /** Minutes of daylight exposure. */
    private double daylightMinutes;

    public HealthDailyMetric() {}

    public HealthDailyMetric(String date) {
        this.date = date;
    }

    // ── Merge helpers ────────────────────────────────────────────────────────

    public void addSteps(double val) { this.steps += (long) val; }
    public void addActiveEnergy(double val) { this.activeEnergyKcal += val; }
    public void addBasalEnergy(double val) { this.basalEnergyKcal += val; }
    public void addSleepHours(double val) { this.sleepHours += val; }
    public void addDaylight(double val) { this.daylightMinutes += val; }

    public void addHeartRate(double val) {
        // Running average
        avgHeartRate = (avgHeartRate * heartRateSamples + val) / (heartRateSamples + 1);
        if (heartRateSamples == 0 || val < minHeartRate) minHeartRate = val;
        if (val > maxHeartRate) maxHeartRate = val;
        heartRateSamples++;
    }

    public void addHrv(double val) {
        avgHrv = (avgHrv * hrvSamples + val) / (hrvSamples + 1);
        hrvSamples++;
    }

    public void addRespiratoryRate(double val) {
        avgRespiratoryRate = (avgRespiratoryRate * respiratorySamples + val) / (respiratorySamples + 1);
        respiratorySamples++;
    }

    public void addOxygenSaturation(double val) {
        avgOxygenSaturation = (avgOxygenSaturation * oxygenSamples + val) / (oxygenSamples + 1);
        oxygenSamples++;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public long getSteps() { return steps; }
    public void setSteps(long steps) { this.steps = steps; }
    public double getActiveEnergyKcal() { return activeEnergyKcal; }
    public void setActiveEnergyKcal(double activeEnergyKcal) { this.activeEnergyKcal = activeEnergyKcal; }
    public double getBasalEnergyKcal() { return basalEnergyKcal; }
    public void setBasalEnergyKcal(double basalEnergyKcal) { this.basalEnergyKcal = basalEnergyKcal; }
    public double getSleepHours() { return sleepHours; }
    public void setSleepHours(double sleepHours) { this.sleepHours = sleepHours; }
    public double getAvgHeartRate() { return avgHeartRate; }
    public void setAvgHeartRate(double avgHeartRate) { this.avgHeartRate = avgHeartRate; }
    public double getMinHeartRate() { return minHeartRate; }
    public void setMinHeartRate(double minHeartRate) { this.minHeartRate = minHeartRate; }
    public double getMaxHeartRate() { return maxHeartRate; }
    public void setMaxHeartRate(double maxHeartRate) { this.maxHeartRate = maxHeartRate; }
    public int getHeartRateSamples() { return heartRateSamples; }
    public void setHeartRateSamples(int heartRateSamples) { this.heartRateSamples = heartRateSamples; }
    public double getAvgHrv() { return avgHrv; }
    public void setAvgHrv(double avgHrv) { this.avgHrv = avgHrv; }
    public int getHrvSamples() { return hrvSamples; }
    public void setHrvSamples(int hrvSamples) { this.hrvSamples = hrvSamples; }
    public double getAvgRespiratoryRate() { return avgRespiratoryRate; }
    public void setAvgRespiratoryRate(double avgRespiratoryRate) { this.avgRespiratoryRate = avgRespiratoryRate; }
    public int getRespiratorySamples() { return respiratorySamples; }
    public void setRespiratorySamples(int respiratorySamples) { this.respiratorySamples = respiratorySamples; }
    public double getAvgOxygenSaturation() { return avgOxygenSaturation; }
    public void setAvgOxygenSaturation(double avgOxygenSaturation) { this.avgOxygenSaturation = avgOxygenSaturation; }
    public int getOxygenSamples() { return oxygenSamples; }
    public void setOxygenSamples(int oxygenSamples) { this.oxygenSamples = oxygenSamples; }
    public double getDaylightMinutes() { return daylightMinutes; }
    public void setDaylightMinutes(double daylightMinutes) { this.daylightMinutes = daylightMinutes; }
}
