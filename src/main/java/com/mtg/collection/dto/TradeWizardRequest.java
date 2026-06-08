package com.mtg.collection.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Request body for POST /api/compare/trade-wizard.
 *
 * @param userA            App-User A ("Victor" or "Andre")
 * @param userB            App-User B ("Victor" or "Andre"), must differ from A (validated in service)
 * @param sets             Set codes to consider (≥1)
 * @param mode             "greedy" or "bundle"
 * @param tolerancePercent Allowed price diff in % (0–100), only relevant for greedy
 * @param minCardValue     Minimum EUR price per card to qualify for trading pool
 */
public record TradeWizardRequest(
        @NotBlank
        String userA,

        @NotBlank
        String userB,

        @NotEmpty
        List<@NotBlank String> sets,

        @NotBlank
        @Pattern(regexp = "greedy|bundle", message = "mode must be 'greedy' or 'bundle'")
        String mode,

        @DecimalMin(value = "0.0", message = "tolerancePercent must be ≥ 0")
        @DecimalMax(value = "100.0", message = "tolerancePercent must be ≤ 100")
        double tolerancePercent,

        @DecimalMin(value = "0.0", message = "minCardValue must be ≥ 0")
        Double minCardValue
) {}
