package com.hospital.scheduler.scheduling.domain;

import lombok.*;

/**
 * Constraint evaluation result.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConstraintResult {
    private String constraintId;
    private String constraintName;
    private boolean satisfied;
    private double penalty;
    private String message;
}
