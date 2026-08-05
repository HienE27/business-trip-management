package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.hospital.scheduler.algorithm.CspRepairHeuristics.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OPT-002 repair pass improvements:
 * CspRepairHeuristics and CspRepairQueueManager.
 *
 * <p>Tests cover:
 * <ol>
 *   <li>Priority score ordering (scarcity → streak → slack).</li>
 *   <li>3-phase repair heuristic: normal, relaxed fairness, rotation retry.</li>
 *   <li>Hard constraint never being relaxed.</li>
 *   <li>Retry-with-rotation capping at MAX_ROTATION_RETRIES.</li>
 * </ol>
 */
class CspRepairHeuristicsTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 8, 3);

    private Staff staff(int id) {
        return Staff.builder().id(id).build();
    }

    // ── CspRepairHeuristics Tests ──────────────────────────────────────────────

    @Nested
    class AttemptRepair {

        @Test
        void succeedsOnFirstCandidate() {
            List<Staff> pool = List.of(staff(1), staff(2), staff(3));
            Map<Integer, Integer> workload = Map.of(1, 5, 2, 8, 3, 3);
            Predicate<Integer> alwaysFair = s -> true;
            BiFunction<Integer, Integer, String> noHard = (s, w) -> null;

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", null,
                    pool, workload, alwaysFair, noHard);

            assertTrue(result.repaired());
            assertFalse(result.isFairnessRelaxed());
            assertEquals(0, result.getRetriesUsed());
            // Staff 3 has lowest workload (3) — should be picked
            assertEquals(3, result.getReplacementStaffId());
        }

        @Test
        void skipsExcludedStaff() {
            List<Staff> pool = List.of(staff(1), staff(2));
            Map<Integer, Integer> workload = Map.of(1, 1, 2, 2);
            Predicate<Integer> alwaysFair = s -> true;
            BiFunction<Integer, Integer, String> noHard = (s, w) -> null;

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", 1,
                    pool, workload, alwaysFair, noHard);

            assertTrue(result.repaired());
            assertEquals(2, result.getReplacementStaffId());
        }

        @Test
        void phase1Fails_phase2RelaxedSucceeds() {
            List<Staff> pool = List.of(staff(1), staff(2));
            Map<Integer, Integer> workload = Map.of(1, 10, 2, 20);
            // Fairness: only staff 1 passes (below threshold)
            Predicate<Integer> strictFair = s -> s == 1;
            // Hard constraints: only staff 2 passes
            BiFunction<Integer, Integer, String> onlyStaff2Passes = (s, w) ->
                    s == 2 ? null : "Staff 1 hard blocked";

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", null,
                    pool, workload, strictFair, onlyStaff2Passes);

            assertTrue(result.repaired());
            assertTrue(result.isFairnessRelaxed()); // Phase 2: fairness was relaxed
            assertEquals(2, result.getReplacementStaffId());
        }

        @Test
        void hardConstraintNeverRelaxed() {
            List<Staff> pool = List.of(staff(1), staff(2));
            Map<Integer, Integer> workload = Map.of(1, 1, 2, 1);
            Predicate<Integer> alwaysFair = s -> true;
            // Both staff fail hard constraints
            BiFunction<Integer, Integer, String> bothFail = (s, w) -> "Hard conflict";

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", null,
                    pool, workload, alwaysFair, bothFail);

            assertFalse(result.repaired());
            assertNotNull(result.getFailureReason());
        }

        @Test
        void rotationRetryFindsAlternative() {
            List<Staff> pool = List.of(staff(1), staff(2), staff(3));
            Map<Integer, Integer> workload = Map.of(1, 1, 2, 1, 3, 1);
            Predicate<Integer> alwaysFair = s -> true;
            // Staff 1 and 2 fail, staff 3 passes
            BiFunction<Integer, Integer, String> skipFirstTwo = (s, w) ->
                    s <= 2 ? "Blocked" : null;

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", null,
                    pool, workload, alwaysFair, skipFirstTwo);

            assertTrue(result.repaired());
            assertEquals(3, result.getReplacementStaffId());
            assertTrue(result.getRetriesUsed() > 0); // Used rotation
        }

        @Test
        void rotationCappedAtMaxRetries() {
            // More candidates than MAX_ROTATION_RETRIES
            List<Staff> pool = new ArrayList<>();
            for (int i = 1; i <= 10; i++) pool.add(staff(i));
            Map<Integer, Integer> workload = new HashMap<>();
            for (int i = 1; i <= 10; i++) workload.put(i, 1);
            Predicate<Integer> alwaysFair = s -> true;
            // Only staff 1 passes — exclude it so the repair MUST rotate
            // through blocked alternatives and exhaust the retry budget.
            BiFunction<Integer, Integer, String> onlyStaff1 = (s, w) ->
                    s == 1 ? null : "Blocked";

            RepairResult result = attemptRepair(
                    "2026-08-03|L01", FIXED_DATE, "L01", 1 /* exclude staff 1 */,
                    pool, workload, alwaysFair, onlyStaff1);

            assertFalse(result.repaired());
            assertEquals(MAX_ROTATION_RETRIES, result.getRetriesUsed());
        }
    }

    @Nested
    class ScarcityTierComputation {

        @Test
        void tierCritical_whenSingleEligible() {
            assertEquals(0, computeScarcityTier(1));
        }

        @Test
        void tierTight_whenTwoEligible() {
            assertEquals(1, computeScarcityTier(2));
        }

        @Test
        void tierModerate_when3To5Eligible() {
            assertEquals(2, computeScarcityTier(3));
            assertEquals(2, computeScarcityTier(4));
            assertEquals(2, computeScarcityTier(5));
        }

        @Test
        void tierAbundant_when6OrMoreEligible() {
            assertEquals(3, computeScarcityTier(6));
            assertEquals(3, computeScarcityTier(100));
        }
    }

    @Nested
    class RepairResultFactory {

        @Test
        void successHasCorrectFields() {
            RepairResult r = RepairResult.success(42, true, 2);
            assertTrue(r.repaired());
            assertEquals(42, r.getReplacementStaffId());
            assertTrue(r.isFairnessRelaxed());
            assertEquals(2, r.getRetriesUsed());
            assertNull(r.getFailureReason());
        }

        @Test
        void failureHasCorrectFields() {
            RepairResult r = RepairResult.failure("No staff available");
            assertFalse(r.repaired());
            assertNull(r.getReplacementStaffId());
            assertFalse(r.isFairnessRelaxed());
            assertEquals(0, r.getRetriesUsed());
            assertEquals("No staff available", r.getFailureReason());
        }

        @Test
        void stuckHasCorrectReason() {
            RepairResult r = RepairResult.stuck();
            assertFalse(r.repaired());
            assertTrue(r.getFailureReason().contains("" + MAX_ROTATION_RETRIES));
        }
    }

    // ── CspRepairQueueManager Tests ─────────────────────────────────────────────

    @Nested
    class RepairQueueManager {

        @Test
        void buildsFromUnmetVarKeys() {
            Set<String> unmet = Set.of("2026-08-03|L01", "2026-08-04|L02");
            Map<String, Integer> varIndexMap = Map.of(
                    "2026-08-03|L01", 0,
                    "2026-08-04|L02", 1);
            Map<Integer, Integer> staffIndexMap = Map.of(1, 0, 2, 1);
            Map<String, String> assignments = Map.of();
            Map<String, Integer> eligibleCounts = Map.of("L01", 5, "L02", 2);

            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    unmet, varIndexMap, staffIndexMap, List.of(),
                    assignments, eligibleCounts, true);

            assertEquals(2, queue.remainingRepairs());
        }

        @Test
        void pollNextRepairReturnsEntry() {
            Set<String> unmet = Set.of("2026-08-03|L01");
            Map<String, Integer> varIndexMap = Map.of("2026-08-03|L01", 0);
            Map<Integer, Integer> staffIndexMap = Map.of(1, 0);
            Map<String, Integer> eligibleCounts = Map.of("L01", 2);

            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    unmet, varIndexMap, staffIndexMap, List.of(),
                    Map.of(), eligibleCounts, true);

            CspRepairQueueManager.RepairEntry entry = queue.pollNextRepair();
            assertNotNull(entry);
            assertEquals("2026-08-03|L01", entry.varKey());
            assertEquals(FIXED_DATE, entry.date());
            assertEquals("L01", entry.shiftType());
            assertEquals(2, entry.eligibleStaffCount());
            assertNotNull(entry.priority());
        }

        @Test
        void remainingRepairsDecrementsAfterPoll() {
            Set<String> unmet = Set.of("2026-08-03|L01", "2026-08-04|L02");
            Map<String, Integer> varIndexMap = Map.of(
                    "2026-08-03|L01", 0, "2026-08-04|L02", 1);
            Map<Integer, Integer> staffIndexMap = Map.of(1, 0);
            Map<String, Integer> eligibleCounts = Map.of("L01", 2, "L02", 2);

            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    unmet, varIndexMap, staffIndexMap, List.of(),
                    Map.of(), eligibleCounts, true);

            assertEquals(2, queue.remainingRepairs());
            queue.pollNextRepair();
            assertEquals(1, queue.remainingRepairs());
            queue.pollNextRepair();
            assertEquals(0, queue.remainingRepairs());
        }

        @Test
        void markRepairedRemovesEntry() {
            Set<String> unmet = Set.of("2026-08-03|L01");
            Map<String, Integer> varIndexMap = Map.of("2026-08-03|L01", 0);
            Map<Integer, Integer> staffIndexMap = Map.of(1, 0);
            Map<String, Integer> eligibleCounts = Map.of("L01", 2);

            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    unmet, varIndexMap, staffIndexMap, List.of(),
                    Map.of(), eligibleCounts, true);

            queue.pollNextRepair();
            queue.markRepaired("2026-08-03|L01");
            assertEquals(0, queue.remainingRepairs());
        }

        @Test
        void recordAssignmentUpdatesWorkload() {
            Set<String> unmet = Set.of("2026-08-03|L01");
            Map<String, Integer> varIndexMap = Map.of("2026-08-03|L01", 0);
            Map<Integer, Integer> staffIndexMap = Map.of(1, 0, 2, 1);
            Map<String, Integer> eligibleCounts = Map.of("L01", 2);

            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    unmet, varIndexMap, staffIndexMap, List.of(),
                    Map.of(), eligibleCounts, true);

            queue.recordAssignment(1);
            queue.recordAssignment(1);
            queue.recordAssignment(2);

            assertEquals(2, queue.getStaffWorkload().get(1));
            assertEquals(1, queue.getStaffWorkload().get(2));
        }

        @Test
        void shouldRelaxFairness_whenEnabledAndQueueNotEmpty() {
            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    Set.of("2026-08-03|L01"),
                    Map.of("2026-08-03|L01", 0),
                    Map.of(1, 0),
                    List.of(), Map.of(), Map.of("L01", 2), true);

            assertTrue(queue.shouldRelaxFairness());
        }

        @Test
        void shouldNotRelaxFairness_whenDisabled() {
            CspRepairQueueManager queue = CspRepairQueueManager.build(
                    Set.of("2026-08-03|L01"),
                    Map.of("2026-08-03|L01", 0),
                    Map.of(1, 0),
                    List.of(), Map.of(), Map.of("L01", 2), false);

            assertFalse(queue.shouldRelaxFairness());
        }
    }

    @Nested
    class PriorityScoreOrdering {

        // Use factory methods to avoid internal constructor visibility issues
        private CspRepairQueueManager.PriorityScore make(int scarcity, int streak, int slack, long seq) {
            // Access via the queue's priority map after build
            return new CspRepairQueueManager.PriorityScore(scarcity, streak, slack, seq);
        }

        @Test
        void scarceBeforeAbundant() {
            CspRepairQueueManager.PriorityScore scarce = make(0, 1, 5, 0);
            CspRepairQueueManager.PriorityScore abundant = make(3, 1, 5, 1);

            assertTrue(scarce.compareTo(abundant) < 0);
        }

        @Test
        void longerStreakBeforeShorterStreak() {
            CspRepairQueueManager.PriorityScore longStreak = make(1, 3, 5, 0);
            CspRepairQueueManager.PriorityScore shortStreak = make(1, 1, 5, 1);

            assertTrue(longStreak.compareTo(shortStreak) < 0);
        }

        @Test
        void lowerWorkloadWinsOnTiebreak() {
            CspRepairQueueManager.PriorityScore lowWorkload = make(1, 1, 2, 0);
            CspRepairQueueManager.PriorityScore highWorkload = make(1, 1, 8, 1);

            assertTrue(lowWorkload.compareTo(highWorkload) < 0);
        }

        @Test
        void fifoOnEqualScore() {
            CspRepairQueueManager.PriorityScore first = make(1, 1, 5, 10);
            CspRepairQueueManager.PriorityScore second = make(1, 1, 5, 20);

            assertTrue(first.compareTo(second) < 0); // first has lower sequence
        }
    }
}
