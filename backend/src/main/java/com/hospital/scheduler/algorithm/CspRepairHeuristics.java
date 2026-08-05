package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;

/**
 * Heuristic helpers for the repair pass in {@link CspIncrementalResolver}.
 * Encapsulates three improvements over the original arbitrary-order repair:
 *
 * <ol>
 *   <li><b>Soft constraint relaxation</b> — when a repair is stuck (no eligible
 *       staff passes hard constraints), BR05 (fairness) is temporarily relaxed
 *       so the repair step can use any eligible staff.</li>
 *   <li><b>Retry with rotation</b> — instead of failing when the preferred
 *       candidate is blocked, the search rotates through the staff list
 *       (index + 1, + 2, … up to {@value #MAX_ROTATION_RETRIES} retries).</li>
 *   <li><b>Minimum-workload tie-break</b> — candidates are sorted by current
 *       total assignments so staff with more slack get priority.</li>
 * </ol>
 *
 * <p>All three features only affect <i>soft</i> constraint handling during
 * repair. Hard constraints (BR01/BR02, BR03, BR04, quota, specialty
 * eligibility) are NEVER relaxed.
 */
public final class CspRepairHeuristics {

    /** Maximum number of rotation retries when the preferred staff is blocked. */
    public static final int MAX_ROTATION_RETRIES = 3;

    private CspRepairHeuristics() {}

    /**
     * Result of a repair attempt for a single slot.
     */
    @Builder
    @Getter
    public static class RepairResult {
        /** The staffId that should fill this slot, or {@code null} if unrepairable. */
        private final Integer replacementStaffId;
        /** True when the repair succeeded. */
        private final boolean repaired;
        /** True when fairness was relaxed to achieve the repair. */
        private final boolean fairnessRelaxed;
        /** Number of rotation retries used (0 = first attempt succeeded). */
        private final int retriesUsed;
        /** Human-readable reason if repair failed. */
        private final String failureReason;

        /**
         * Convenience accessor matching the test contract. Lombok's
         * {@code @Getter} generates {@link #isRepaired()} for a boolean field,
         * but callers (and the test suite) expect the bare {@code repaired()}
         * name as well.
         */
        public boolean repaired() {
            return repaired;
        }

        public boolean isRepaired() {
            return repaired;
        }

        public static RepairResult success(int staffId, boolean fairnessRelaxed, int retries) {
            return RepairResult.builder()
                    .replacementStaffId(staffId)
                    .repaired(true)
                    .fairnessRelaxed(fairnessRelaxed)
                    .retriesUsed(retries)
                    .build();
        }

        public static RepairResult failure(String reason) {
            return RepairResult.builder()
                    .replacementStaffId(null)
                    .repaired(false)
                    .fairnessRelaxed(false)
                    .retriesUsed(0)
                    .failureReason(reason)
                    .build();
        }

        public static RepairResult stuck() {
            return stuck(MAX_ROTATION_RETRIES);
        }

        public static RepairResult stuck(int retriesUsed) {
            return RepairResult.builder()
                    .replacementStaffId(null)
                    .repaired(false)
                    .fairnessRelaxed(false)
                    .retriesUsed(retriesUsed)
                    .failureReason("No eligible staff available after " + retriesUsed + " retries")
                    .build();
        }
    }

    /**
     * Attempt to repair a single unassigned or conflicted slot using the
     * three-phase heuristic:
     *
     * <ol>
     *   <li>Phase 1 — Normal: find the lowest-workload eligible staff, respecting
     *       all hard constraints + fairness (BR05).</li>
     *   <li>Phase 2 — Relaxed fairness: if Phase 1 finds no candidate, retry
     *       ignoring BR05 but still enforcing all hard constraints.</li>
     *   <li>Phase 3 — Rotation: if Phase 2 finds no candidate, retry with
     *       index rotation (skip preferred, try next eligible up to
     *       {@value #MAX_ROTATION_RETRIES} times).</li>
     * </ol>
     *
     * @param varKey              the varKey being repaired (date|shiftType)
     * @param date                work date
     * @param shiftType           shift type id (L01–L04)
     * @param excludeStaffId      staff currently assigned to this slot (to be replaced)
     * @param eligibleStaffList   all eligible staff for this slot (from domain)
     * @param staffWorkload       map: staffId → total assignments so far
     * @param fairnessCheck       custom fairness predicate (staffId → true = passes BR05)
     * @param hardConstraintCheck custom hard-constraint checker
     * @return                    a {@link RepairResult}
     */
    public static RepairResult attemptRepair(
            String varKey,
            LocalDate date,
            String shiftType,
            Integer excludeStaffId,
            List<Staff> eligibleStaffList,
            Map<Integer, Integer> staffWorkload,
            java.util.function.Predicate<Integer> fairnessCheck,
            java.util.function.BiFunction<Integer, Integer, String> hardConstraintCheck) {

        // ── Phase 1: Normal (respect all constraints including BR05) ─────────────
        RepairResult phase1 = findBestCandidate(
                varKey, date, shiftType, excludeStaffId,
                eligibleStaffList, staffWorkload,
                fairnessCheck, hardConstraintCheck,
                false, 0);
        if (phase1.isRepaired()) {
            return phase1;
        }

        // ── Phase 2: Relaxed fairness (suspend BR05 only) ───────────────────────
        RepairResult phase2 = findBestCandidate(
                varKey, date, shiftType, excludeStaffId,
                eligibleStaffList, staffWorkload,
                s -> true,    // always pass fairness
                hardConstraintCheck,
                true, 0);     // fairnessRelaxed=true, no rotation
        if (phase2.isRepaired()) {
            return phase2;
        }

        // ── Phase 3: Rotation retry (skip preferred, try next eligible) ──────────
        return rotateRetry(
                varKey, date, shiftType, excludeStaffId,
                eligibleStaffList, staffWorkload,
                hardConstraintCheck);
    }

    /**
     * Phase 1 & 2 implementation: find the lowest-workload eligible staff that
     * passes all constraints.
     *
     * @param relaxFairness   if true, ignore the fairnessCheck
     * @param rotationOffset  starting offset into the candidate list (0 = no rotation)
     */
    private static RepairResult findBestCandidate(
            String varKey,
            LocalDate date,
            String shiftType,
            Integer excludeStaffId,
            List<Staff> eligibleStaffList,
            Map<Integer, Integer> staffWorkload,
            java.util.function.Predicate<Integer> fairnessCheck,
            java.util.function.BiFunction<Integer, Integer, String> hardConstraintCheck,
            boolean relaxFairness,
            int rotationOffset) {

        // Sort eligible staff by workload (ascending) for the repair pass
        // This prefers staff with more slack — better chance of acceptance
        List<Staff> sorted = new ArrayList<>(eligibleStaffList);
        sorted.sort(Comparator.comparingInt(
                (Staff s) -> staffWorkload.getOrDefault(s.getId(), 0)));

        // Apply rotation offset (skip the first `rotationOffset` candidates)
        int startIdx = Math.min(rotationOffset, sorted.size());

        for (int i = startIdx; i < sorted.size(); i++) {
            Staff candidate = sorted.get(i);
            int candidateId = candidate.getId();

            if (excludeStaffId != null && candidateId == excludeStaffId) continue;

            // Hard constraint check (BR01/BR02, BR03, BR04, quota, specialty)
            String hardFailure = hardConstraintCheck.apply(candidateId, staffWorkload.getOrDefault(candidateId, 0));
            if (hardFailure != null) continue;

            // Fairness check (BR05) — skip if relaxed
            if (!relaxFairness && !fairnessCheck.test(candidateId)) continue;

            int retriesUsed = i - startIdx; // how many we skipped before finding this one
            return RepairResult.success(candidateId, relaxFairness, retriesUsed);
        }

        return RepairResult.stuck();
    }

    /**
     * Phase 3: rotate through the staff list, trying staff at positions
     * (startIndex + 1), (startIndex + 2), … up to MAX_ROTATION_RETRIES.
     *
     * <p>This handles the case where the lowest-workload staff is blocked by
     * an as-yet-unpropagated assignment, but a slightly higher-workload staff
     * is available.  The rotation prevents the repair from failing just because
     * it always picks the same blocked candidate.
     */
    private static RepairResult rotateRetry(
            String varKey,
            LocalDate date,
            String shiftType,
            Integer excludeStaffId,
            List<Staff> eligibleStaffList,
            Map<Integer, Integer> staffWorkload,
            java.util.function.BiFunction<Integer, Integer, String> hardConstraintCheck) {

        int lastRetryUsed = 0;
        for (int retry = 1; retry <= MAX_ROTATION_RETRIES; retry++) {
            lastRetryUsed = retry;
            // Sort fresh each rotation so we don't keep retrying the same blocked staff
            List<Staff> sorted = new ArrayList<>(eligibleStaffList);
            sorted.sort(Comparator.comparingInt(
                    (Staff s) -> staffWorkload.getOrDefault(s.getId(), 0)));

            // Skip the first `retry` candidates (they were tried and blocked)
            for (int i = retry; i < sorted.size(); i++) {
                Staff candidate = sorted.get(i);
                int candidateId = candidate.getId();

                if (excludeStaffId != null && candidateId == excludeStaffId) continue;

                String hardFailure = hardConstraintCheck.apply(
                        candidateId, staffWorkload.getOrDefault(candidateId, 0));
                if (hardFailure != null) continue;

                // No fairness check in rotation phase — any hard-eligible staff is fine
                return RepairResult.success(candidateId, false, retry);
            }
        }

        return RepairResult.stuck(lastRetryUsed);
    }

    /**
     * Estimate the demand scarcity tier for a shift type.
     * Used by the repair queue manager to prioritize scarce shift types first.
     *
     * @param eligibleCount number of eligible staff for this shift type
     * @return tier 0 (critical) … 3 (abundant)
     */
    public static int computeScarcityTier(int eligibleCount) {
        return eligibleCount <= 1 ? 0
                : eligibleCount == 2 ? 1
                : eligibleCount <= 5 ? 2 : 3;
    }
}
