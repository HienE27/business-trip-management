package com.hospital.scheduler.scheduling.explain;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2.1 service — produces explanations for a scheduling result.
 *
 * <p>This is a thin orchestration layer that wires the {@link Explainer} to
 * the database (period → staff → requirements) and returns the explanation
 * tree for the frontend {@code /explain} panel.
 */
@Service
@RequiredArgsConstructor
public class ExplainService {

    private final SchedulingConfig config;
    private final ConstraintRegistry registry;

    /**
     * Explain the assignment for one slot in a period.
     *
     * <p>The current implementation rebuilds a synthetic working solution
     * from the JPA entities (since the v10 scheduler does not persist its
     * working state — that is part of Phase 2.5's replay log).
     */
    public AssignmentExplanation explainPeriodSlot(int periodId, int slotId,
                                                   List<Staff> staffList,
                                                   List<LocalDate> periodDates,
                                                   java.util.function.BiFunction<Integer, LocalDate, Integer> currentAssignmentLookup) {
        // Build a minimal working solution covering periodDates × types so the
        // explainer can iterate constraints.
        List<com.hospital.scheduler.entity.ShiftRequirement> reqs = new ArrayList<>();
        String[] types = {"L01", "L02", "L03", "L04"};
        int i = 1;
        for (LocalDate d : periodDates) {
            for (String t : types) {
                com.hospital.scheduler.entity.ShiftRequirement sr =
                        new com.hospital.scheduler.entity.ShiftRequirement();
                sr.setId(i++);
                sr.setWorkDate(d);
                com.hospital.scheduler.entity.ShiftType st = new com.hospital.scheduler.entity.ShiftType();
                st.setId(t);
                sr.setShiftType(st);
                sr.setRequiredStaffCount(1);
                reqs.add(sr);
            }
        }
        SchedulingProblem problem = SchedulingProblem.from(
                staffList, reqs, new ArrayList<>(), new ArrayList<>(),
                new java.util.HashSet<>(), config);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        SolutionDescriptor wired = new SolutionDescriptor(problem, hub);
        WorkingSolution sol = WorkingSolution.fromProblem(config, wired);

        // Hydrate the working solution from the lookup (slotId → staffId)
        for (var req : reqs) {
            Integer assigned = currentAssignmentLookup.apply(req.getId(), req.getWorkDate());
            if (assigned != null && assigned > 0) {
                sol.assign(req.getId(), assigned);
            }
        }

        Explainer explainer = new Explainer(config, wired, registry, sol);
        return explainer.explain(slotId);
    }
}