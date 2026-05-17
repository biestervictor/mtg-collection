package com.mtg.collection.service;

import com.mtg.collection.model.health.HealthDailyMetric;
import com.mtg.collection.model.health.HealthMetric;
import com.mtg.collection.model.health.HealthWorkout;
import com.mtg.collection.repository.HealthDailyMetricRepository;
import com.mtg.collection.repository.HealthMetricRepository;
import com.mtg.collection.repository.HealthWorkoutRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Query and aggregation service for the Apple Health dashboard.
 *
 * <p>All data has already been imported into MongoDB by {@link HealthImportService}.
 * This service retrieves and transforms it for the views.</p>
 */
@Service
public class HealthDashboardService {

    // Muscle-group activation per workout type (0.0 = not targeted, 1.0 = primary)
    private static final Map<String, Map<String, Double>> MUSCLE_MAP;

    static {
        MUSCLE_MAP = new LinkedHashMap<>();

        MUSCLE_MAP.put("TraditionalStrengthTraining", Map.ofEntries(
                muscle("chest", 1.0), muscle("left-shoulder", 1.0), muscle("right-shoulder", 1.0),
                muscle("left-bicep", 1.0), muscle("right-bicep", 1.0),
                muscle("left-forearm", 0.8), muscle("right-forearm", 0.8),
                muscle("abs", 0.7), muscle("left-oblique", 0.7), muscle("right-oblique", 0.7),
                muscle("left-quad", 1.0), muscle("right-quad", 1.0),
                muscle("left-hamstring", 0.9), muscle("right-hamstring", 0.9),
                muscle("left-calf", 0.6), muscle("right-calf", 0.6),
                muscle("glutes", 0.9), muscle("back-upper", 1.0), muscle("back-lower", 0.8)
        ));

        MUSCLE_MAP.put("FunctionalStrengthTraining", Map.ofEntries(
                muscle("chest", 0.8), muscle("left-shoulder", 0.8), muscle("right-shoulder", 0.8),
                muscle("left-bicep", 0.8), muscle("right-bicep", 0.8),
                muscle("abs", 1.0), muscle("left-oblique", 0.9), muscle("right-oblique", 0.9),
                muscle("left-quad", 0.9), muscle("right-quad", 0.9),
                muscle("left-hamstring", 0.8), muscle("right-hamstring", 0.8),
                muscle("glutes", 0.8), muscle("back-upper", 0.9), muscle("back-lower", 0.9)
        ));

        MUSCLE_MAP.put("CoreTraining", Map.ofEntries(
                muscle("abs", 1.0), muscle("left-oblique", 1.0), muscle("right-oblique", 1.0),
                muscle("back-lower", 0.9), muscle("glutes", 0.6),
                muscle("left-quad", 0.4), muscle("right-quad", 0.4)
        ));

        MUSCLE_MAP.put("Running", Map.ofEntries(
                muscle("left-quad", 1.0), muscle("right-quad", 1.0),
                muscle("left-hamstring", 1.0), muscle("right-hamstring", 1.0),
                muscle("left-calf", 1.0), muscle("right-calf", 1.0),
                muscle("glutes", 0.9),
                muscle("abs", 0.4), muscle("left-oblique", 0.3), muscle("right-oblique", 0.3)
        ));

        MUSCLE_MAP.put("Walking", Map.ofEntries(
                muscle("left-quad", 0.6), muscle("right-quad", 0.6),
                muscle("left-hamstring", 0.6), muscle("right-hamstring", 0.6),
                muscle("left-calf", 0.7), muscle("right-calf", 0.7),
                muscle("glutes", 0.5)
        ));

        MUSCLE_MAP.put("Cycling", Map.ofEntries(
                muscle("left-quad", 1.0), muscle("right-quad", 1.0),
                muscle("left-hamstring", 0.7), muscle("right-hamstring", 0.7),
                muscle("left-calf", 0.6), muscle("right-calf", 0.6),
                muscle("glutes", 0.8), muscle("abs", 0.4)
        ));

        MUSCLE_MAP.put("Hiking", Map.ofEntries(
                muscle("left-quad", 0.9), muscle("right-quad", 0.9),
                muscle("left-hamstring", 0.8), muscle("right-hamstring", 0.8),
                muscle("left-calf", 0.8), muscle("right-calf", 0.8),
                muscle("glutes", 0.8), muscle("abs", 0.3)
        ));

        MUSCLE_MAP.put("Swimming", Map.ofEntries(
                muscle("left-shoulder", 1.0), muscle("right-shoulder", 1.0),
                muscle("chest", 0.9), muscle("back-upper", 1.0),
                muscle("left-bicep", 0.8), muscle("right-bicep", 0.8),
                muscle("left-forearm", 0.7), muscle("right-forearm", 0.7),
                muscle("abs", 0.8), muscle("left-oblique", 0.7), muscle("right-oblique", 0.7),
                muscle("left-quad", 0.5), muscle("right-quad", 0.5),
                muscle("left-calf", 0.4), muscle("right-calf", 0.4)
        ));

        MUSCLE_MAP.put("Yoga", Map.ofEntries(
                muscle("abs", 0.7), muscle("left-oblique", 0.7), muscle("right-oblique", 0.7),
                muscle("back-upper", 0.6), muscle("back-lower", 0.7),
                muscle("left-shoulder", 0.5), muscle("right-shoulder", 0.5),
                muscle("left-quad", 0.5), muscle("right-quad", 0.5),
                muscle("glutes", 0.5), muscle("left-hamstring", 0.6), muscle("right-hamstring", 0.6)
        ));

        MUSCLE_MAP.put("Rowing", Map.ofEntries(
                muscle("back-upper", 1.0), muscle("back-lower", 0.9),
                muscle("left-bicep", 0.8), muscle("right-bicep", 0.8),
                muscle("left-forearm", 0.7), muscle("right-forearm", 0.7),
                muscle("left-quad", 0.6), muscle("right-quad", 0.6),
                muscle("abs", 0.7), muscle("glutes", 0.6)
        ));
    }

    private static Map.Entry<String, Double> muscle(String name, double val) {
        return Map.entry(name, val);
    }

    private final HealthMetricRepository metricRepo;
    private final HealthDailyMetricRepository dailyRepo;
    private final HealthWorkoutRepository workoutRepo;

    public HealthDashboardService(HealthMetricRepository metricRepo,
                                  HealthDailyMetricRepository dailyRepo,
                                  HealthWorkoutRepository workoutRepo) {
        this.metricRepo  = metricRepo;
        this.dailyRepo   = dailyRepo;
        this.workoutRepo = workoutRepo;
    }

    // ── Dashboard summary ─────────────────────────────────────────────────────

    public Optional<Double> getLatest(String type) {
        return metricRepo.findTopByTypeOrderByDateDesc(type).map(HealthMetric::getValue);
    }

    public Optional<LocalDate> getLatestDate(String type) {
        return metricRepo.findTopByTypeOrderByDateDesc(type).map(HealthMetric::getDate);
    }

    /** Returns count of workouts in the last N days. */
    public long workoutsInLastDays(int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        return workoutRepo.countByDateBetween(from, LocalDate.now());
    }

    /** Total workout count. */
    public long totalWorkouts() { return workoutRepo.count(); }

    /** Data start date (oldest body mass record). */
    public Optional<LocalDate> getDataStartDate() {
        return metricRepo.findByTypeOrderByDateAsc("BodyMass")
                .stream().findFirst().map(HealthMetric::getDate);
    }

    // ── Body composition series ───────────────────────────────────────────────

    public record BodyCompositionPoint(String date, Double bodyMassKg, Double leanBodyMassKg,
                                       Double bodyFatPct, Double bmi) {}

    /**
     * Returns daily body-composition data merged from all four metrics.
     * Dates without a measurement for a given type carry the last known value.
     */
    public List<BodyCompositionPoint> getBodyCompositionSeries(LocalDate from, LocalDate to) {
        Map<LocalDate, Double> massMap    = toMap(metricRepo.findByTypeAndDateBetweenOrderByDateAsc("BodyMass",         from, to));
        Map<LocalDate, Double> leanMap    = toMap(metricRepo.findByTypeAndDateBetweenOrderByDateAsc("LeanBodyMass",     from, to));
        Map<LocalDate, Double> fatMap     = toMap(metricRepo.findByTypeAndDateBetweenOrderByDateAsc("BodyFatPercentage", from, to));
        Map<LocalDate, Double> bmiMap     = toMap(metricRepo.findByTypeAndDateBetweenOrderByDateAsc("BMI",             from, to));

        // Collect all dates that have at least one data point
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(massMap.keySet());
        allDates.addAll(leanMap.keySet());
        allDates.addAll(fatMap.keySet());
        allDates.addAll(bmiMap.keySet());

        List<BodyCompositionPoint> result = new ArrayList<>();
        for (LocalDate d : allDates) {
            Double fat = fatMap.get(d);
            if (fat != null && fat > 1.0) fat = fat / 100.0; // normalise fraction stored as %
            result.add(new BodyCompositionPoint(
                    d.toString(),
                    massMap.get(d),
                    leanMap.get(d),
                    fat != null ? fat * 100.0 : null, // display as percent
                    bmiMap.get(d)
            ));
        }
        return result;
    }

    private Map<LocalDate, Double> toMap(List<HealthMetric> list) {
        // When multiple measurements exist for the same day, take the last
        Map<LocalDate, Double> map = new LinkedHashMap<>();
        for (HealthMetric m : list) map.put(m.getDate(), m.getValue());
        return map;
    }

    // ── Workout type distribution ─────────────────────────────────────────────

    public Map<String, Long> getWorkoutTypeCounts(LocalDate from, LocalDate to) {
        return workoutRepo.findByDateBetweenOrderByDateDesc(from, to)
                .stream()
                .collect(Collectors.groupingBy(HealthWorkout::getActivityType, Collectors.counting()));
    }

    public List<HealthWorkout> getRecentWorkouts(int limit) {
        return workoutRepo.findTop10ByOrderByDateDesc();
    }

    // ── Workout calendar (heatmap data) ──────────────────────────────────────

    public record WorkoutDay(String date, int count, String primaryType) {}

    public List<WorkoutDay> getWorkoutCalendar(LocalDate from, LocalDate to) {
        Map<LocalDate, List<HealthWorkout>> byDay = workoutRepo.findByDateBetweenOrderByDateDesc(from, to)
                .stream()
                .collect(Collectors.groupingBy(HealthWorkout::getDate));

        List<WorkoutDay> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<HealthWorkout>> e : byDay.entrySet()) {
            String primary = e.getValue().get(0).getActivityType();
            result.add(new WorkoutDay(e.getKey().toString(), e.getValue().size(), primary));
        }
        return result;
    }

    // ── Body map: muscle activation ───────────────────────────────────────────

    /**
     * Computes per-muscle-group intensity for the given period.
     * Returns a map of muscle-group-id → intensity (0.0 – 1.0).
     */
    public Map<String, Double> getMuscleActivation(LocalDate from, LocalDate to) {
        Map<String, Long> typeCounts = getWorkoutTypeCounts(from, to);
        Map<String, Double> rawScores = new LinkedHashMap<>();

        for (Map.Entry<String, Long> e : typeCounts.entrySet()) {
            Map<String, Double> muscles = MUSCLE_MAP.getOrDefault(e.getKey(), Map.of());
            long count = e.getValue();
            for (Map.Entry<String, Double> m : muscles.entrySet()) {
                rawScores.merge(m.getKey(), count * m.getValue(), Double::sum);
            }
        }
        if (rawScores.isEmpty()) return rawScores;

        // Normalise to 0.0–1.0
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        rawScores.replaceAll((k, v) -> Math.min(1.0, v / max));
        return rawScores;
    }

    // ── Daily series ──────────────────────────────────────────────────────────

    public List<HealthDailyMetric> getDailySeries(LocalDate from, LocalDate to) {
        return dailyRepo.findByDateBetweenOrderByDateAsc(from.toString(), to.toString());
    }

    public List<HealthMetric> getMetricSeries(String type, LocalDate from, LocalDate to) {
        return metricRepo.findByTypeAndDateBetweenOrderByDateAsc(type, from, to);
    }

    // ── Summary stats for dashboard cards ────────────────────────────────────

    public record DashboardStats(
            Double currentWeightKg, LocalDate weightDate,
            Double currentLeanMassKg, LocalDate leanMassDate,
            Double currentBodyFatPct, Double currentBmi,
            Double latestVO2Max, LocalDate vo2maxDate,
            Double latestRestingHR, LocalDate restingHRDate,
            Long recentWorkouts30d, Long totalWorkouts,
            Double avgSteps7d, Double avgSleep7d,
            Double avgHRV7d
    ) {}

    public DashboardStats getDashboardStats() {
        Double weight    = getLatest("BodyMass").orElse(null);
        LocalDate wDate  = getLatestDate("BodyMass").orElse(null);
        Double lean      = getLatest("LeanBodyMass").orElse(null);
        LocalDate lDate  = getLatestDate("LeanBodyMass").orElse(null);
        Double fat       = getLatest("BodyFatPercentage").orElse(null);
        if (fat != null && fat <= 1.0) fat = fat * 100.0; // normalise
        Double bmi       = getLatest("BMI").orElse(null);
        Double vo2       = getLatest("VO2Max").orElse(null);
        LocalDate v2Date = getLatestDate("VO2Max").orElse(null);
        Double rhr       = getLatest("RestingHeartRate").orElse(null);
        LocalDate rDate  = getLatestDate("RestingHeartRate").orElse(null);

        long recent30  = workoutsInLastDays(30);
        long total     = totalWorkouts();

        // 7-day averages from daily
        LocalDate now = LocalDate.now();
        List<HealthDailyMetric> last7 = dailyRepo.findByDateBetweenOrderByDateAsc(
                now.minusDays(7).toString(), now.toString());
        double avgSteps = last7.stream().mapToLong(HealthDailyMetric::getSteps).average().orElse(0);
        double avgSleep = last7.stream().mapToDouble(HealthDailyMetric::getSleepHours).average().orElse(0);
        double avgHRV   = last7.stream().mapToDouble(HealthDailyMetric::getAvgHrv).filter(v -> v > 0).average().orElse(0);

        return new DashboardStats(weight, wDate, lean, lDate, fat, bmi,
                vo2, v2Date, rhr, rDate, recent30, total,
                avgSteps, avgSleep, avgHRV);
    }
}
