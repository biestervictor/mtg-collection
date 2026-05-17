package com.mtg.collection.model.health;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Stores individual body-metric measurements from Apple Health (Withings scale data,
 * VO2max, blood pressure, HRV, etc.).
 *
 * <p>High-frequency metrics (step count, active energy, heart rate) are pre-aggregated
 * into {@link HealthDailyMetric} during import to keep this collection small.</p>
 *
 * <p>Unique key: (type, date, sourceName) – allows upsert without duplicates.</p>
 */
@Document(collection = "health_metrics")
@CompoundIndexes({
    @CompoundIndex(name = "type_date_idx", def = "{'type': 1, 'date': 1}"),
    @CompoundIndex(name = "type_date_source_unique", def = "{'type': 1, 'date': 1, 'sourceName': 1}", unique = false)
})
public class HealthMetric {

    @Id
    private String id;

    /** Short type name, e.g. "BodyMass", "LeanBodyMass", "BodyFatPercentage", "VO2Max". */
    private String type;

    /** Calendar date of the measurement (from the startDate attribute). */
    private LocalDate date;

    /** Numeric value in the unit recorded by Apple Health. */
    private double value;

    /** Unit string, e.g. "kg", "%", "mL/min·kg". */
    private String unit;

    /** Source app / device, e.g. "Withings", "Apple Watch von Victor". */
    private String sourceName;

    public HealthMetric() {}

    public HealthMetric(String type, LocalDate date, double value, String unit, String sourceName) {
        this.type = type;
        this.date = date;
        this.value = value;
        this.unit = unit;
        this.sourceName = sourceName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}
