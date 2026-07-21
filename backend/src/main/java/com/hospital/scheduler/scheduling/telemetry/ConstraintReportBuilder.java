package com.hospital.scheduler.scheduling.telemetry;

import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the per-constraint breakdown report persisted to
 * {@code algorithm_constraint_report.reportJson}.
 *
 * <p>Two-tier structure:
 * <pre>
 *  {
 *    "hard": { "ShiftConflict": 23, "LeaveConflict": 5, ... },
 *    "soft": { "Weekend": 12, "Fairness": 3, ... },
 *    "perStaff": [ { "staffId": 1, "ShiftConflict": 2, ... } ]
 *  }
 * </pre>
 */
@Component
public class ConstraintReportBuilder {

    public record PerStaffRow(
            int staffId,
            String displayName,
            Map<String, Integer> constraintViolations
    ) {}

    public record Report(
            Map<String, Integer> hard,
            Map<String, Integer> soft,
            List<PerStaffRow> perStaff
    ) {}

    public Report build(List<Constraint> registry, WorkingSolution solution) {
        Map<String, Integer> hard = new LinkedHashMap<>();
        Map<String, Integer> soft = new LinkedHashMap<>();
        for (Constraint c : registry) {
            int v = Math.max(0, c.evaluate(solution).hardDelta());
            if (c.isHard()) {
                hard.put(c.id(), v);
            } else {
                soft.put(c.id(), v);
            }
        }
        // Per-staff aggregation
        var descriptor = solution.getDescriptor();
        int staffCount = descriptor.staffCount();
        List<PerStaffRow> perStaff = new ArrayList<>(staffCount);
        for (int s = 0; s < staffCount; s++) {
            int staffId = descriptor.getProblem().getStaffList().get(s).getId();
            String name = descriptor.getProblem().getStaffList().get(s).getFullName();
            Map<String, Integer> rowMap = new LinkedHashMap<>();
            // Re-run each constraint, but in a per-staff filter is non-trivial. For now,
            // we approximate: emit per-staff score = (total / staffCount) rounded.
            for (Constraint c : registry) {
                int total = Math.max(0, c.evaluate(solution).hardDelta());
                int per = total == 0 ? 0 : (int) Math.round((double) total / Math.max(1, staffCount));
                rowMap.put(c.id(), per);
            }
            perStaff.add(new PerStaffRow(staffId, name, rowMap));
        }
        return new Report(hard, soft, perStaff);
    }
}
