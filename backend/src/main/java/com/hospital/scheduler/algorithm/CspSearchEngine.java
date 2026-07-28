package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;

/**
 * Backtracking search with MRV heuristic, AC-3 propagation, and nogood
 * learning. Returns a {@link Result} that the orchestrator can hand to the
 * result builder.
 */
@Component
@RequiredArgsConstructor
class CspSearchEngine {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final CompensationDateCalculator compensationDateCalculator;
    private final CspNogoodStore nogoodStore;

    /** Internal search outcome. Used by {@link #search} so the caller can
     * distinguish a dead-end backtrack from a timeout-induced early exit —
     * on timeout we still keep the partial assignment so the user sees a
     * useful preview instead of a "no solution found" wall. */
    private enum SearchOutcome { FOUND, DEAD_END, TIMED_OUT }

    /**
     * Solve with the default 30s timeout (production / auto-schedule path).
     */
    Result solve(ProblemData data, long startTime) {
        return solve(data, startTime, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Solve with a caller-supplied timeout. Used by the preview path so
     * that preview responses come back fast (typically &lt; 8s) even when
     * the full run would have taken the full 30s budget. The preview
     * contract is "show a feasible plan quickly"; if a tighter timeout
     * exhausts the search the orchestrator still gets a partial / empty
     * result and can surface a "preview unavailable" message without
     * blocking the user for 30s.
     */
    Result solve(ProblemData data, long startTime, long timeoutMs) {
        BitSet[] domains = copyDomains(data);
        int[] assignment = new int[data.numVars];
        java.util.Arrays.fill(assignment, -1);
        int[] staffWorkload = new int[data.numStaff];
        // Per-type workload: staffShiftWorkload[staffIdx][shiftIdx] = how many of that type assigned
        int[][] staffShiftWorkload = new int[data.numStaff][data.numShifts];
        BitSet[] restDays = new BitSet[data.numStaff];
        for (int i = 0; i < data.numStaff; i++) {
            restDays[i] = new BitSet(data.numDays);
        }

        // Gap 1: week-bucketed per-(staff, shift) counter for under-min biasing.
        // numWeeks = ceil(numDays / 7) so the last partial week is still tracked.
        int numWeeks = data.dayToWeek == null ? 0
                : (data.dayToWeek.length == 0 ? 0 : data.dayToWeek[data.dayToWeek.length - 1] + 1);
        CspConstraints.MinWeekTracker weekTracker = (numWeeks > 0)
                ? new CspConstraints.MinWeekTracker(data.numStaff, data.numShifts, numWeeks,
                        data.minShiftsPerWeekByShift)
                : null;

        int maxTrail = data.numVars * data.numStaff + 1000;
        int[] trailVar = new int[maxTrail];
        int[] trailStaff = new int[maxTrail];
        int[] trailPtr = {0};

        SearchOutcome outcome = search(domains, assignment, staffWorkload, staffShiftWorkload, restDays,
                trailVar, trailStaff, trailPtr, data, startTime, weekTracker, timeoutMs);

        Map<String, Boolean> result = new HashMap<>();
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) {
                result.put(assignment[v] + "|" + data.varDay[v] + "|" + data.varShift[v], true);
            }
        }

        if (outcome == SearchOutcome.FOUND) {
            return Result.builder()
                    .valid(true)
                    .partial(false)
                    .assignment(result)
                    .build();
        }
        if (outcome == SearchOutcome.TIMED_OUT) {
            // Partial coverage under timeout: the caller decides what to do
            // (typically: fall back to Greedy and merge schedules). We surface
            // the partial map so nothing is lost, but flag it so the orchestrator
            // can choose to top up with a different algorithm instead of
            // treating the partial as a complete plan.
            return Result.builder()
                    .valid(!result.isEmpty())
                    .partial(true)
                    .assignment(result)
                    .errors(result.isEmpty()
                            ? List.of("Hết thời gian trước khi gán được slot nào")
                            : List.of("Hết thời gian — trả về lịch từng phần"))
                    .build();
        }
        return Result.builder().valid(false).errors(List.of("Không tìm được lịch hợp lệ")).build();
    }

    /**
     * Per-frame state for iterative backtracking (replaces JVM call stack).
     * Stores everything needed to resume from a "recursive call" return:
     * the variable, its candidates, the committed staff index, trail pointer,
     * and side-effect state (week tracker, rest days) for clean undo.
     */
    private static final class SearchFrame {
        final int var;
        final int shiftIdx;
        final int dayIdx;
        final String shiftType;
        final List<Integer> candidates;
        final int week;
        final int compDayIdx;     // compensation day for DIRECT_24H, -1 otherwise
        final boolean hasWeekTracker;

        int nextCandidateIdx;     // next index to try in candidates
        int staffIdx;             // committed staff, -1 if not yet committed
        int trailBefore;          // trail pointer at commit time
        boolean committed;        // true = assigned + propagate OK (waiting for child)

        SearchFrame(int var, int shiftIdx, int dayIdx, String shiftType,
                    List<Integer> candidates, int week, int compDayIdx,
                    boolean hasWeekTracker) {
            this.var = var;
            this.shiftIdx = shiftIdx;
            this.dayIdx = dayIdx;
            this.shiftType = shiftType;
            this.candidates = candidates;
            this.week = week;
            this.compDayIdx = compDayIdx;
            this.hasWeekTracker = hasWeekTracker;
            this.nextCandidateIdx = 0;
            this.staffIdx = -1;
            this.trailBefore = -1;
            this.committed = false;
        }

        boolean isDirect24h() { return DIRECT_24H.equals(shiftType); }
    }

    /**
     * Iterative MRV-FC backtracking search.
     *
     * Replaces the original recursive implementation to avoid StackOverflowError
     * on deep problems (30-day × 23-staff with 4 shift types generates ~900+
     * variables). Uses an explicit {@link Deque} of {@link SearchFrame} as the
     * backtracking stack. The search tree, variable ordering (MRV+DH), candidate
     * ordering (per-type workload), AC-3 propagation, nogood learning, and
     * constraint checking are identical to the recursive version — only the
     * frame management mechanism differs.
     *
     * <p>Each outer-loop iteration either:
     * <ol>
     *   <li>Selects a new unassigned variable and pushes a {@link SearchFrame}
     *       ({@link SearchFrame#committed committed} = false), or</li>
     *   <li>Tries the next candidate for the current top frame (the candidate
     *       loop inside the recursive version's {@code for (int staffIdx : candidates)}).</li>
     * </ol>
     * When a candidate passes all checks and propagation succeeds, the frame is
     * marked {@link SearchFrame#committed committed} = true and control returns
     * to step 1 to assign the next variable. When all candidates for a frame are
     * exhausted, the frame is popped; its parent is un-committed (nogood learned,
     * trail rolled back, assignment undone) and the parent's next candidate is
     * tried — exactly matching the recursive unwind.
     */
    private SearchOutcome search(
            BitSet[] domains, int[] assignment, int[] staffWorkload, int[][] staffShiftWorkload,
            BitSet[] restDays, int[] trailVar, int[] trailStaff, int[] trailPtr,
            ProblemData data, long startTime, CspConstraints.MinWeekTracker weekTracker,
            long timeoutMs) {

        // Explicit backtracking stack: one frame per selected variable.
        Deque<SearchFrame> stack = new ArrayDeque<>();

        while (true) {
            // Timeout check — same frequency as the recursive entry check.
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return SearchOutcome.TIMED_OUT;
            }

            // ── Step 1: Select next variable if top frame is committed or stack empty ──
            SearchFrame top = stack.peek();
            if (top == null || top.committed) {
                int var = selectMRV(domains, assignment, data);
                if (var < 0) {
                    return SearchOutcome.FOUND;
                }
                int dayIdx = data.varDay[var];
                int shiftIdx = data.varShift[var];
                String shiftType = SHIFT_ORDER[shiftIdx];
                // Sort candidates: fewest of THIS shift type first, then fewest total
                List<Integer> candidates = getCandidates(domains[var], staffWorkload,
                        staffShiftWorkload, shiftIdx, weekTracker, dayIdx, shiftIdx);
                int week = data.dayToWeek == null ? -1 : data.dayToWeek[dayIdx];
                int compDayIdxVal = shiftType.equals(DIRECT_24H)
                        ? getCompensationDayIdx(dayIdx, data) : -1;
                stack.push(new SearchFrame(var, shiftIdx, dayIdx, shiftType,
                        candidates, week, compDayIdxVal, weekTracker != null));
                continue;
            }

            // ── Step 2: Try next candidate for the current (uncommitted) top frame ──
            SearchFrame frame = top; // alias, guaranteed !committed
            boolean found = false;

            while (frame.nextCandidateIdx < frame.candidates.size()) {
                int staffIdx = frame.candidates.get(frame.nextCandidateIdx);
                frame.nextCandidateIdx++;

                // Nogood check
                if (nogoodStore.violatesNogood(assignment, frame.var, staffIdx, data.numVars)) continue;
                // Consistency check (BR-01/02/03/04/06)
                if (!isConsistent(staffIdx, frame.var, assignment, restDays, staffWorkload, data)) continue;

                // ── Commit candidate ──
                frame.trailBefore = trailPtr[0];
                frame.staffIdx = staffIdx;
                assignment[frame.var] = staffIdx;
                staffWorkload[staffIdx]++;
                staffShiftWorkload[staffIdx][frame.shiftIdx]++;
                if (frame.hasWeekTracker) {
                    weekTracker.increment(staffIdx, frame.shiftIdx, frame.week);
                }
                if (frame.isDirect24h() && frame.compDayIdx >= 0 && frame.compDayIdx < data.numDays) {
                    restDays[staffIdx].set(frame.compDayIdx);
                }

                // Propagate constraints (AC-3 style forward checking)
                if (propagate(staffIdx, frame.var, domains, restDays, assignment,
                        trailVar, trailStaff, trailPtr, frame.trailBefore, data)) {
                    // Propagation succeeded — variable is fully assigned
                    frame.committed = true;
                    found = true;
                    break;
                }

                // Propagate failed → learn nogood, rollback, try next candidate
                learnConflictClause(frame.var, staffIdx, assignment, trailVar, trailPtr, data);
                rollback(frame, domains, assignment, staffWorkload, staffShiftWorkload,
                        restDays, trailVar, trailStaff, trailPtr, weekTracker, data);
            }

            if (found) {
                continue; // committed → go to Step 1 for next variable
            }

            // ── All candidates exhausted — backtrack ──
            stack.pop();
            if (stack.isEmpty()) {
                return SearchOutcome.DEAD_END;
            }

            // Undo the parent frame's commitment (nogood learning + rollback + unassign)
            SearchFrame parent = stack.peek();
            learnConflictClause(parent.var, parent.staffIdx, assignment, trailVar, trailPtr, data);
            rollback(parent, domains, assignment, staffWorkload, staffShiftWorkload,
                    restDays, trailVar, trailStaff, trailPtr, weekTracker, data);
            parent.committed = false;
            // frame.nextCandidateIdx already advanced past the exhausted candidate
        }
    }

    /**
     * Learn a conflict clause from a failure: the failing (var, staffIdx) pair
     * plus all trail entries whose variables are currently assigned.
     * Mirrors the nogood-learning block from the recursive search.
     */
    private void learnConflictClause(
            int var, int staffIdx, int[] assignment,
            int[] trailVar, int[] trailPtr, ProblemData data) {
        Set<int[]> conflict = new java.util.HashSet<>();
        conflict.add(new int[]{var, staffIdx});
        for (int i = 0; i < trailPtr[0]; i++) {
            int tVar = trailVar[i];
            if (tVar >= 0 && tVar < data.numVars && assignment[tVar] >= 0) {
                conflict.add(new int[]{tVar, assignment[tVar]});
            }
        }
        if (!conflict.isEmpty()) {
            nogoodStore.addNogood(conflict, "Domain wipeout or constraint violation");
        }
    }

    /**
     * Rollback a committed frame: restore domain values cleared during
     * propagation, unassign the variable, and undo all side effects
     * (workload counters, week tracker, rest days).
     *
     * @param frame  the frame whose commitment to undo (must have trailBefore >= 0)
     */
    private void rollback(
            SearchFrame frame, BitSet[] domains, int[] assignment,
            int[] staffWorkload, int[][] staffShiftWorkload, BitSet[] restDays,
            int[] trailVar, int[] trailStaff, int[] trailPtr,
            CspConstraints.MinWeekTracker weekTracker, ProblemData data) {
        int staffIdx = frame.staffIdx;
        if (staffIdx < 0) return;

        // Restore domain entries cleared during propagation
        while (trailPtr[0] > frame.trailBefore) {
            trailPtr[0]--;
            domains[trailVar[trailPtr[0]]].set(trailStaff[trailPtr[0]]);
        }

        // Unassign the variable
        assignment[frame.var] = -1;
        staffWorkload[staffIdx]--;
        staffShiftWorkload[staffIdx][frame.shiftIdx]--;

        // Undo week tracker
        if (frame.hasWeekTracker) {
            weekTracker.decrement(staffIdx, frame.shiftIdx, frame.week);
        }

        // Undo rest day (DIRECT_24H compensation)
        if (frame.isDirect24h() && frame.compDayIdx >= 0 && frame.compDayIdx < data.numDays) {
            restDays[staffIdx].clear(frame.compDayIdx);
        }

        frame.staffIdx = -1;
        frame.trailBefore = -1;
    }

    private boolean propagate(
            int staffIdx, int var, BitSet[] domains, BitSet[] restDays, int[] assignment,
            int[] trailVar, int[] trailStaff, int[] trailPtr, int trailBefore, ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // 1. BR-01/02: same-day conflicting shifts — iterate only neighbors via constraintGraph
        for (int neighbor : data.constraintGraph[var]) {
            if (assignment[neighbor] >= 0) continue;
            if (data.varDay[neighbor] != dayIdx) continue;
            String otherShiftType = SHIFT_ORDER[data.varShift[neighbor]];
            if (conflicts(shiftType, otherShiftType) && domains[neighbor].get(staffIdx)) {
                domains[neighbor].clear(staffIdx);
                trailVar[trailPtr[0]] = neighbor;
                trailStaff[trailPtr[0]] = staffIdx;
                trailPtr[0]++;
            }
        }

        // 2. BR-03: rest day on compensation day — only iterate vars on the
        // specific comp-day, not the full var set.
        int compDayIdx = getCompensationDayIdx(dayIdx, data);
        if (compDayIdx >= 0 && compDayIdx < data.numDays && data.varsByDay != null) {
            for (int v : data.varsByDay[compDayIdx]) {
                if (v == var || assignment[v] >= 0) continue;
                if (domains[v].get(staffIdx)) {
                    domains[v].clear(staffIdx);
                    trailVar[trailPtr[0]] = v;
                    trailStaff[trailPtr[0]] = staffIdx;
                    trailPtr[0]++;
                }
            }
        }

        // 3. Detect failure: only the trail entries added in THIS propagate
        // call can have just become empty as a direct result of OUR clear.
        // Older trail entries were already verified non-empty at the time they
        // were added; if we ALSO clear a value from the same var in this
        // call, we'll catch the empty domain in the new trail entry (because
        // domains[v] becomes empty at most once, and we record a trail entry
        // per clear, so the var appears at most once per propagate call —
        // either it's already in OLD trail from before this call, OR it's
        // newly added here). Verifying only [trailBefore, trailPtr[0]) is
        // therefore sufficient and avoids the O(trailSize) scan that
        // dominates runtime on over-constrained workloads.
        for (int i = trailBefore; i < trailPtr[0]; i++) {
            int v = trailVar[i];
            if (v >= 0 && v < data.numVars && assignment[v] < 0 && domains[v].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isConsistent(
            int staffIdx, int var, int[] assignment, BitSet[] restDays,
            int[] staffWorkload, ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        if (restDays[staffIdx].get(dayIdx)) return false;
        if (!data.leaveMatrix[staffIdx][dayIdx]) return false;
        if (data.holidayDays[dayIdx]) return false;
        if (staffWorkload[staffIdx] >= data.staffMaxShifts[staffIdx]) return false;

        // BR-01/02: same-day conflicting shifts — check via constraintGraph neighbors
        for (int neighbor : data.constraintGraph[var]) {
            if (assignment[neighbor] == staffIdx) {
                String otherShiftType = SHIFT_ORDER[data.varShift[neighbor]];
                if (conflicts(shiftType, otherShiftType)) return false;
            }
        }

        // BR-06: at most one L01 per STAFF per day (slots on the same shift-
        // type same-day for different staff are allowed when requiredStaffCount > 1).
        // Earlier revisions of this check forgot the staff equality and
        // rejected every candidate whenever any L01 var on the same day was
        // already assigned — making CSP fail as soon as it assigned the very
        // first L01 slot and silently dropping that requirement. The staff
        // equality restores the intended semantics.
        if (shiftType.equals(DIRECT_24H) && data.varsByDay != null) {
            for (int v : data.varsByDay[dayIdx]) {
                if (v != var && assignment[v] == staffIdx
                        && SHIFT_ORDER[data.varShift[v]].equals(DIRECT_24H)) {
                    return false;
                }
            }
        }

        // Gap 2: BR-04 — adjacent L01 (cùng nhân sự, 2 ngày liên tiếp có L01).
        // Lookup is O(adjacentPairs) per check which is small (≤ 2*K where K is
        // number of L01 pairs in the period). Without this guard the CSP could
        // pick two adjacent L01s and rely on the post-hoc scorer to flag the
        // violation, which is far more expensive than a single early reject.
        if (shiftType.equals(DIRECT_24H) && data.adjacentL01Pairs != null) {
            for (int p = 0; p < data.adjacentL01PairCount; p++) {
                int base = p * 2;
                int v1 = data.adjacentL01Pairs[base];
                int v2 = data.adjacentL01Pairs[base + 1];
                int otherVar = (v1 == var) ? v2 : (v2 == var ? v1 : -1);
                if (otherVar >= 0 && assignment[otherVar] == staffIdx) {
                    return false;
                }
            }
        }
        return true;
    }

    private int selectMRV(BitSet[] domains, int[] assignment, ProblemData data) {
        int bestVar = -1;
        int minSize = Integer.MAX_VALUE;
        int maxDegree = -1;
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) continue;
            int size = domains[v].cardinality();
            if (size == 0) return -1;
            // MRV primary: smaller domain first. Tie-break: degree heuristic
            // (more unassigned neighbors = more constrained = should be picked
            // before peers to expose dead-ends earlier). This is a classic CSP
            // combo called MRV+DH and empirically cuts search tree depth on
            // over-constrained workloads.
            int degree = 0;
            if (size <= minSize && data.constraintGraph != null) {
                List<Integer> neighbors = data.constraintGraph[v];
                if (neighbors != null) {
                    for (int n : neighbors) {
                        if (assignment[n] < 0) degree++;
                    }
                }
            }
            if (size < minSize) {
                minSize = size;
                bestVar = v;
                maxDegree = degree;
            } else if (size == minSize && degree > maxDegree) {
                bestVar = v;
                maxDegree = degree;
            }
        }
        return bestVar;
    }

    /**
     * Sort candidates for a variable:
     * Tier-1: fewest of THIS shift type → guarantees even per-type distribution
     * Tier-2: fewest total shifts → secondary tiebreak for overall balance
     *
     * This two-level sort prevents the CSP from repeatedly assigning the same
     * staff to L01 (or any other heavy type) simply because they have the
     * lowest total workload, while other staff accumulate only light shifts.
     */
    private List<Integer> getCandidates(BitSet domain, int[] staffWorkload,
                                        int[][] staffShiftWorkload, int shiftIdx,
                                        CspConstraints.MinWeekTracker weekTracker,
                                        int dayIdx, int dayShiftIdx) {
        List<Integer> list = new ArrayList<>();
        for (int s = domain.nextSetBit(0); s >= 0; s = domain.nextSetBit(s + 1)) {
            list.add(s);
        }
        final int week = dayIdx < 0 ? -1 : dayIdx / 7; // mirror CspConstraints.buildWeekBuckets
        list.sort(Comparator
                .comparingInt((Integer s) -> staffShiftWorkload[s][shiftIdx])  // per-type first
                .thenComparingInt(s -> staffWorkload[s])                        // total as tiebreak
                .thenComparingInt(s -> weekTracker == null
                        ? 0
                        : -weekTracker.underMinScore(s, shiftIdx, week)));      // Gap 1: under-min first
        return list;
    }

    private boolean isGoal(BitSet[] domains, int[] assignment, ProblemData data) {
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] < 0) return false;
        }
        return true;
    }

    /**
     * Compensation day index for a given work-day index, or -1 if the
     * compensation day falls outside the active period.
     * Uses the pre-computed mapping from ProblemData (flexible for Fri/Sat duty).
     */
    int getCompensationDayIdx(int dayIdx, ProblemData data) {
        if (data.compDayIdx != null && dayIdx >= 0 && dayIdx < data.numDays) {
            return data.compDayIdx[dayIdx];
        }
        // Fallback (should not be reached when compDayIdx is properly built)
        LocalDate workDate = data.baseDate.plusDays(dayIdx);
        LocalDate compDate = compensationDateCalculator.calculate(workDate);
        long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
        if (offset >= 0 && offset < data.numDays) return (int) offset;
        return -1;
    }

    private BitSet[] copyDomains(ProblemData data) {
        BitSet[] copy = new BitSet[data.numVars];
        for (int v = 0; v < data.numVars; v++) {
            copy[v] = new BitSet(data.numStaff);
            copy[v].or(data.domains[v]);
        }
        return copy;
    }

    @Builder
    @Getter
    static class Result {
        boolean valid;
        /**
         * True when {@link #valid} is true only because the search returned
         * a partial assignment under timeout pressure (not a complete
         * coverage of every required slot). The orchestrator can use this
         * to surface a "coverage rate &lt; 100%" hint in the UI rather than
         * silently presenting a partial plan as if it were complete.
         */
        boolean partial;
        Map<String, Boolean> assignment;
        List<String> errors;
    }
}
