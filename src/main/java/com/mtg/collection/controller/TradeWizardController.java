package com.mtg.collection.controller;

import com.mtg.collection.dto.*;
import com.mtg.collection.service.TradeWizardService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST endpoint for the Trade Wizard feature on the /compare page.
 *
 * Exposes POST /api/compare/trade-wizard which computes either:
 *  - Greedy 1:1 pair matches within a price tolerance, OR
 *  - Karmarkar-Karp n:m bundle approximation across multiple sets.
 *
 * Pool definition (per user):
 *   - User owns ≥ 2 copies AND other user owns 0 copies (Foil and Normal counted separately)
 *   - EUR price ≥ minCardValue (default 0.50)
 *   - Pool truncated to top 300 cards by price if larger
 *
 * Both pools are computed across all sets in the request, then matched.
 */
@RestController
@RequestMapping("/api/compare")
public class TradeWizardController {

    private static final Logger log = LoggerFactory.getLogger(TradeWizardController.class);

    /** App-level users allowed in trade requests. */
    private static final Set<String> VALID_APP_USERS = Set.of("Victor", "Andre");

    private final TradeWizardService tradeWizardService;

    public TradeWizardController(TradeWizardService tradeWizardService) {
        this.tradeWizardService = tradeWizardService;
    }

    @PostMapping("/trade-wizard")
    public ResponseEntity<?> computeTrade(@Valid @RequestBody TradeWizardRequest req) {

        // ── Domain validation ───────────────────────────────────────────
        if (!VALID_APP_USERS.contains(req.userA())) {
            return badRequest("userA must be one of " + VALID_APP_USERS + " but was '" + req.userA() + "'");
        }
        if (!VALID_APP_USERS.contains(req.userB())) {
            return badRequest("userB must be one of " + VALID_APP_USERS + " but was '" + req.userB() + "'");
        }
        if (req.userA().equals(req.userB())) {
            return badRequest("userA and userB must differ");
        }

        // ── Build pools ─────────────────────────────────────────────────
        double minValue = req.minCardValue() != null ? req.minCardValue() : 0.50;

        List<SkippedCard> skippedA = new ArrayList<>();
        List<SkippedCard> skippedB = new ArrayList<>();

        List<TradeCard> poolA = tradeWizardService.buildPool(req.userA(), req.userB(), req.sets(), minValue, skippedA);
        List<TradeCard> poolB = tradeWizardService.buildPool(req.userB(), req.userA(), req.sets(), minValue, skippedB);

        List<String> notes = new ArrayList<>();
        if (poolA.size() >= TradeWizardService.MAX_POOL_SIZE) {
            notes.add("Pool for " + req.userA() + " truncated to top " + TradeWizardService.MAX_POOL_SIZE + " by price");
        }
        if (poolB.size() >= TradeWizardService.MAX_POOL_SIZE) {
            notes.add("Pool for " + req.userB() + " truncated to top " + TradeWizardService.MAX_POOL_SIZE + " by price");
        }

        // ── Match ───────────────────────────────────────────────────────
        TradeWizardResponse response;
        if ("greedy".equals(req.mode())) {
            TradeMatchResult result = tradeWizardService.greedyMatch(poolA, poolB, req.tolerancePercent());
            double sumA = result.pairs().stream().mapToDouble(p -> p.fromA().price()).sum();
            double sumB = result.pairs().stream().mapToDouble(p -> p.fromB().price()).sum();
            double fairness = tradeWizardService.computeFairnessScore(sumA, sumB);

            // Merge service-skipped (no_match_in_tolerance) with build-skipped (no_price etc.)
            skippedA.addAll(result.skippedA());
            skippedB.addAll(result.skippedB());

            response = new TradeWizardResponse(
                    "greedy", result.pairs(), null,
                    sumA, sumB, Math.abs(sumA - sumB), fairness,
                    skippedA, skippedB, notes
            );
        } else {  // "bundle"
            TradeBundleResult result = tradeWizardService.karmarkarKarpMatch(poolA, poolB);
            double sumA = tradeWizardService.sumPrices(result.bundle().aSide());
            double sumB = tradeWizardService.sumPrices(result.bundle().bSide());
            double fairness = tradeWizardService.computeFairnessScore(sumA, sumB);

            skippedA.addAll(result.skippedA());
            skippedB.addAll(result.skippedB());

            response = new TradeWizardResponse(
                    "bundle", List.of(), result.bundle(),
                    sumA, sumB, Math.abs(sumA - sumB), fairness,
                    skippedA, skippedB, notes
            );
        }

        return ResponseEntity.ok(response);
    }

    // ── Exception handlers ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Bad trade-wizard request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    private ResponseEntity<ProblemDetail> badRequest(String detail) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }
}
