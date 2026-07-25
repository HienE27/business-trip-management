package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Greedy — Greedy + fatigue awareness.
 * Avoids assigning consecutive-day shifts to the same staff when possible.
 * Ported from algorithm_comparison.ipynb (generate_enhanced_greedy_schedule).
 */
@Slf4j
@Component
@RequiredArgsConstructor
	public class EnhancedGreedyScheduler {

    private final CompensationDateCalculator compensationDateCalculator;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds,
            L04CrossSpecialtyConfig l04CrossConfig) {

        long start = System.currentTimeMillis();
        // Inter-type balance weight: derived from arrangement_mode config.
        // WITH_INTER_BALANCE → 5.0 (soft penalty), INTRA_TYPE → 0.0 (disabled).
        double interTypeWeight = "WITH_INTER_BALANCE".equals(runtimeConfig.getArrangementMode()) ? 5.0 : 0.0;
	        Map<Integer, Staff> staffMap = activeStaff.stream()
	                .collect(Collectors.toMap(Staff::getId, s -> s));
	        // P2-7: single-date map (only the latest date is consulted; previous List<LocalDate> grew unbounded)
	        Map<Integer, LocalDate> staffLastWork = new HashMap<>();
	        Map<Integer, Integer> staffCount = new HashMap<>();
	        Map<Integer, Set<String>> staffTypes = new HashMap<>(); // rotation tracking
	        // L04 per-specialty balance: staffId → {specialtyId → l04Count}
	        Map<Integer, Map<Integer, Integer>> l04CountBySpec = new HashMap<>();
		        // Per-type balance: staffId → {shiftTypeId → count} for L01/L02/L03 balance
		        Map<Integer, Map<String, Integer>> typeCountByStaff = new HashMap<>();
			        // Track shift types per staff per day (L01+L02 cấm, L03+L04 cấm, L01 cấm ALL)
			        Map<String, Set<String>> assignedTypesPerDay = new HashMap<>(); // "staffId|date" → {L01, L02, ...}
        // Compensation day tracking: sau khi gán L01, ngày hôm sau (hoặc sau T6/T7) là nghỉ bù
        Map<Integer, Set<LocalDate>> staffCompDays = new HashMap<>(); // staffId → {compDates}
        // Track cross-specialty assignments per L04 requirement: "date|shiftTypeId|specId" → count
        Map<String, Integer> crossAssignmentCount = new HashMap<>();
        
        // Adaptive penalty: tổng required và assigned cho từng loại/specialty
        // Dùng để tính coverage gap → điều chỉnh penalty cho phù hợp
        Map<String, Integer> totalRequiredByType = new HashMap<>(); // shiftTypeId → tổng required
        Map<Integer, Integer> totalL04BySpec = new HashMap<>();     // specialtyId → tổng L04 required
        Map<String, Integer> assignedByType = new HashMap<>();      // shiftTypeId → đã gán
        Map<Integer, Integer> assignedL04BySpec = new HashMap<>();  // specialtyId → L04 đã gán
        // Pre-compute totals
        for (ShiftRequirement r : requirements) {
            String tid = r.getShiftType().getId();
            int req = r.getRequiredStaffCount();
            totalRequiredByType.merge(tid, req, Integer::sum);
            if ("L04".equals(tid) && r.getSpecialty() != null) {
                totalL04BySpec.merge(r.getSpecialty().getId(), req, Integer::sum);
            }
        }
        int numStaff = activeStaff.size();

        // Group by date+shift
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate, TreeMap::new, Collectors.toList()));

        int l01Window = runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1;

        List<Schedule> result = new ArrayList<>();

        // Xử lý theo thứ tự ưu tiên: L01 → L02 → L03 → L04 (M07 B3)
        // Mỗi loại được xử lý TOÀN BỘ trước khi chuyển sang loại tiếp theo
        String[] priorityOrder = {"L01", "L02", "L03", "L04"};
        for (String priorityType : priorityOrder) {
            for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
                LocalDate date = e.getKey();
                for (ShiftRequirement req : e.getValue()) {
                    // Chỉ xử lý requirement thuộc loại ưu tiên hiện tại
                    if (!priorityType.equals(req.getShiftType().getId())) continue;
                    
                    String shiftTypeId = req.getShiftType().getId();
                    int required = req.getRequiredStaffCount();
                    Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

                // Score candidates: fatigue = gap since last work
                List<ScoredStaff> candidates = new ArrayList<>();
	                for (Staff s : activeStaff) {
	                    if (excludedStaffIds != null && excludedStaffIds.contains(s.getId())) continue;
	                    
                    // Conflict check:
                    // - L01 (trực 24/24) xung đột với L02 (thông tầm)
                    // - L03 (PK Dịch vụ) xung đột với L04 (PK Chuyên gia)
                    String dayKey = s.getId() + "|" + date;
                    Set<String> todayTypes = assignedTypesPerDay.getOrDefault(dayKey, Collections.emptySet());
                    // L01 ↔ L02 conflict
                    if (("L01".equals(shiftTypeId) && todayTypes.contains("L02"))
                            || ("L02".equals(shiftTypeId) && todayTypes.contains("L01"))) {
                        continue;
                    }
                    // L03↔L04 conflict
                    if (("L03".equals(shiftTypeId) && todayTypes.contains("L04"))
                            || ("L04".equals(shiftTypeId) && todayTypes.contains("L03"))) {
                        continue;
                    }

                    // maxShiftsPerDay — HARD cap tổng số ca mỗi nhân sự mỗi ngày (main pass).
                    // 0 = không giới hạn, scheduler tự quyết định theo các constraint khác.
                    if (runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                            && todayTypes.size() >= runtimeConfig.getMaxShiftsPerDay()) {
                        continue;
                    }

                    // Compensation day check: không được gán ca nào vào ngày nghỉ bù
                    Set<LocalDate> compDays = staffCompDays.get(s.getId());
                    if (compDays != null && compDays.contains(date)) continue;

                    // Consecutive L01 hard block (main pass) — window = ceil(overnightRecoveryHours/24)
                    if ("L01".equals(shiftTypeId)) {
                        boolean hasAdjacent = false;
                        for (int dt = 1; dt <= l01Window; dt++) {
                            Set<String> prevTypes = assignedTypesPerDay.getOrDefault(
                                    s.getId() + "|" + date.minusDays(dt), Collections.emptySet());
                            if (prevTypes.contains("L01")) { hasAdjacent = true; break; }
                            Set<String> nextTypes = assignedTypesPerDay.getOrDefault(
                                    s.getId() + "|" + date.plusDays(dt), Collections.emptySet());
                            if (nextTypes.contains("L01")) { hasAdjacent = true; break; }
                        }
                        if (hasAdjacent) continue;
                    }

                    // Specialty check:
                    // - L04 w/ specialty: strict match unless cross-specialty is permitted & within cap
                    // - Non-L04 w/o specialty: staff must have a specialty (not null)
                    if (specId != null && "L04".equals(shiftTypeId)) {
                        boolean matchesSpecialty = s.getSpecialty() != null
                                && s.getSpecialty().getId().equals(specId);
                        if (!matchesSpecialty) {
                            String reqSpecName = req.getSpecialty() != null
                                    ? req.getSpecialty().getName() : null;
                            if (!l04CrossConfig.isPermittedFor(reqSpecName)) continue;
                            String crossKey = date + "|" + shiftTypeId + "|" + specId;
                            if (crossAssignmentCount.getOrDefault(crossKey, 0)
                                    >= l04CrossConfig.crossCap(required)) continue;
                        }
                    } else if (specId == null && !"L04".equals(shiftTypeId)
                            && s.getSpecialty() == null) {
                        continue;
                    }

		                    int cnt = staffCount.getOrDefault(s.getId(), 0);
		                    int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
		                            ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
		                    if (cnt >= maxShifts) continue;

		                    // Fatigue bonus: more gap = better
		                    // Rotation bonus: prefer staff missing some shift types
		                    Set<String> types = staffTypes.getOrDefault(s.getId(), Collections.emptySet());
		                    int missingTypes = 4 - types.size();
		                    double rotationBonus = missingTypes * 15.0; // 15 pts per missing type
		                    double fatigueBonus = 0;
		                    LocalDate last = staffLastWork.get(s.getId());
		                    if (last != null) {
		                        long gap = date.toEpochDay() - last.toEpochDay();
		                        if (gap >= 1) fatigueBonus = Math.min(gap * 5, 15.0);
		                    }

	                    // L04 per-specialty balance penalty (ADAPTIVE)
	                    double specBalancePenalty = 0;
	                    if (specId != null && "L04".equals(shiftTypeId)) {
	                        int l04InSpec = l04CountBySpec
	                                .getOrDefault(s.getId(), Collections.emptyMap())
	                                .getOrDefault(specId, 0);
	                        // Adaptive factor: giảm penalty khi còn nhiều L04 chưa gán (ưu tiên coverage)
	                        int specTotalReq = totalL04BySpec.getOrDefault(specId, 0);
	                        int specAssigned = assignedL04BySpec.getOrDefault(specId, 0);
	                        double specCoverageGap = specTotalReq > 0 ? (double)(specTotalReq - specAssigned) / specTotalReq : 0;
	                        double specAdaptive = Math.max(0.3, 1.0 - specCoverageGap * 0.7);
	                        specBalancePenalty = l04InSpec * 18.0 * specAdaptive;
	                    }
	                    
                    // Per-type balance penalty for L01/L02/L03 (ADAPTIVE)
                    double typeBalancePenalty = 0;
                    if (!"L04".equals(shiftTypeId)) {
                        int typeCount = typeCountByStaff
                                .getOrDefault(s.getId(), Collections.emptyMap())
                                .getOrDefault(shiftTypeId, 0);
                        // Adaptive factor: giảm penalty khi còn nhiều ca loại này chưa gán
                        int typeTotalReq = totalRequiredByType.getOrDefault(shiftTypeId, 0);
                        int typeAssigned = assignedByType.getOrDefault(shiftTypeId, 0);
                        double typeCoverageGap = typeTotalReq > 0 ? (double)(typeTotalReq - typeAssigned) / typeTotalReq : 0;
                        double typeAdaptive = Math.max(0.3, 1.0 - typeCoverageGap * 0.7);
                        typeBalancePenalty = typeCount * 18.0 * typeAdaptive;
                    }

                    // Non-adaptive per-type penalty (TASK-L01-FAIRNESS Phase A).
                    // Penalizes staff above the running average for this shift type.
                    // Unlike typeBalancePenalty, weight does NOT decrease with coverage gap.
                    // Weight 35/cap 60 (up from 20/40): stronger diff to reduce L02 span from 3 to ≤2.
                    double quotaPenalty = 0;
                    if (!"L04".equals(shiftTypeId)) {
                        int typeCount = typeCountByStaff
                                .getOrDefault(s.getId(), Collections.emptyMap())
                                .getOrDefault(shiftTypeId, 0);
                        int totalAssigned = assignedByType.getOrDefault(shiftTypeId, 0);
                        double runningAvg = numStaff > 0 ? (double) totalAssigned / numStaff : 0;
                        if (runningAvg > 0 && typeCount > runningAvg) {
                            double deviation = (typeCount - runningAvg) / Math.max(runningAvg, 0.5);
                            quotaPenalty = Math.min(deviation * 35.0, 60.0);
                        }
                    }

                    // Inter-type balance penalty (TASK-INTER-TYPE-BALANCE).
                    // Soft objective: penalizes staff where max(L01,L02,L03) - min(L01,L02,L03) is large.
                    // Weight kept low (5.0) — coverage and intra-type fairness take priority.
                    double interTypePenalty = 0;
                    if (interTypeWeight > 0) {
                        Map<String, Integer> tCounts = typeCountByStaff
                                .getOrDefault(s.getId(), Collections.emptyMap());
                        int l01c = tCounts.getOrDefault("L01", 0);
                        int l02c = tCounts.getOrDefault("L02", 0);
                        int l03c = tCounts.getOrDefault("L03", 0);
                        int dev = Math.max(Math.max(l01c, l02c), l03c)
                                - Math.min(Math.min(l01c, l02c), l03c);
                        interTypePenalty = interTypeWeight * dev;
                    }

                    double score = 100 - cnt * 6 + fatigueBonus + rotationBonus
                            - specBalancePenalty - typeBalancePenalty - quotaPenalty - interTypePenalty;
			                    int typeCnt = !"L04".equals(shiftTypeId)
			                            ? typeCountByStaff.getOrDefault(s.getId(), Collections.emptyMap())
			                                    .getOrDefault(shiftTypeId, 0)
			                            : 0;
			                    candidates.add(new ScoredStaff(s.getId(), score, typeCnt, cnt));
			                }
	
	                // Score-based sorting cho tất cả loại
	                candidates.sort((a, b) -> Double.compare(b.score, a.score));
                int assign = Math.min(required, candidates.size());
	                for (int i = 0; i < assign; i++) {
	                    int sid = candidates.get(i).staffId;
	                    String dayKey = sid + "|" + date;
	                    assignedTypesPerDay.computeIfAbsent(dayKey, k -> new HashSet<>()).add(shiftTypeId);
	                    staffCount.merge(sid, 1, Integer::sum);
                    staffTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);
                    staffLastWork.put(sid, date);
                    // Track per-type count for L01/L02/L03 balance
                    typeCountByStaff.computeIfAbsent(sid, k -> new HashMap<>())
                            .merge(shiftTypeId, 1, Integer::sum);
	                    // Track L04 per-specialty count for balance
		                    if (specId != null && "L04".equals(shiftTypeId)) {
                        l04CountBySpec.computeIfAbsent(sid, k -> new HashMap<>())
                                .merge(specId, 1, Integer::sum);
                        // Track cross-specialty assignment for ratio cap
                        Staff assignedStaff = staffMap.get(sid);
                        boolean isCross = assignedStaff != null
                                && assignedStaff.getSpecialty() != null
                                && !assignedStaff.getSpecialty().getId().equals(specId);
                        if (isCross) {
                            String crossKey = date + "|" + shiftTypeId + "|" + specId;
                            crossAssignmentCount.merge(crossKey, 1, Integer::sum);
                        }
                    }
		                    
                // Track assigned counts for adaptive penalty
                assignedByType.merge(shiftTypeId, 1, Integer::sum);
		                    if (specId != null && "L04".equals(shiftTypeId)) {
		                        assignedL04BySpec.merge(specId, 1, Integer::sum);
		                    }
		                    
		                    // Compensation day: nếu gán L01, tính ngày nghỉ bù
		                    if ("L01".equals(shiftTypeId)) {
		                        LocalDate compDate = compensationDateCalculator.calculate(date);
		                        if (compDate != null) {
		                            staffCompDays.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
		                        }
		                    }

	                    Schedule sch = new Schedule();
	                    sch.setStaff(staffMap.get(sid));
	                    sch.setPeriod(period);
	                    sch.setWorkDate(date);
	                    sch.setShiftType(req.getShiftType());
	                    sch.setRequirement(req);
sch.setHasConflict(false);
                    result.add(sch);
                }
            }
        }
        }

// POST-PROCESSING: Ensure every staff has ALL 4 shift types
        // If a staff is missing a type, swap from another type to fill the gap
        // Skip swaps that would create conflicts (consecutive L01, L01+L02 same day, L03+L04 same day)
        Map<Integer, Set<String>> staffTypesFinal = new HashMap<>();
        // P2-8: precompute byStaffType to avoid O(N) result.stream() per swap candidate
        Map<Integer, Map<String, List<Schedule>>> byStaffType = new HashMap<>();
        for (Schedule s : result) {
            int sid = s.getStaff().getId();
            String tid = s.getShiftType().getId();
            staffTypesFinal.computeIfAbsent(sid, k -> new HashSet<>()).add(tid);
            byStaffType.computeIfAbsent(sid, k -> new HashMap<>())
                    .computeIfAbsent(tid, k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<Integer, Set<String>> entry : staffTypesFinal.entrySet()) {
            if (entry.getValue().size() >= 4) continue; // Already has all types

            int sid = entry.getKey();
            Set<String> types = entry.getValue();
            Map<String, List<Schedule>> staffSchedules = byStaffType.getOrDefault(sid, Map.of());
            String[] needed = {"L01", "L02", "L03", "L04"};
            for (String need : needed) {
                if (types.contains(need)) continue;

                // Find a schedule for this staff that we can swap to the missing type
                for (String swapFrom : new String[]{"L04", "L01", "L02", "L03"}) {
                    if (!types.contains(swapFrom)) continue;
                    List<Schedule> fromList = staffSchedules.get(swapFrom);
                    // Don't swap if staff only has 1 of this type
                    if (fromList == null || fromList.size() <= 1) continue;

                    boolean swapped = false;
                    for (Schedule s : fromList) {
                        // Check if swap would create conflict (exclude this schedule from check)
                        if (wouldCreateConflict(result, sid, s.getWorkDate(), need, s, l01Window)) continue;

                        // Swap this schedule to the missing type
                        s.setShiftType(ScheduleConflictUtils.findShiftType(need, requirements));
                        // Update requirement to match new shift type
ShiftRequirement newReq = ScheduleConflictUtils.findMatchingRequirement(
                                    s.getStaff(), s.getWorkDate(), need, requirements);
                        if (newReq != null) s.setRequirement(newReq);
                        types.add(need);
                        // Move schedule between buckets
                        fromList.remove(s);
                        staffSchedules.computeIfAbsent(need, k -> new ArrayList<>()).add(s);
                        if (fromList.isEmpty()) types.remove(swapFrom);
                        swapped = true;
                        break;
                    }
                    if (swapped) break; // Successfully swapped
                }
            }
        }

        // POST-ROTATION: Fix any consecutive L01 violations created by rotation
        // If staff has L01 on adjacent days, swap the newer one back to L04
        // P2-8: precompute L01 dates by staff → O(1) per schedule instead of O(N) inner loop
        Map<Integer, Set<LocalDate>> l01DatesByStaff = new HashMap<>();
        for (Schedule s : result) {
            if ("L01".equals(s.getShiftType().getId())) {
                l01DatesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>())
                        .add(s.getWorkDate());
            }
        }
        for (int i = result.size() - 1; i >= 0; i--) {
            Schedule s = result.get(i);
            if (!"L01".equals(s.getShiftType().getId())) continue;
            int sid = s.getStaff().getId();
            LocalDate date = s.getWorkDate();
            Set<LocalDate> l01Dates = l01DatesByStaff.get(sid);
            if (l01Dates == null) continue;
            // Check window: L01 cách nhau < l01Window ngày là vi phạm
            boolean hasAdjacent = false;
            for (int dt = 1; dt <= l01Window; dt++) {
                if (l01Dates.contains(date.minusDays(dt)) || l01Dates.contains(date.plusDays(dt))) {
                    hasAdjacent = true;
                    break;
                }
            }
            if (hasAdjacent) {
                // Found consecutive L01 - swap this one back to L04
                // P1-4: null-check findShiftType — no L04 req on this date → skip swap
                com.hospital.scheduler.entity.ShiftType l04Type = ScheduleConflictUtils.findShiftType("L04", requirements);
                if (l04Type == null) continue;
                s.setShiftType(l04Type);
ShiftRequirement l04Req = ScheduleConflictUtils.findMatchingRequirement(
                            s.getStaff(), date, "L04", requirements);
                if (l04Req != null) s.setRequirement(l04Req);
                // Update type tracking
                Set<String> st = staffTypesFinal.getOrDefault(sid, new HashSet<>());
                st.remove("L01");
                st.add("L04");
                l01Dates.remove(date);
            }
        }

        // POST-PROCESSING 2: Gap-filling pass
        // Re-scan unassigned requirements and try to fill them with relaxed scoring.
        // This boosts coverage while the per-type penalties from the main pass already
        // established a balanced baseline.
        // Rebuild per-type counts from final result for gap-fill scoring
        Map<Integer, Map<String, Integer>> typeCountGap = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> l04SpecGap = new HashMap<>();
        Map<Integer, Integer> totalCountGap = new HashMap<>();
        for (Schedule s : result) {
            int sid = s.getStaff().getId();
            String tid = s.getShiftType().getId();
            totalCountGap.merge(sid, 1, Integer::sum);
            typeCountGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(tid, 1, Integer::sum);
            if ("L04".equals(tid) && s.getRequirement() != null && s.getRequirement().getSpecialty() != null) {
                int specId = s.getRequirement().getSpecialty().getId();
                l04SpecGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(specId, 1, Integer::sum);
            }
        }

        int gapFilled = 0;
        // P2-8: precompute assigned-by-req-key count → O(1) per req instead of O(N) stream scan
        Map<String, Integer> assignedByReqKey = new HashMap<>();
        for (Schedule s : result) {
            Integer sid = s.getRequirement() != null && s.getRequirement().getSpecialty() != null
                    ? s.getRequirement().getSpecialty().getId() : null;
            assignedByReqKey.merge(reqKey(s.getShiftType().getId(), s.getWorkDate(), sid),
                    1, Integer::sum);
        }
        for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
            LocalDate date = e.getKey();
            for (ShiftRequirement req : e.getValue()) {
                // O(1) lookup of how many are already assigned for this requirement
                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
                int alreadyAssigned = assignedByReqKey.getOrDefault(
                        reqKey(req.getShiftType().getId(), date, specId), 0);
                if (alreadyAssigned >= req.getRequiredStaffCount()) continue;

                int stillNeeded = req.getRequiredStaffCount() - alreadyAssigned;
                String shiftTypeId = req.getShiftType().getId();

		                // Score candidates with relaxed penalty (half of main pass)
		                List<ScoredStaff> gapCandidates = new ArrayList<>();
		                for (Staff s : activeStaff) {
		                    if (excludedStaffIds != null && excludedStaffIds.contains(s.getId())) continue;
		                    // Không block nếu đã có ca ngày đó, chỉ block nếu conflict
		                    // (L01+L02, L03+L04, hoặc cùng 2 lần 1 loại ca)
		                    if (wouldCreateConflict(result, s.getId(), date, shiftTypeId, null, l01Window)) continue;
		                    // Check duplicate: không gán 2 ca cùng loại cho 1 NS trong 1 ngày
		                    boolean alreadyHasSameType = result.stream()
		                            .anyMatch(r -> r.getStaff().getId() == s.getId() && r.getWorkDate().equals(date)
		                                    && r.getShiftType().getId().equals(shiftTypeId));
			                    if (alreadyHasSameType) continue;
			                    // Compensation day: không gán ca vào ngày nghỉ bù
			                    Set<LocalDate> compDays = staffCompDays.get(s.getId());
			                    if (compDays != null && compDays.contains(date)) continue;
			                    // L04 specialty with cross-specialty support (TASK-02)
			                    if (specId != null && "L04".equals(shiftTypeId)) {
			                        boolean matchesSpecialty = s.getSpecialty() != null
			                                && s.getSpecialty().getId().equals(specId);
			                        if (!matchesSpecialty) {
			                            String reqSpecName = req.getSpecialty() != null
			                                    ? req.getSpecialty().getName() : null;
			                            if (!l04CrossConfig.isPermittedFor(reqSpecName)) continue;
			                            String crossKey = date + "|" + shiftTypeId + "|" + specId;
			                            if (crossAssignmentCount.getOrDefault(crossKey, 0)
			                                    >= l04CrossConfig.crossCap(req.getRequiredStaffCount())) continue;
			                        }
			                    } else if (specId == null && !"L04".equals(shiftTypeId)
			                            && s.getSpecialty() == null) { continue; }
			                    // Eligibility check cho L01/L02/L03: staff KHÔNG có chuyên khoa không được gán
			                    if (specId == null && !"L04".equals(shiftTypeId)
			                            && s.getSpecialty() == null) continue;
			                    // maxShiftsPerDay — HARD cap tổng số ca mỗi nhân sự mỗi ngày (gap-fill)
			                    if (runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0) {
			                        long todayCount = result.stream()
			                                .filter(r -> r.getStaff().getId() == s.getId() && r.getWorkDate().equals(date))
			                                .count();
			                        if (todayCount >= runtimeConfig.getMaxShiftsPerDay()) continue;
			                    }

	int cnt = totalCountGap.getOrDefault(s.getId(), 0);
                    int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                            ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
		                    // P1-5: sync gap-fill cap with main pass (>= maxShifts) — gap-fill used to allow +1 overshoot
		                    if (cnt >= maxShifts) continue;

	                    Set<String> types = staffTypes.getOrDefault(s.getId(), Collections.emptySet());
	                    int missingTypes = 4 - types.size();
	                    double rotationBonus = missingTypes * 10.0;
double fatigueBonus = 0;
		                    LocalDate last = staffLastWork.get(s.getId());
		                    if (last != null) {
		                        long gap = date.toEpochDay() - last.toEpochDay();
		                        if (gap >= 1) fatigueBonus = Math.min(gap * 3, 10.0);
		                    }
		                    double totalPenalty = cnt * 6.0; // Tăng: 5→6
		                    double typePenalty = 0;
		                    if (!"L04".equals(shiftTypeId)) {
		                        int typeCnt = typeCountGap.getOrDefault(s.getId(), Collections.emptyMap())
		                                .getOrDefault(shiftTypeId, 0);
		                        // Adaptive: giảm penalty khi còn nhiều ca chưa gán
		                        int typeTotalReq = totalRequiredByType.getOrDefault(shiftTypeId, 0);
		                        int typeAssigned = assignedByType.getOrDefault(shiftTypeId, 0) + alreadyAssigned;
		                        double typeGap = typeTotalReq > 0 ? (double)(typeTotalReq - typeAssigned) / typeTotalReq : 0;
		                        double typeAdaptive = Math.max(0.3, 1.0 - typeGap * 0.7);
		                        typePenalty = typeCnt * 15.0 * typeAdaptive;
	                    }
			                    double specPenalty = 0;
			                    if (specId != null && "L04".equals(shiftTypeId)) {
			                        int l04Spec = l04SpecGap.getOrDefault(s.getId(), Collections.emptyMap())
			                                .getOrDefault(specId, 0);
			                        // Adaptive: giảm penalty khi còn nhiều L04 chưa gán trong specialty này
			                        int specTotalReq = totalL04BySpec.getOrDefault(specId, 0);
			                        int specAssigned = assignedL04BySpec.getOrDefault(specId, 0) + alreadyAssigned;
			                        double specGap = specTotalReq > 0 ? (double)(specTotalReq - specAssigned) / specTotalReq : 0;
			                        double specAdaptive = Math.max(0.3, 1.0 - specGap * 0.7);
			                        specPenalty = l04Spec * 18.0 * specAdaptive;
			                    }

                    // Inter-type balance penalty in gap-fill (TASK-INTER-TYPE-BALANCE)
                    double gapInterTypePenalty = 0;
                    if (interTypeWeight > 0) {
                        Map<String, Integer> tCounts = typeCountGap
                                .getOrDefault(s.getId(), Collections.emptyMap());
                        int l01c = tCounts.getOrDefault("L01", 0);
                        int l02c = tCounts.getOrDefault("L02", 0);
                        int l03c = tCounts.getOrDefault("L03", 0);
                        int dev = Math.max(Math.max(l01c, l02c), l03c)
                                - Math.min(Math.min(l01c, l02c), l03c);
                        gapInterTypePenalty = interTypeWeight * dev;
                    }

			                    double score = 100 - totalPenalty + fatigueBonus + rotationBonus
			                            - typePenalty - specPenalty - gapInterTypePenalty;
                    // Per-type penalty in gap-fill (TASK-L01-FAIRNESS Phase A)
                    if (!"L04".equals(shiftTypeId)) {
                        int typeCntGapVal = typeCountGap.getOrDefault(s.getId(), Collections.emptyMap())
                                .getOrDefault(shiftTypeId, 0);
                        int totalAssignedGap = assignedByType.getOrDefault(shiftTypeId, 0);
                        double runningAvgGap = numStaff > 0 ? (double) totalAssignedGap / numStaff : 0;
                        if (runningAvgGap > 0 && typeCntGapVal > runningAvgGap) {
                            double deviation = (typeCntGapVal - runningAvgGap) / Math.max(runningAvgGap, 0.5);
                            score -= Math.min(deviation * 35.0, 60.0);
                        }
                    }
		                    int typeCntGap = typeCountGap.getOrDefault(s.getId(), Collections.emptyMap())
		                            .getOrDefault(shiftTypeId, 0);
	                    gapCandidates.add(new ScoredStaff(s.getId(), score, typeCntGap, cnt));
	                }
	
	                    gapCandidates.sort((a, b) -> Double.compare(b.score, a.score));
                int assign = Math.min(stillNeeded, gapCandidates.size());
	                for (int i = 0; i < assign; i++) {
	                    int sid = gapCandidates.get(i).staffId;
	                    totalCountGap.merge(sid, 1, Integer::sum);
                    typeCountGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(shiftTypeId, 1, Integer::sum);
                    staffTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);
                    staffLastWork.put(sid, date);
	                    if (specId != null && "L04".equals(shiftTypeId)) {
	                        l04SpecGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(specId, 1, Integer::sum);
	                        // Track cross-specialty for ratio cap (TASK-02)
	                        Staff assignedStaff = staffMap.get(sid);
	                        boolean isCross = assignedStaff != null
	                                && assignedStaff.getSpecialty() != null
	                                && !assignedStaff.getSpecialty().getId().equals(specId);
	                        if (isCross) {
	                            String crossKey = date + "|" + shiftTypeId + "|" + specId;
	                            crossAssignmentCount.merge(crossKey, 1, Integer::sum);
	                        }
	                    }
	
	Schedule sch = new Schedule();
                    sch.setStaff(staffMap.get(sid));
                    sch.setPeriod(period);
                    sch.setWorkDate(date);
                    sch.setShiftType(req.getShiftType());
                    sch.setRequirement(req);
                    sch.setHasConflict(false);
                    result.add(sch);
                    // Keep assignedTypesPerDay + gap counters + req counts in sync
                    assignedTypesPerDay.computeIfAbsent(sid + "|" + date, k -> new HashSet<>()).add(shiftTypeId);
                    assignedByReqKey.merge(reqKey(shiftTypeId, date, specId), 1, Integer::sum);
                    if ("L01".equals(shiftTypeId)) {
                        LocalDate compDate = compensationDateCalculator.calculate(date);
                        if (compDate != null) {
                            staffCompDays.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
                        }
                    }
                    gapFilled++;
                }
            }
        }
        if (gapFilled > 0) {
            log.info("EnhancedGreedy: gap-fill added {} schedules", gapFilled);
        }

        // POST-PROCESSING 3: Per-type move rebalance (TASK-L01-FAIRNESS Phase A+C).
        // Moves L02/L03 shifts from overloaded → underloaded staff.
        // Unlike swap, move changes total count per staff but improves per-type balance.
        // Accepts maxShifts exceed during rebalance (total schedule count unchanged).
        perTypeMoveRebalance(result, staffMap, requirements, l01Window, runtimeConfig);

        log.info("EnhancedGreedy: {} schedules in {}ms (rotation + gap-fill + rebalance done)",
                result.size(), System.currentTimeMillis() - start);
        return result;
    }

    // Records can't declare non-canonical constructors in Java; use a static class instead.
    private static final class ScoredStaff {
        final int staffId;
        final double score;
        final int typeCount;
        final int totalCount;
        ScoredStaff(int staffId, double score) { this(staffId, score, 0, 0); }
        ScoredStaff(int staffId, double score, int typeCount, int totalCount) {
            this.staffId = staffId;
            this.score = score;
            this.typeCount = typeCount;
            this.totalCount = totalCount;
        }
    }

    /**
     * Check if swapping a schedule to newType on workDate for staffId would create a conflict.
     * Conflicts: L01+L02 same day, L03+L04 same day, consecutive L01 (within {@code l01Window}).
     * @param excludeSchedule the schedule being swapped (excluded from check)
     */
    private boolean wouldCreateConflict(List<Schedule> schedules, int staffId,
            LocalDate workDate, String newType, Schedule excludeSchedule, int l01Window) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s == excludeSchedule) continue; // Skip the schedule being swapped

            // Same-day conflicts: same-type duplicate, L01↔L02, L03↔L04
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (existingType.equals(newType)
                        || ("L01".equals(newType) && "L02".equals(existingType))
                        || ("L02".equals(newType) && "L01".equals(existingType))
                        || ("L03".equals(newType) && "L04".equals(existingType))
                        || ("L04".equals(newType) && "L03".equals(existingType))) {
                    return true;
                }
            }

            // Consecutive L01 check: window = ceil(overnightRecoveryHours/24)
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                long diff = Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay());
                if (diff <= l01Window) return true;
            }
        }
        return false;
    }

    /** Composite key for grouping assignments by requirement slot: typeId|date[:specId]. */
    private static String reqKey(String typeId, LocalDate date, Integer specId) {
        return typeId + "|" + date + (specId != null ? ":" + specId : "");
    }

    /**
     * Per-type move rebalance: move L02/L03 shifts from staff with most of that
     * type to staff with least. Unlike swap, move changes total count per staff
     * (may temporarily exceed maxShifts). This is safe because the total schedule
     * count is unchanged — we're only reassigning staff on existing schedules.
     *
     * <p>Strategy: for L02 (most imbalanced), find max/min staff, move one shift.
     * Accept if no conflicts. Repeat until balanced or no moves possible.
     *
     * <p>ponytail: extend to L01 when overnight constraints become soft enough.
     */
    private void perTypeMoveRebalance(List<Schedule> schedules, Map<Integer, Staff> staffMap,
                                       List<ShiftRequirement> reqs, int l01Window,
                                       AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        String[] typesToBalance = {"L02", "L03"};
        int totalMoved = 0;
        int maxRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsEg() : 40;
        for (String type : typesToBalance) {
            for (int round = 0; round < maxRounds; round++) {
                Map<Integer, Integer> counts = new HashMap<>();
                for (Schedule s : schedules) {
                    if (type.equals(s.getShiftType().getId())) {
                        counts.merge(s.getStaff().getId(), 1, Integer::sum);
                    }
                }
                if (counts.isEmpty()) break;
                int maxCnt = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                int minCnt = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                if (maxCnt - minCnt <= 1) break;

                int overloaded = -1, underloaded = -1;
                int overVal = Integer.MIN_VALUE, underVal = Integer.MAX_VALUE;
                for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                    if (e.getValue() > overVal) { overVal = e.getValue(); overloaded = e.getKey(); }
                    if (e.getValue() < underVal) { underVal = e.getValue(); underloaded = e.getKey(); }
                }
                if (overloaded < 0 || underloaded < 0 || overloaded == underloaded) break;

                // Find a shift of `type` on overloaded and move to underloaded
                boolean moved = false;
                for (Schedule s : schedules) {
                    if (s.getStaff().getId() != overloaded) continue;
                    if (!type.equals(s.getShiftType().getId())) continue;
                    // Check underloaded can take it (no conflict)
                    if (wouldCreateConflict(schedules, underloaded, s.getWorkDate(), type, s, l01Window)) continue;
                    // Execute move
                    s.setStaff(staffMap.get(underloaded));
                    ShiftRequirement matched = ScheduleConflictUtils.findMatchingRequirement(
                            s.getStaff(), s.getWorkDate(), type, reqs);
                    if (matched != null) s.setRequirement(matched);
                    moved = true;
                    totalMoved++;
                    break;
                }
                if (!moved) break;
            }
        }
        if (totalMoved > 0) {
            log.info("EnhancedGreedy: per-type rebalance moved {} shifts", totalMoved);
        }
    }
}
