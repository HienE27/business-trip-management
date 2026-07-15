package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for constraint plugins.
 * 
 * <p>Manages all constraints and provides methods to:
 * <ul>
 *   <li>Calculate total delta from all applicable constraints</li>
 *   <li>Validate a solution against all constraints</li>
 *   <li>Filter constraints by type</li>
 * </ul>
 */
@Getter
public class ConstraintRegistry {

    private final List<Constraint> allConstraints;
    private final List<Constraint> hardConstraints;
    private final List<Constraint> softConstraints;

    public ConstraintRegistry() {
        this.allConstraints = new ArrayList<>();
        this.hardConstraints = new ArrayList<>();
        this.softConstraints = new ArrayList<>();
    }

    /**
     * Create registry with initial constraints.
     */
    public ConstraintRegistry(List<Constraint> constraints) {
        this();
        for (Constraint c : constraints) {
            register(c);
        }
    }

    /**
     * Register a constraint.
     */
    public void register(Constraint constraint) {
        allConstraints.add(constraint);
        if (constraint.type() == Constraint.Type.HARD) {
            hardConstraints.add(constraint);
        } else {
            softConstraints.add(constraint);
        }
    }

    /**
     * Get all constraints.
     */
    public List<Constraint> getAllConstraints() {
        return List.copyOf(allConstraints);
    }

    /**
     * Get only hard constraints.
     */
    public List<Constraint> getHardConstraints() {
        return List.copyOf(hardConstraints);
    }

    /**
     * Get only soft constraints.
     */
    public List<Constraint> getSoftConstraints() {
        return List.copyOf(softConstraints);
    }

    /**
     * Get constraints applicable to a move.
     */
    public List<Constraint> getApplicable(Move move) {
        List<Constraint> applicable = new ArrayList<>();
        for (Constraint c : allConstraints) {
            if (c.isApplicable(move)) {
                applicable.add(c);
            }
        }
        return applicable;
    }

    /**
     * Calculate total delta from all applicable constraints.
     */
    public ScoreDelta calculateDelta(Move move, WorkingSolution solution) {
        ScoreDelta.Builder totalDelta = ScoreDelta.builder();

        for (Constraint c : allConstraints) {
            if (c.isApplicable(move)) {
                ScoreDelta delta = c.calculateDelta(move, solution);
                if (delta != null) {
                    totalDelta.add(delta);
                }
            }
        }

        return totalDelta.build();
    }

    /**
     * Validate a solution against all constraints.
     */
    public ValidationResult validate(WorkingSolution solution) {
        int hardCount = 0;
        int softCount = 0;
        List<Violation> violations = new ArrayList<>();

        for (Constraint c : hardConstraints) {
            ViolationResult result = c.validate(solution);
            if (result != null) {
                hardCount += result.count();
                violations.addAll(result.violations());
            }
        }

        for (Constraint c : softConstraints) {
            ViolationResult result = c.validate(solution);
            if (result != null) {
                softCount += result.count();
                violations.addAll(result.violations());
            }
        }

        return new ValidationResult(hardCount, softCount, violations);
    }

    /**
     * Create default registry with BR-01 to BR-07 constraints.
     */
    public static ConstraintRegistry createDefault() {
        ConstraintRegistry registry = new ConstraintRegistry();

        registry.register(new ShiftConflictConstraint());    // BR-01, BR-02
        registry.register(new RestDayConstraint());        // BR-03
        registry.register(new AdjacentL01Constraint());    // BR-04
        registry.register(new LeaveConflictConstraint());  // BR-05
        registry.register(new MaxShiftsConstraint());      // BR-06
        registry.register(new DuplicateShiftConstraint()); // BR-07

        return registry;
    }

    /**
     * Validation result for a solution.
     */
    public record ValidationResult(
            int hardCount,
            int softCount,
            List<Violation> violations
    ) {
        public int getHardCount() { return hardCount; }
        public int getSoftCount() { return softCount; }
        public int getTotalCount() { return hardCount + softCount; }
        public boolean isFeasible() { return hardCount == 0; }
    }

    /**
     * Constraint violation details.
     */
    public record Violation(
            String constraintName,
            Constraint.Type type,
            int staffId,
            String staffName,
            String date,
            String description
    ) {}

    /**
     * Result of validating a single constraint.
     */
    public record ViolationResult(int count, List<Violation> violations) {}

    /**
     * Validate a constraint against a solution.
     */
    default ViolationResult validate(WorkingSolution solution) {
        // Default implementation - override in concrete constraints
        return new ViolationResult(0, List.of());
    }
}
