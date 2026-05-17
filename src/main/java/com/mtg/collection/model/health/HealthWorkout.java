package com.mtg.collection.model.health;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Represents a single workout session from Apple Health.
 *
 * <p>The activity type is stored as a short label (e.g. "TraditionalStrengthTraining",
 * "Running") after stripping the "HKWorkoutActivityType" prefix for readability.</p>
 */
@Document(collection = "health_workouts")
public class HealthWorkout {

    @Id
    private String id;

    /**
     * Short workout type, e.g. "TraditionalStrengthTraining", "Running", "Walking".
     * Derived by stripping "HKWorkoutActivityType" prefix from the Apple Health value.
     */
    @Indexed
    private String activityType;

    /** Calendar date of the workout (derived from startDate). */
    @Indexed
    private LocalDate date;

    /** ISO start datetime string (for display purposes). */
    private String startDate;

    /** ISO end datetime string. */
    private String endDate;

    /** Workout duration in minutes. */
    private double durationMinutes;

    /** Total active energy burned (kcal). */
    private double caloriesBurned;

    /** Distance in km (for running, cycling, walking etc.; 0 for strength training). */
    private double distanceKm;

    /** Source app / device. */
    private String sourceName;

    public HealthWorkout() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public double getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(double durationMinutes) { this.durationMinutes = durationMinutes; }
    public double getCaloriesBurned() { return caloriesBurned; }
    public void setCaloriesBurned(double caloriesBurned) { this.caloriesBurned = caloriesBurned; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    /** Display-friendly label for the activity type. */
    public String getActivityLabel() {
        if (activityType == null) return "Unknown";
        return switch (activityType) {
            case "TraditionalStrengthTraining"  -> "Krafttraining";
            case "FunctionalStrengthTraining"   -> "Functional Strength";
            case "Running"                      -> "Laufen";
            case "Walking"                      -> "Gehen";
            case "Cycling"                      -> "Radfahren";
            case "Hiking"                       -> "Wandern";
            case "CoreTraining"                 -> "Core Training";
            case "Yoga"                         -> "Yoga";
            case "Swimming"                     -> "Schwimmen";
            case "CardioDance"                  -> "Tanz/Cardio";
            case "Rowing"                       -> "Rudern";
            default                             -> activityType;
        };
    }

    /** Icon class (Bootstrap Icons) for the activity type. */
    public String getActivityIcon() {
        if (activityType == null) return "bi-activity";
        return switch (activityType) {
            case "TraditionalStrengthTraining", "FunctionalStrengthTraining" -> "bi-lightning-charge-fill";
            case "Running"   -> "bi-person-walking";
            case "Walking"   -> "bi-person-walking";
            case "Cycling"   -> "bi-bicycle";
            case "Hiking"    -> "bi-geo-alt-fill";
            case "CoreTraining" -> "bi-person-arms-up";
            case "Yoga"      -> "bi-peace";
            case "Swimming"  -> "bi-water";
            default          -> "bi-activity";
        };
    }
}
