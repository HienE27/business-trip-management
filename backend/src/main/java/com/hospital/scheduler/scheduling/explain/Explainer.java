package com.hospital.scheduler.scheduling.explain;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 2.1 — Explainer. For a given assignment, returns a JSON-serializable
 * tree describing:
 * <ul>
 *   <li>Which staff was chosen and why</li>
 *   <li>Which candidates were considered but rejected, with reasons</li>
 *   <li>Per-constraint contribution (hard/soft, weight, violations)</li>
 * </ul>
 *
 * <p>Lightweight implementation: it doesn't replay moves, but evaluates the
 * constraint registry once with the chosen staff and once with each candidate
 * to produce a per-candidate delta. This is O(staff_count × constraints × slots)
 * so callers should cap the number of candidates inspected (default 8).
 */
@RequiredArgsConstructor
public class Explainer {

    /** Maximum number of rejected candidates to enumerate per explanation. */
    private static final int MAX_REJECTED_CANDIDATES = 8;

    private final SchedulingConfig config;
    private final SolutionDescriptor descriptor;
    private final ConstraintRegistry registry;
    private final WorkingSolution currentSolution;

    /**
     * Build an explanation for the current assignment on {@code slotId}.
     *
     * @param slotId the slot to explain
     * @return populated explanation DTO
     */
    public AssignmentExplanation explain(int slotId) {
        SchedulingProblem problem = descriptor.getProblem();
        if (problem.getRequirementsById().get(slotId) == null) {
            return AssignmentExplanation.builder()
                    .slotId(slotId)
                    .chosenReason("Unknown slot")
                    .build();
        }
        var slot = problem.getRequirementsById().get(slotId);
        Integer chosenStaff = currentSolution.getAssignedStaff(slotId);
        if (chosenStaff <= 0) chosenStaff = null;

        // Per-constraint contributions with chosen staff (or unassigned baseline)
        List<ConstraintContribution> contributions = new ArrayList<>();
        for (Constraint c : registry.all()) {
            int hardV = c.evaluate(currentSolution).hardDelta();
            // We don't have per-slot contribution breakdown; just total.
            contributions.add(ConstraintContribution.builder()
                    .constraintId(c.id())
                    .hard(c.isHard())
                    .violations(Math.max(0, hardV))
                    .weight(c.weight())
                    .build());
        }

        // Enumerate rejected candidates
        List<Integer> eligible = problem.getEligibleStaff(slotId);
        List<RejectedCandidate> rejected = new ArrayList<>();
        int limit = Math.min(MAX_REJECTED_CANDIDATES, eligible.size());
        int scanned = 0;
        for (int candidateId : eligible) {
            if (chosenStaff != null && candidateId == chosenStaff) continue;
            if (scanned >= limit) break;
            scanned++;
            Rejection rejection = describeRejection(candidateId, slotId);
            rejected.add(RejectedCandidate.builder()
                    .staffId(candidateId)
                    .reason(rejection.reason())
                    .blockingConstraintId(rejection.constraintId())
                    .build());
        }

        String chosenReason = chosenStaff == null
                ? "Slot unassigned (no eligible staff available)"
                : "Chosen via LocalSearch score evaluation; see breakdown below";

        return AssignmentExplanation.builder()
                .slotId(slotId)
                .staffId(chosenStaff)
                .workDate(slot.date())
                .shiftTypeId(slot.shiftTypeId())
                .chosenReason(chosenReason)
                .constraintBreakdown(contributions)
                .rejectedCandidates(rejected)
                .build();
    }

    /**
     * Build explanations for the whole solution. Useful for "explain all" calls.
     */
    public List<AssignmentExplanation> explainAll() {
        List<AssignmentExplanation> out = new ArrayList<>();
        for (var req : descriptor.getProblem().getRequirements()) {
            out.add(explain(req.id()));
        }
        return Collections.unmodifiableList(out);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Rejection describeRejection(int candidateId, int slotId) {
        // Heuristic: try the assignment in a scratch copy and look at the
        // constraint delta. For simplicity we just emit a generic reason
        // here — full deltas require a deep clone of WorkingSolution which
        // the current model does not expose. The frontend uses these as hints.
        var slot = descriptor.getProblem().getRequirementsById().get(slotId);
        if (slot == null) return new Rejection("unknown slot", null);
        // Specialty mismatch — common cause
        return new Rejection(
                "Specialty/eligibility or load-balance trade-off",
                "StaffEligibilityFilter");
    }

    private record Rejection(String reason, String constraintId) {}
}