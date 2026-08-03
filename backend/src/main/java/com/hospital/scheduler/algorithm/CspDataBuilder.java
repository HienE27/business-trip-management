package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;
import static com.hospital.scheduler.algorithm.CspConstants.getShiftIdx;

/**
 * Builds the {@link ProblemData} snapshot from raw inputs: requirements,
 * leaves, staff and the supplied date range.
 *
 * Also runs an initial AC-3 pass and symmetry breaking before handing the
 * data to the search engine.
 */
@Component
@RequiredArgsConstructor
public class CspDataBuilder {

    private final CompensationDateCalculator compensationDateCalculator;
    private final CspAc3Engine ac3Engine;
    private final CspConstraints constraints;

    /**
     * Gap-fix params:
     * <ul>
     *   <li>{@code minShiftsPerWeekByShift} — Gap 1: per-(shift) minimum
     *       shifts a staff should accumulate per week. Pass {@code null} to
     *       disable enforcement (the search engine treats it as all-zero).</li>
     *   <li>{@code activeShiftTypeIds} — Gap 3: when non-null, re-orders
     *       the shift-type table to match the set of {@code ShiftType}
     *       rows active in the DB. When {@code null}, the canonical
     *       {@link CspConstants#SHIFT_ORDER} is used.</li>
     * </ul>
     */
    public ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {
        return build(staffList, dates, requirements, leaveRequests, null, null, 0);
    }

    public ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            int[] minShiftsPerWeekByShift,
            Set<String> activeShiftTypeIds) {
        return build(staffList, dates, requirements, leaveRequests,
                minShiftsPerWeekByShift, activeShiftTypeIds, 0);
    }

    public ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            int[] minShiftsPerWeekByShift,
            Set<String> activeShiftTypeIds,
            int maxShiftsOverride) {

        int numDays = dates.size();
        int numShifts = SHIFT_ORDER.length;
        int numStaff = staffList.size();

        int[][] slotCount = countSlots(dates, numDays, requirements);
        int varCount = totalVars(slotCount, numDays, numShifts);

        int[] varDay = new int[varCount];
        int[] varShift = new int[varCount];
        int[] varSlot = new int[varCount];
        int[] varSpecialty = new int[varCount];
        fillVarArrays(slotCount, varDay, varShift, varSlot, varSpecialty, numDays, numShifts, requirements, dates);

        boolean[][] leaveMatrix = buildLeaveMatrix(staffList, dates, numDays, numStaff, leaveRequests);
        boolean[] holidayDays = detectHolidayDays(slotCount, numDays, numShifts);
        int[] staffMaxShifts = maxShiftsPerStaff(staffList, numStaff, maxShiftsOverride);

        BitSet[] domains = buildInitialDomains(varCount, varDay, varShift, varSpecialty, slotCount, numDays, numStaff,
                leaveMatrix, holidayDays, staffList);
        int[] compDayIdx = buildCompDayIdx(slotCount, dates, numDays, numShifts);
        List<Integer>[] constraintGraph = buildConstraintGraph(varDay, varShift, varCount, slotCount, dates, compDayIdx, numDays);

        applySymmetryBreaking(domains, varCount);

        // Gap 1: week bucket for per-week minimum tracking
        int[] dayToWeek = constraints.buildWeekBuckets(numDays);

        // Gap 2: BR-04 adjacent-L01 pair table
        int[] adjacentPairs = constraints.buildAdjacentL01Pairs(varDay, varShift, varCount);

        // Gap 3: shift type ids (mirror SHIFT_ORDER by default, override when DB-driven)
        String[] shiftTypeIds = constraints.resolveShiftOrder(activeShiftTypeIds);

        // Performance: pre-index vars by day so propagate() and isConsistent() can do
        // O(varsOnDay) work instead of O(numVars) — important for 29-day periods where
        // numVars is in the hundreds of thousands and most slots are off-day.
        List<Integer>[] varsByDay = buildVarsByDay(varDay, varCount, numDays);

        ProblemData data = ProblemData.builder()
                .numDays(numDays)
                .numShifts(numShifts)
                .numStaff(numStaff)
                .numVars(varCount)
                .varDay(varDay)
                .varShift(varShift)
                .varSlot(varSlot)
                .varSpecialty(varSpecialty)
                .slotCount(slotCount)
                .leaveMatrix(leaveMatrix)
                .holidayDays(holidayDays)
                .staffMaxShifts(staffMaxShifts)
                .domains(domains)
                .constraintGraph(constraintGraph)
                .baseDate(dates.get(0))
                .minShiftsPerWeekByShift(
                        minShiftsPerWeekByShift != null ? minShiftsPerWeekByShift : new int[numShifts])
                .dayToWeek(dayToWeek)
                .adjacentL01Pairs(adjacentPairs)
                .adjacentL01PairCount(adjacentPairs.length / 2)
                .shiftTypeIds(shiftTypeIds)
                .varsByDay(varsByDay)
                .compDayIdx(compDayIdx)
                .build();

        // Initial AC-3 prunes domains using BR-01, BR-02 and BR-03 arcs
        ac3Engine.runInitialAC3(domains, constraintGraph, varDay, data);
        return data;
    }

    // ==================== private helpers ====================

    private int findStaffIdx(List<Staff> staffList, int staffId) {
        for (int i = 0; i < staffList.size(); i++) {
            if (staffList.get(i).getId() == staffId) return i;
        }
        return -1;
    }

    // ==================== private helpers ====================

    private int[][] countSlots(List<LocalDate> dates, int numDays, List<ShiftRequirementInfo> requirements) {
        int numShifts = SHIFT_ORDER.length;
        int[][] slotCount = new int[numDays][numShifts];
        for (ShiftRequirementInfo req : requirements) {
            int dayIdx = (int) ChronoUnit.DAYS.between(dates.get(0), req.workDate());
            int shiftIdx = getShiftIdx(req.shiftTypeId());
            if (dayIdx >= 0 && dayIdx < numDays && shiftIdx >= 0) {
                slotCount[dayIdx][shiftIdx] += req.requiredCount();
            }
        }
        return slotCount;
    }

    private int totalVars(int[][] slotCount, int numDays, int numShifts) {
        int varCount = 0;
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numShifts; s++) {
                varCount += slotCount[d][s];
            }
        }
        return varCount;
    }

    private void fillVarArrays(int[][] slotCount, int[] varDay, int[] varShift, int[] varSlot, int[] varSpecialty,
                               int numDays, int numShifts, List<ShiftRequirementInfo> requirements, List<LocalDate> dates) {
        // BUGFIX (2026-08-03): specialtyMap (day_shift → MỘT specialty) sai khi một
        // ngày có yêu cầu L04 của NHIỀU chuyên khoa (adaptive L04 mở PK nhiều khoa
        // cùng ngày) — specialty cuối ghi đè, mọi slot L04 ngày đó mang cùng 1
        // specialty → domain sai → CSP DEAD_END (iters=5, bestPartial=1) → fallback
        // Greedy. Thay bằng danh sách specialty theo từng slot L04 (mở rộng
        // requiredCount, giữ thứ tự requirement) — đúng chuyên khoa cho từng var.
        java.util.List<java.util.List<Integer>> l04SpecByDay = new java.util.ArrayList<>();
        for (int d = 0; d < numDays; d++) l04SpecByDay.add(new java.util.ArrayList<>());
        for (ShiftRequirementInfo req : requirements) {
            int dayIdx = (int) ChronoUnit.DAYS.between(dates.get(0), req.workDate());
            if (dayIdx < 0 || dayIdx >= numDays || req.specialtyId() == null) continue;
            if (getShiftIdx(req.shiftTypeId()) != 3) continue; // chỉ L04
            for (int k = 0; k < req.requiredCount(); k++) {
                l04SpecByDay.get(dayIdx).add(req.specialtyId());
            }
        }

        int vid = 0;
        for (int d = 0; d < numDays; d++) {
            java.util.List<Integer> l04Specs = l04SpecByDay.get(d);
            int l04Idx = 0;
            for (int s = 0; s < numShifts; s++) {
                for (int slot = 0; slot < slotCount[d][s]; slot++) {
                    varDay[vid] = d;
                    varShift[vid] = s;
                    varSlot[vid] = slot;
                    // Store specialty for L04 shifts (null/0 for other types)
                    if (s == 3) {
                        varSpecialty[vid] = (l04Idx < l04Specs.size()) ? l04Specs.get(l04Idx) : 0;
                        l04Idx++;
                    } else {
                        varSpecialty[vid] = 0;
                    }
                    vid++;
                }
            }
        }
    }

    private boolean[][] buildLeaveMatrix(List<Staff> staffList, List<LocalDate> dates, int numDays, int numStaff,
                                         List<LeaveRequest> leaveRequests) {
        // Default = all-true (every staff is available on every day). Individual
        // APPROVED leave requests then set the matching cell to false.
        // The previous implementation left the matrix all-false when leaveRequests
        // was null, which made the domain pruner wipe every variable.
        boolean[][] leaveMatrix = new boolean[numStaff][numDays];
        for (int i = 0; i < numStaff; i++) {
            java.util.Arrays.fill(leaveMatrix[i], true);
        }
        if (leaveRequests == null || leaveRequests.isEmpty()) return leaveMatrix;

        // Cache epoch days outside inner loop for O(1) lookup
        long[] dayEpochs = new long[numDays];
        for (int d = 0; d < numDays; d++) {
            dayEpochs[d] = dates.get(d).toEpochDay();
        }

        for (LeaveRequest lr : leaveRequests) {
            if (lr.getStatus() != LeaveRequest.LeaveStatus.APPROVED || lr.getStaff() == null) continue;
            int staffIdx = findStaffIdx(staffList, lr.getStaff().getId());
            if (staffIdx < 0) continue;
            long startEpoch = lr.getStartDate().toEpochDay();
            long endEpoch = lr.getEndDate().toEpochDay();
            for (int d = 0; d < numDays; d++) {
                long dayEpoch = dayEpochs[d];
                if (dayEpoch >= startEpoch && dayEpoch <= endEpoch) {
                    leaveMatrix[staffIdx][d] = false;
                }
            }
        }
        return leaveMatrix;
    }

    private boolean[] detectHolidayDays(int[][] slotCount, int numDays, int numShifts) {
        boolean[] holidayDays = new boolean[numDays];
        for (int d = 0; d < numDays; d++) {
            // A day with no requirements on any shift is treated as a holiday
            holidayDays[d] = (slotCount[d][0] == 0 && slotCount[d][1] == 0 &&
                              slotCount[d][2] == 0 && slotCount[d][3] == 0);
        }
        return holidayDays;
    }

    private int[] maxShiftsPerStaff(List<Staff> staffList, int numStaff, int maxShiftsOverride) {
        int[] staffMaxShifts = new int[numStaff];
        if (maxShiftsOverride > 0) {
            java.util.Arrays.fill(staffMaxShifts, maxShiftsOverride);
        } else {
            for (int i = 0; i < numStaff; i++) {
                staffMaxShifts[i] = staffList.get(i).getMaxShiftsPerMonth() != null
                        ? staffList.get(i).getMaxShiftsPerMonth() : 5;
            }
        }
        return staffMaxShifts;
    }

    private BitSet[] buildInitialDomains(int varCount, int[] varDay, int[] varShift, int[] varSpecialty, int[][] slotCount,
                                         int numDays, int numStaff, boolean[][] leaveMatrix, boolean[] holidayDays,
                                         List<Staff> staffList) {
        // Pre-compute eligibility flags per staff per shift type.
        //
        // IMPORTANT: keep this in sync with {@link StaffShiftTypeEligibility}:
        //   L01/L02/L03 → ALL_ELIGIBLE_SPECIALTIES (6 khoa: Ngoại, Nội, Sản, Nhi, Mắt, Răng)
        //   L04         → strict: staff.specialty.id == varSpecialty[v] (không cross-specialty)
        //
        // Theo tài liệu nghiệp vụ, L01/L02/L03 không bị giới hạn theo chuyên khoa.
        // Specialty.name values come from the {@code Specialty} entity (seeded
        // as "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng" in DataSeeder).
        java.util.Map<Integer, boolean[]> eligibilityMatrix = new java.util.HashMap<>();
        for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
            Staff st = staffList.get(staffIdx);
            String spName = st.getSpecialty() != null ? st.getSpecialty().getName() : null;
            boolean active = Boolean.TRUE.equals(st.getIsActive());
            boolean inAllEligible = spName != null && StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES.contains(spName);
            for (int s = 0; s < SHIFT_ORDER.length; s++) {
                String shiftTypeId = SHIFT_ORDER[s];
                boolean eligible;
                if ("L04".equals(shiftTypeId)) {
                    // L04: any active staff with a specialty is eligible; the
                    // per-variable strict specialty filter below prunes to the
                    // exact required specialty (không cross-specialty).
                    eligible = active && st.getSpecialty() != null;
                } else {
                    // L01/L02/L03: any active staff in ALL_ELIGIBLE_SPECIALTIES
                    eligible = active && inAllEligible;
                }
                eligibilityMatrix.computeIfAbsent(s, k -> new boolean[numStaff])[staffIdx] = eligible;
            }
        }

        BitSet[] domains = new BitSet[varCount];
        for (int v = 0; v < varCount; v++) {
            domains[v] = new BitSet(numStaff);
            int d = varDay[v];
            int s = varShift[v];
            if (slotCount[d][s] > 0 && !holidayDays[d]) {
                boolean[] shiftEligibility = eligibilityMatrix.get(s);
                // L04 luôn strict-specialty: chỉ staff đúng chuyên khoa
                // (requiredSpecialtyId) vào domain — domain nhỏ, search nhanh.
                int requiredSpecialtyId = (varSpecialty != null && s == 3) ? varSpecialty[v] : 0;
                for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
                    if (!leaveMatrix[staffIdx][d]) continue;
                    if (!shiftEligibility[staffIdx]) continue;
                    if (requiredSpecialtyId != 0) {
                        Specialty sp = staffList.get(staffIdx).getSpecialty();
                        if (sp == null || sp.getId() == null || sp.getId() != requiredSpecialtyId) continue;
                    }
                    domains[v].set(staffIdx);
                }
            }
        }
        return domains;
    }

    /**
     * Build a symmetric constraint graph where edges mean "the two variables
     * must not be assigned the same staff on the same day" (BR-01/02) or
     * "L01(day) blocks any shift on its compensation day" (BR-03).
     */
    private List<Integer>[] buildConstraintGraph(int[] varDay, int[] varShift, int varCount,
                                                 int[][] slotCount, List<LocalDate> dates,
                                                 int[] compDayIdx, int numDays) {
        @SuppressWarnings("unchecked")
        List<Integer>[] graph = new ArrayList[varCount];
        for (int i = 0; i < varCount; i++) {
            graph[i] = new ArrayList<>();
        }

        // 1. Same-day conflicts: L01↔L02, L03↔L04
        for (int v1 = 0; v1 < varCount; v1++) {
            int d1 = varDay[v1];
            String t1 = SHIFT_ORDER[varShift[v1]];

            for (int v2 = v1 + 1; v2 < varCount; v2++) {
                if (varDay[v2] != d1) continue;
                String t2 = SHIFT_ORDER[varShift[v2]];
                if (conflicts(t1, t2)) {
                    graph[v1].add(v2);
                    graph[v2].add(v1);
                }
            }
        }

        // 2. BR-03: L01(day) ↔ all shifts on its compensation day
        for (int v = 0; v < varCount; v++) {
            if (!SHIFT_ORDER[varShift[v]].equals(DIRECT_24H)) continue;

            int dayIdx = varDay[v];
            int compDayIdxVal = (dayIdx >= 0 && dayIdx < numDays) ? compDayIdx[dayIdx] : -1;
            if (compDayIdxVal < 0 || compDayIdxVal >= numDays) continue;

            for (int u = 0; u < varCount; u++) {
                if (varDay[u] == compDayIdxVal && u != v) {
                    graph[v].add(u);
                    graph[u].add(v);
                }
            }
        }

        return graph;
    }

    /**
     * Build a {@code [numDays]} array of {@code List<Integer>} of variable indices
     * that fall on each day. Used by propagate() and isConsistent() to skip
     * irrelevant vars instead of iterating every var on the hot path.
     */
    @SuppressWarnings("unchecked")
    private List<Integer>[] buildVarsByDay(int[] varDay, int varCount, int numDays) {
        List<Integer>[] out = new List[numDays];
        for (int d = 0; d < numDays; d++) out[d] = new ArrayList<>();
        for (int v = 0; v < varCount; v++) {
            int d = varDay[v];
            if (d >= 0 && d < numDays) out[d].add(v);
        }
        return out;
    }

    /**
     * Pre-compute the best compensation day index for each day.
     * For Fri/Sat duty, picks the option (Tue/Wed/Thu of next week) with the
     * lowest total required staff count (least-loaded day → best day to be off).
     * For other days, uses the single default compensation day.
     * Returns -1 for days with no L01 requirement or when comp day falls outside
     * the period.
     */
    private int[] buildCompDayIdx(int[][] slotCount, List<LocalDate> dates, int numDays, int numShifts) {
        int l01ShiftIdx = -1;
        for (int s = 0; s < numShifts; s++) {
            if (DIRECT_24H.equals(SHIFT_ORDER[s])) {
                l01ShiftIdx = s;
                break;
            }
        }
        if (l01ShiftIdx < 0) return new int[numDays]; // all -1

        // Pre-compute daily total load (sum across all shift types)
        int[] dailyLoad = new int[numDays];
        for (int d = 0; d < numDays; d++) {
            int total = 0;
            for (int s = 0; s < numShifts; s++) {
                total += slotCount[d][s];
            }
            dailyLoad[d] = total;
        }

        int[] compDayIdx = new int[numDays];
        java.util.Arrays.fill(compDayIdx, -1);

        for (int d = 0; d < numDays; d++) {
            if (slotCount[d][l01ShiftIdx] <= 0) continue; // no L01 on this day

            LocalDate workDate = dates.get(d);
            Set<LocalDate> options = compensationDateCalculator.calculateAll(workDate);
            if (options == null || options.isEmpty()) continue;

            LocalDate bestDate = null;
            int bestLoad = Integer.MAX_VALUE;
            for (LocalDate opt : options) {
                long optOffset = ChronoUnit.DAYS.between(dates.get(0), opt);
                if (optOffset < 0 || optOffset >= numDays) continue;
                int load = dailyLoad[(int) optOffset];
                if (load < bestLoad) {
                    bestLoad = load;
                    bestDate = opt;
                }
            }
            if (bestDate != null) {
                long offset = ChronoUnit.DAYS.between(dates.get(0), bestDate);
                if (offset >= 0 && offset < numDays) {
                    compDayIdx[d] = (int) offset;
                }
            }
        }
        return compDayIdx;
    }

    /**
     * Symmetry breaking: fix the first variable (the one with the smallest
     * domain) to its first eligible staff, so the search tree doesn't
     * enumerate permutations of equivalent solutions.
     */
    private void applySymmetryBreaking(BitSet[] domains, int varCount) {
        int firstVar = -1;
        int minDomainSize = Integer.MAX_VALUE;
        for (int v = 0; v < varCount; v++) {
            if (!domains[v].isEmpty() && domains[v].cardinality() < minDomainSize) {
                minDomainSize = domains[v].cardinality();
                firstVar = v;
            }
        }
        if (firstVar < 0 || domains[firstVar].isEmpty()) return;

        int firstStaff = domains[firstVar].nextSetBit(0);
        for (int s = domains[firstVar].nextSetBit(0); s >= 0; s = domains[firstVar].nextSetBit(s + 1)) {
            if (s != firstStaff) domains[firstVar].clear(s);
        }
    }
}
