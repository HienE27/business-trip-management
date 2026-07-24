package com.hospital.scheduler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Commit B: balance_score_min is a soft gate on final fairness (0–100).
 * Config stores 0.0–1.0 (0.70 → 70). Never rejects — only threshold math.
 */
@DisplayName("balance_score_min soft threshold (Commit B)")
class BalanceScoreMinThresholdTest {

    @Test
    @DisplayName("canonical 0.70 → 70.00 percent")
    void toPercent_canonicalFraction() {
        assertThat(AutoSchedulingService.toBalanceThresholdPercent(new BigDecimal("0.70")))
                .isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("null config → default 70")
    void toPercent_nullDefaultsTo70() {
        assertThat(AutoSchedulingService.toBalanceThresholdPercent(null))
                .isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("mis-seeded percent value >1 is kept as percent")
    void toPercent_alreadyPercent() {
        assertThat(AutoSchedulingService.toBalanceThresholdPercent(new BigDecimal("75")))
                .isEqualByComparingTo("75.00");
    }

    @ParameterizedTest(name = "score={0} min={1} → below={2}")
    @CsvSource({
            "69.99, 0.70, true",
            "70.00, 0.70, false",
            "85.00, 0.70, false",
            "50.00, 0.60, true",
            "60.00, 0.60, false",
            "0.00,  0.70, true",
            "100.00, 0.70, false",
            "69.00, 70, true",
            "70.00, 70, false"
    })
    void isBelow_matrix(String score, String min, boolean expected) {
        assertThat(AutoSchedulingService.isBelowBalanceThreshold(
                new BigDecimal(score), new BigDecimal(min)))
                .as("score=%s min=%s", score, min)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("null score never flags below")
    void isBelow_nullScore() {
        assertThat(AutoSchedulingService.isBelowBalanceThreshold(null, new BigDecimal("0.70")))
                .isFalse();
    }
}
