package com.mtg.collection.service.health;

import com.mtg.collection.model.health.HealthDailyMetric;
import com.mtg.collection.model.health.HealthMetric;
import com.mtg.collection.model.health.HealthWorkout;
import com.mtg.collection.repository.HealthDailyMetricRepository;
import com.mtg.collection.repository.HealthMetricRepository;
import com.mtg.collection.repository.HealthWorkoutRepository;
import com.mtg.collection.service.HealthDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthDashboardService}.
 *
 * <p>Uses Mockito to stub the three repositories.  No Spring context or MongoDB
 * connection required.</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthDashboardServiceTest {

    @Mock private HealthMetricRepository metricRepo;
    @Mock private HealthDailyMetricRepository dailyRepo;
    @Mock private HealthWorkoutRepository workoutRepo;

    private HealthDashboardService service;

    @BeforeEach
    void setUp() {
        service = new HealthDashboardService(metricRepo, dailyRepo, workoutRepo);
    }

    // ── getLatest / getLatestDate ─────────────────────────────────────────────

    @Test
    void getLatest_returnsValueFromRepo() {
        HealthMetric m = metric("BodyMass", LocalDate.of(2025, 1, 10), 80.5);
        when(metricRepo.findTopByTypeOrderByDateDesc("BodyMass")).thenReturn(Optional.of(m));

        assertThat(service.getLatest("BodyMass")).hasValue(80.5);
    }

    @Test
    void getLatest_emptyWhenNoData() {
        when(metricRepo.findTopByTypeOrderByDateDesc(anyString())).thenReturn(Optional.empty());

        assertThat(service.getLatest("BodyMass")).isEmpty();
    }

    @Test
    void getLatestDate_returnsCorrectDate() {
        LocalDate expected = LocalDate.of(2025, 3, 20);
        HealthMetric m = metric("LeanBodyMass", expected, 60.0);
        when(metricRepo.findTopByTypeOrderByDateDesc("LeanBodyMass")).thenReturn(Optional.of(m));

        assertThat(service.getLatestDate("LeanBodyMass")).hasValue(expected);
    }

    // ── totalWorkouts / workoutsInLastDays ────────────────────────────────────

    @Test
    void totalWorkouts_delegatesToRepo() {
        when(workoutRepo.count()).thenReturn(1455L);
        assertThat(service.totalWorkouts()).isEqualTo(1455L);
    }

    @Test
    void workoutsInLastDays_countsCorrectRange() {
        when(workoutRepo.countByDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(12L);
        assertThat(service.workoutsInLastDays(30)).isEqualTo(12L);
    }

    // ── getWorkoutTypeCounts ──────────────────────────────────────────────────

    @Test
    void getWorkoutTypeCounts_groupsByActivityType() {
        List<HealthWorkout> workouts = List.of(
                workout("Running"),
                workout("Running"),
                workout("TraditionalStrengthTraining")
        );
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any())).thenReturn(workouts);

        Map<String, Long> counts = service.getWorkoutTypeCounts(
                LocalDate.now().minusDays(90), LocalDate.now());

        assertThat(counts).containsEntry("Running", 2L)
                          .containsEntry("TraditionalStrengthTraining", 1L);
    }

    @Test
    void getWorkoutTypeCounts_emptyListReturnsEmptyMap() {
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any())).thenReturn(List.of());
        assertThat(service.getWorkoutTypeCounts(LocalDate.now().minusDays(30), LocalDate.now())).isEmpty();
    }

    // ── getMuscleActivation ───────────────────────────────────────────────────

    @Test
    void getMuscleActivation_normalisedTo1() {
        List<HealthWorkout> workouts = List.of(
                workout("Running"), workout("Running"), workout("Running")
        );
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any())).thenReturn(workouts);

        Map<String, Double> activation = service.getMuscleActivation(
                LocalDate.now().minusDays(90), LocalDate.now());

        assertThat(activation).isNotEmpty();
        // Max value must be exactly 1.0
        double max = activation.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        assertThat(max).isEqualTo(1.0);
    }

    @Test
    void getMuscleActivation_emptyWhenNoWorkouts() {
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any())).thenReturn(List.of());
        assertThat(service.getMuscleActivation(LocalDate.now().minusDays(30), LocalDate.now())).isEmpty();
    }

    @Test
    void getMuscleActivation_unknownTypeIgnored() {
        // An unrecognised workout type should not throw – just contribute nothing
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any()))
                .thenReturn(List.of(workout("UnknownSport")));

        Map<String, Double> activation = service.getMuscleActivation(
                LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(activation).isEmpty();
    }

    // ── getWorkoutCalendar ────────────────────────────────────────────────────

    @Test
    void getWorkoutCalendar_groupsByDate() {
        LocalDate day = LocalDate.of(2025, 4, 1);
        HealthWorkout w1 = workout("Running");
        w1.setDate(day);
        HealthWorkout w2 = workout("Walking");
        w2.setDate(day);
        when(workoutRepo.findByDateBetweenOrderByDateDesc(any(), any())).thenReturn(List.of(w1, w2));

        List<HealthDashboardService.WorkoutDay> cal = service.getWorkoutCalendar(
                day.minusDays(1), day.plusDays(1));

        assertThat(cal).hasSize(1);
        assertThat(cal.get(0).count()).isEqualTo(2);
        assertThat(cal.get(0).date()).isEqualTo("2025-04-01");
    }

    // ── getBodyCompositionSeries ──────────────────────────────────────────────

    @Test
    void getBodyCompositionSeries_returnsPointsForDatesWithData() {
        LocalDate d = LocalDate.of(2025, 6, 1);
        HealthMetric mass = metric("BodyMass", d, 78.5);
        HealthMetric lean = metric("LeanBodyMass", d, 60.0);

        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BodyMass"), any(), any()))
                .thenReturn(List.of(mass));
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("LeanBodyMass"), any(), any()))
                .thenReturn(List.of(lean));
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BodyFatPercentage"), any(), any()))
                .thenReturn(List.of());
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BMI"), any(), any()))
                .thenReturn(List.of());

        List<HealthDashboardService.BodyCompositionPoint> pts =
                service.getBodyCompositionSeries(d.minusDays(1), d.plusDays(1));

        assertThat(pts).hasSize(1);
        assertThat(pts.get(0).bodyMassKg()).isEqualTo(78.5);
        assertThat(pts.get(0).leanBodyMassKg()).isEqualTo(60.0);
        assertThat(pts.get(0).date()).isEqualTo("2025-06-01");
    }

    @Test
    void getBodyCompositionSeries_bodyFatPercentNormalisedFromFraction() {
        LocalDate d = LocalDate.of(2025, 6, 1);
        // Apple Health may store body fat as a fraction (e.g. 0.18 → 18%)
        HealthMetric fat = metric("BodyFatPercentage", d, 0.18);

        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BodyMass"), any(), any()))
                .thenReturn(List.of());
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("LeanBodyMass"), any(), any()))
                .thenReturn(List.of());
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BodyFatPercentage"), any(), any()))
                .thenReturn(List.of(fat));
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(eq("BMI"), any(), any()))
                .thenReturn(List.of());

        List<HealthDashboardService.BodyCompositionPoint> pts =
                service.getBodyCompositionSeries(d.minusDays(1), d.plusDays(1));

        assertThat(pts).hasSize(1);
        // 0.18 ≤ 1.0 → no normalisation needed, just multiply back for display: 0.18 * 100 = 18.0
        assertThat(pts.get(0).bodyFatPct()).isEqualTo(18.0);
    }

    // ── getDashboardStats ─────────────────────────────────────────────────────

    @Test
    void getDashboardStats_returnsNullsWhenNoData() {
        when(metricRepo.findTopByTypeOrderByDateDesc(anyString())).thenReturn(Optional.empty());
        when(workoutRepo.count()).thenReturn(0L);
        when(workoutRepo.countByDateBetween(any(), any())).thenReturn(0L);
        when(dailyRepo.findByDateBetweenOrderByDateAsc(anyString(), anyString())).thenReturn(List.of());

        HealthDashboardService.DashboardStats stats = service.getDashboardStats();

        assertThat(stats.currentWeightKg()).isNull();
        assertThat(stats.latestVO2Max()).isNull();
        assertThat(stats.totalWorkouts()).isEqualTo(0L);
        assertThat(stats.avgSteps7d()).isEqualTo(0.0);
    }

    @Test
    void getDashboardStats_bodyFatNormalisedFromFraction() {
        // If fat stored as 0.20 (fraction), getDashboardStats must return 20.0 (percent)
        HealthMetric fat = metric("BodyFatPercentage", LocalDate.now(), 0.20);
        // General stub first, then override for the specific type
        when(metricRepo.findTopByTypeOrderByDateDesc(anyString())).thenReturn(Optional.empty());
        when(metricRepo.findTopByTypeOrderByDateDesc("BodyFatPercentage")).thenReturn(Optional.of(fat));
        when(workoutRepo.count()).thenReturn(0L);
        when(workoutRepo.countByDateBetween(any(), any())).thenReturn(0L);
        when(dailyRepo.findByDateBetweenOrderByDateAsc(anyString(), anyString())).thenReturn(List.of());

        HealthDashboardService.DashboardStats stats = service.getDashboardStats();

        assertThat(stats.currentBodyFatPct()).isEqualTo(20.0);
    }

    @Test
    void getDashboardStats_avgStepsComputedFromLast7Days() {
        HealthDailyMetric d1 = dailyMetric("2025-05-10", 8000, 0);
        HealthDailyMetric d2 = dailyMetric("2025-05-11", 12000, 0);
        when(metricRepo.findTopByTypeOrderByDateDesc(anyString())).thenReturn(Optional.empty());
        when(workoutRepo.count()).thenReturn(0L);
        when(workoutRepo.countByDateBetween(any(), any())).thenReturn(0L);
        when(dailyRepo.findByDateBetweenOrderByDateAsc(anyString(), anyString()))
                .thenReturn(List.of(d1, d2));

        HealthDashboardService.DashboardStats stats = service.getDashboardStats();

        assertThat(stats.avgSteps7d()).isEqualTo(10000.0);
    }

    // ── getDailySeries / getMetricSeries ──────────────────────────────────────

    @Test
    void getDailySeries_delegatesToRepo() {
        HealthDailyMetric d = dailyMetric("2025-01-01", 5000, 7.5);
        when(dailyRepo.findByDateBetweenOrderByDateAsc(anyString(), anyString()))
                .thenReturn(List.of(d));

        List<HealthDailyMetric> result = service.getDailySeries(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSteps()).isEqualTo(5000);
    }

    @Test
    void getMetricSeries_delegatesToRepo() {
        HealthMetric m = metric("VO2Max", LocalDate.of(2025, 2, 14), 47.3);
        when(metricRepo.findByTypeAndDateBetweenOrderByDateAsc(anyString(), any(), any()))
                .thenReturn(List.of(m));

        List<HealthMetric> result = service.getMetricSeries(
                "VO2Max", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(47.3);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private HealthMetric metric(String type, LocalDate date, double value) {
        return new HealthMetric(type, date, value, "", "");
    }

    private HealthWorkout workout(String activityType) {
        HealthWorkout w = new HealthWorkout();
        w.setActivityType(activityType);
        w.setDate(LocalDate.now());
        return w;
    }

    private HealthDailyMetric dailyMetric(String date, long steps, double sleepHours) {
        HealthDailyMetric d = new HealthDailyMetric(date);
        d.setSteps(steps);
        d.setSleepHours(sleepHours);
        return d;
    }
}
