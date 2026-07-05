package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SchedulingResult} and {@link ShiftRequirementInfo}
 * value semantics that underpin the CSP / GA dispatch layer.
 */
@DisplayName("Algorithm data-layer invariants")
class SchedulingResultTest {

    @Test
    @DisplayName("SchedulingResult.addAssignment(key) round-trips via getAssignment")
    void assignmentRoundTrip() {
        SchedulingResult result = SchedulingResult.builder().build();
        result.addAssignment(1, LocalDate.of(2026, 7, 1), "L01");
        result.addAssignment(1, LocalDate.of(2026, 7, 2), "L02");

        assertThat(result.getAssignment(1, LocalDate.of(2026, 7, 1))).isEqualTo("L01");
        assertThat(result.getAssignment(1, LocalDate.of(2026, 7, 2))).isEqualTo("L02");
        assertThat(result.getAssignment(1, LocalDate.of(2026, 7, 3))).isNull();
    }

    @Test
    @DisplayName("addCompensationDay + isCompensationDay symmetry")
    void compensationDayRoundTrip() {
        SchedulingResult result = SchedulingResult.builder().build();
        result.addCompensationDay(7, LocalDate.of(2026, 7, 5));
        assertThat(result.isCompensationDay(7, LocalDate.of(2026, 7, 5))).isTrue();
        assertThat(result.isCompensationDay(7, LocalDate.of(2026, 7, 6))).isFalse();
        assertThat(result.isCompensationDay(8, LocalDate.of(2026, 7, 5))).isFalse();
    }

    @Test
    @DisplayName("ShiftRequirementInfo exposes record components readably")
    void requirementInfoReadout() {
        ShiftRequirementInfo info = new ShiftRequirementInfo(
                "L03", LocalDate.of(2026, 7, 4), 2);
        assertThat(info.shiftTypeId()).isEqualTo("L03");
        assertThat(info.workDate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(info.requiredCount()).isEqualTo(2);
    }
}
