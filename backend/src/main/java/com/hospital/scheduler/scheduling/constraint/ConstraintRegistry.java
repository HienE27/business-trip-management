package com.hospital.scheduler.scheduling.constraint;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Registry of {@link Constraint} plugins.
 *
 * <p>Constraints are registered in priority order (hard before soft) and
 * evaluated in sequence by the search loop. Each constraint can be
 * individually enabled/disabled via configuration.
 */
@Getter
@Component
public class ConstraintRegistry {

    private final List<Constraint> constraints = new ArrayList<>();

    /** Register a constraint. */
    public void register(Constraint constraint) {
        constraints.add(constraint);
    }

    /** All registered constraints (read-only view). */
    public List<Constraint> all() {
        return List.copyOf(constraints);
    }

    /** Number of registered constraints. */
    public int size() {
        return constraints.size();
    }
}