package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
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

    private static final long TIMEOUT_MS = 30_000L;

    private final CompensationDateCalculator compensationDateCalculator;
    private final CspNogoodStore nogoodStore;

    Result solve(ProblemData data, long startTime) {
        BitSet[] domains = copyDomains(data);
        int[] assignment = new int[data.numVars];
        java.util.Arrays.fill(assignment, -1);
        int[] staffWorkload = new int[data.numStaff];
        BitSet[] restDays = new BitSet[data.numStaff];
        for (int i = 0; i < data.numStaff; i++) {
            restDays[i] = new BitSet(data.numDays);
        }

        int maxTrail = data.numVars * data.numStaff + 1000;
        int[] trailVar = new int[maxTrail];
        int[] trailStaff = new int[maxTrail];
        int[] trailPtr = {0};

        boolean found = search(domains, assignment, staffWorkload, restDays,
                trailVar, trailStaff, trailPtr, data, startTime);
        if (!found) {
            return Result.builder().valid(false).errors(List.of("Không tìm được lịch hợp lệ")).build();
        }

        Map<String, Boolean> result = new HashMap<>();
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) {
                result.put(assignment[v] + "|" + data.varDay[v] + "|" + data.varShift[v], true);
            }
        }
        return Result.builder().valid(true).assignment(result).build();
    }

    private boolean search(
            BitSet[] domains, int[] assignment, int[] staffWorkload, BitSet[] restDays,
            int[] trailVar, int[] trailStaff, int[] trailPtr, ProblemData data, long startTime) {

        if (System.currentTimeMillis() - startTime > TIMEOUT_MS) return false;
        if (isGoal(domains, assignment, data)) return true;

        int var = selectMRV(domains, assignment, data);
        if (var < 0) return true;

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        List<Integer> candidates = getCandidates(domains[var], staffWorkload);

        for (int staffIdx : candidates) {
            if (nogoodStore.violatesNogood(assignment, var, staffIdx, data.numVars)) continue;
            if (!isConsistent(staffIdx, var, assignment, restDays, staffWorkload, data)) continue;

            int trailBefore = trailPtr[0];
            assignment[var] = staffIdx;
            staffWorkload[staffIdx]++;

            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) restDays[staffIdx].set(compDayIdx);
            }

            if (propagate(staffIdx, var, domains, restDays, assignment,
                    trailVar, trailStaff, trailPtr, data)) {

                if (search(domains, assignment, staffWorkload, restDays,
                        trailVar, trailStaff, trailPtr, data, startTime)) {
                    return true;
                }
            }

            // Learn from failure before rolling back
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

            // Rollback
            while (trailPtr[0] > trailBefore) {
                trailPtr[0]--;
                domains[trailVar[trailPtr[0]]].set(trailStaff[trailPtr[0]]);
            }
            assignment[var] = -1;
            staffWorkload[staffIdx]--;
            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) restDays[staffIdx].clear(compDayIdx);
            }
        }
        return false;
    }

    private boolean propagate(
            int staffIdx, int var, BitSet[] domains, BitSet[] restDays, int[] assignment,
            int[] trailVar, int[] trailStaff, int[] trailPtr, ProblemData data) {

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

        // 2. BR-03: rest day on compensation day
        int compDayIdx = getCompensationDayIdx(dayIdx, data);
        if (compDayIdx >= 0 && compDayIdx < data.numDays) {
            for (int v = 0; v < data.numVars; v++) {
                if (v == var || assignment[v] >= 0) continue;
                if (data.varDay[v] != compDayIdx) continue;
                if (domains[v].get(staffIdx)) {
                    domains[v].clear(staffIdx);
                    trailVar[trailPtr[0]] = v;
                    trailStaff[trailPtr[0]] = staffIdx;
                    trailPtr[0]++;
                }
            }
        }

        // 3. Detect failure: any unassigned variable has an empty domain
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] < 0 && domains[v].isEmpty()) return false;
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
        if (data.leaveMatrix[staffIdx][dayIdx]) return false;
        if (data.holidayDays[dayIdx]) return false;
        if (staffWorkload[staffIdx] >= data.staffMaxShifts[staffIdx]) return false;

        // BR-01/02: same-day conflicting shifts — check via constraintGraph neighbors
        for (int neighbor : data.constraintGraph[var]) {
            if (assignment[neighbor] == staffIdx) {
                String otherShiftType = SHIFT_ORDER[data.varShift[neighbor]];
                if (conflicts(shiftType, otherShiftType)) return false;
            }
        }

        // BR-06: at most one L01 per day
        if (shiftType.equals(DIRECT_24H)) {
            for (int v = 0; v < data.numVars; v++) {
                if (v != var && assignment[v] >= 0 && data.varDay[v] == dayIdx
                        && SHIFT_ORDER[data.varShift[v]].equals(DIRECT_24H)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int selectMRV(BitSet[] domains, int[] assignment, ProblemData data) {
        int bestVar = -1;
        int minSize = Integer.MAX_VALUE;
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) continue;
            int size = domains[v].cardinality();
            if (size == 0) return -1;
            if (size < minSize) {
                minSize = size;
                bestVar = v;
            }
        }
        return bestVar;
    }

    private List<Integer> getCandidates(BitSet domain, int[] staffWorkload) {
        List<Integer> list = new ArrayList<>();
        for (int s = domain.nextSetBit(0); s >= 0; s = domain.nextSetBit(s + 1)) {
            list.add(s);
        }
        list.sort(Comparator.comparingInt(s -> staffWorkload[s]));
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
     */
    int getCompensationDayIdx(int dayIdx, ProblemData data) {
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
        Map<String, Boolean> assignment;
        List<String> errors;
    }
}
