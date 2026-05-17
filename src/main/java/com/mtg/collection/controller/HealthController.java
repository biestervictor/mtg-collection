package com.mtg.collection.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mtg.collection.model.health.HealthDailyMetric;
import com.mtg.collection.model.health.HealthMetric;
import com.mtg.collection.model.health.HealthWorkout;
import com.mtg.collection.service.HealthDashboardService;
import com.mtg.collection.service.HealthImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Apple Health dashboard feature.
 *
 * <p>Routes:</p>
 * <ul>
 *   <li>{@code GET  /health}                  — Dashboard overview</li>
 *   <li>{@code GET  /health/import}            — Import page</li>
 *   <li>{@code POST /health/import}            — Start async import</li>
 *   <li>{@code GET  /health/import/status}     — Polling endpoint (JSON)</li>
 *   <li>{@code GET  /health/body-composition}  — Weight / lean mass charts</li>
 *   <li>{@code GET  /health/body-map}          — Muscle activation map</li>
 *   <li>{@code GET  /health/workouts}          — Workout history</li>
 *   <li>{@code GET  /health/cardio}            — Cardio / HR metrics</li>
 *   <li>{@code GET  /health/api/*}             — JSON data endpoints for charts</li>
 * </ul>
 */
@Controller
@RequestMapping("/health")
public class HealthController {

    private static final ObjectMapper JSON;
    static {
        JSON = new ObjectMapper();
        JSON.registerModule(new JavaTimeModule());
        JSON.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private final HealthDashboardService dashService;
    private final HealthImportService    importService;

    public HealthController(HealthDashboardService dashService, HealthImportService importService) {
        this.dashService   = dashService;
        this.importService = importService;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", dashService.getDashboardStats());

        // Sparkline: last 30 days body composition
        LocalDate now  = LocalDate.now();
        List<HealthDashboardService.BodyCompositionPoint> spark =
                dashService.getBodyCompositionSeries(now.minusDays(30), now);
        model.addAttribute("bodyCompSpark", toJson(spark));

        // Last 30 days daily (steps + energy) for activity sparkline
        List<HealthDailyMetric> daily30 = dashService.getDailySeries(now.minusDays(30), now);
        model.addAttribute("daily30", toJson(daily30));

        // Recent workouts
        model.addAttribute("recentWorkouts", dashService.getRecentWorkouts(10));

        // Workout type distribution (last 90 days)
        Map<String, Long> typeCounts = dashService.getWorkoutTypeCounts(now.minusDays(90), now);
        model.addAttribute("workoutTypes", toJson(typeCounts));

        model.addAttribute("hasData", dashService.totalWorkouts() > 0);
        return "health-dashboard";
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @GetMapping("/import")
    public String importPage(Model model) {
        model.addAttribute("status",        importService.getStatus());
        model.addAttribute("processed",     importService.getProcessedRecords());
        model.addAttribute("importError",   importService.getImportError());
        return "health-import";
    }

    @PostMapping("/import")
    public String startImport(@RequestParam String filePath) {
        importService.startImportAsync(filePath.trim());
        return "redirect:/health/import";
    }

    @GetMapping("/import/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importStatus() {
        Map<String, Object> result = Map.of(
                "status",          importService.getStatus(),
                "processed",       importService.getProcessedRecords(),
                "importedMetrics", importService.getImportedMetrics(),
                "importedWorkouts",importService.getImportedWorkouts(),
                "importedDays",    importService.getImportedDays(),
                "error",           importService.getImportError() != null ? importService.getImportError() : ""
        );
        return ResponseEntity.ok(result);
    }

    // ── Body Composition ──────────────────────────────────────────────────────

    @GetMapping("/body-composition")
    public String bodyComposition(@RequestParam(defaultValue = "365") int days, Model model) {
        LocalDate now  = LocalDate.now();
        LocalDate from = now.minusDays(days);
        List<HealthDashboardService.BodyCompositionPoint> series =
                dashService.getBodyCompositionSeries(from, now);
        model.addAttribute("series",    toJson(series));
        model.addAttribute("days",      days);
        model.addAttribute("stats",     dashService.getDashboardStats());
        return "health-body-composition";
    }

    // ── Body Map ─────────────────────────────────────────────────────────────

    @GetMapping("/body-map")
    public String bodyMap(@RequestParam(defaultValue = "90") int days, Model model) {
        LocalDate now  = LocalDate.now();
        LocalDate from = now.minusDays(days);
        Map<String, Double> activation = dashService.getMuscleActivation(from, now);
        Map<String, Long>   typeCounts = dashService.getWorkoutTypeCounts(from, now);
        model.addAttribute("activation",  toJson(activation));
        model.addAttribute("typeCounts",  toJson(typeCounts));
        model.addAttribute("days",        days);
        return "health-body-map";
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    @GetMapping("/workouts")
    public String workouts(@RequestParam(defaultValue = "365") int days, Model model) {
        LocalDate now  = LocalDate.now();
        LocalDate from = now.minusDays(days);
        Map<String, Long> typeCounts = dashService.getWorkoutTypeCounts(from, now);
        List<HealthDashboardService.WorkoutDay> calendar = dashService.getWorkoutCalendar(from, now);
        List<HealthWorkout> recent  = dashService.getRecentWorkouts(20);

        // Running trend (last 365 days)
        List<HealthMetric> vo2series = dashService.getMetricSeries("VO2Max",
                now.minusDays(365), now);

        model.addAttribute("typeCounts",  toJson(typeCounts));
        model.addAttribute("calendar",    toJson(calendar));
        model.addAttribute("recentWorkouts", recent);
        model.addAttribute("vo2series",   toJson(vo2series));
        model.addAttribute("days",        days);
        model.addAttribute("totalWorkouts", dashService.totalWorkouts());
        return "health-workouts";
    }

    // ── Cardio ────────────────────────────────────────────────────────────────

    @GetMapping("/cardio")
    public String cardio(@RequestParam(defaultValue = "180") int days, Model model) {
        LocalDate now  = LocalDate.now();
        LocalDate from = now.minusDays(days);

        List<HealthMetric> rhr   = dashService.getMetricSeries("RestingHeartRate", from, now);
        List<HealthDailyMetric> dailyHrv = dashService.getDailySeries(from, now);
        List<HealthMetric> vo2   = dashService.getMetricSeries("VO2Max", from, now);
        List<HealthMetric> sysBP = dashService.getMetricSeries("SystolicBP", from, now);
        List<HealthMetric> diaBP = dashService.getMetricSeries("DiastolicBP", from, now);

        model.addAttribute("rhrSeries",  toJson(rhr));
        model.addAttribute("hrvSeries",  toJson(dailyHrv));
        model.addAttribute("vo2Series",  toJson(vo2));
        model.addAttribute("sysBPSeries",toJson(sysBP));
        model.addAttribute("diaBPSeries",toJson(diaBP));
        model.addAttribute("days",       days);
        model.addAttribute("stats",      dashService.getDashboardStats());
        return "health-cardio";
    }

    // ── JSON API for chart.js data refresh ───────────────────────────────────

    @GetMapping(value = "/api/body-composition", produces = "application/json")
    @ResponseBody
    public List<HealthDashboardService.BodyCompositionPoint> apiBodyComposition(
            @RequestParam(defaultValue = "365") int days) {
        LocalDate now = LocalDate.now();
        return dashService.getBodyCompositionSeries(now.minusDays(days), now);
    }

    @GetMapping(value = "/api/muscle-activation", produces = "application/json")
    @ResponseBody
    public Map<String, Double> apiMuscleActivation(@RequestParam(defaultValue = "90") int days) {
        LocalDate now = LocalDate.now();
        return dashService.getMuscleActivation(now.minusDays(days), now);
    }

    @GetMapping(value = "/api/workout-calendar", produces = "application/json")
    @ResponseBody
    public List<HealthDashboardService.WorkoutDay> apiWorkoutCalendar(
            @RequestParam(defaultValue = "365") int days) {
        LocalDate now = LocalDate.now();
        return dashService.getWorkoutCalendar(now.minusDays(days), now);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
