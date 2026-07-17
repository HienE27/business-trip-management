package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
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
public class EnhancedGreedyScheduler {

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
	        Map<Integer, Staff> staffMap = activeStaff.stream()
	                .collect(Collectors.toMap(Staff::getId, s -> s));
	        Map<Integer, List<LocalDate>> staffLastWork = new HashMap<>();
	        Map<Integer, Integer> staffCount = new HashMap<>();
	        Map<Integer, Set<String>> staffTypes = new HashMap<>(); // rotation tracking
	        // L04 per-specialty balance: staffId → {specialtyId → l04Count}
	        Map<Integer, Map<Integer, Integer>> l04CountBySpec = new HashMap<>();
		        // Per-type balance: staffId → {shiftTypeId → count} for L01/L02/L03 balance
		        Map<Integer, Map<String, Integer>> typeCountByStaff = new HashMap<>();
		        // Track shift types per staff per day để kiểm tra conflict (L01+L02, L03+L04)
		        Map<String, Set<String>> assignedTypesPerDay = new HashMap<>(); // "staffId|date" → {L01, L02, ...}

        // Group by date+shift
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate, TreeMap::new, Collectors.toList()));

        List<Schedule> result = new ArrayList<>();

        for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
            LocalDate date = e.getKey();
            for (ShiftRequirement req : e.getValue()) {
                String shiftTypeId = req.getShiftType().getId();
                int required = req.getRequiredStaffCount();
                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

                // Score candidates: fatigue = gap since last work
                List<ScoredStaff> candidates = new ArrayList<>();
	                for (Staff s : activeStaff) {
	                    if (excludedStaffIds != null && excludedStaffIds.contains(s.getId())) continue;
	                    
	                    // Conflict check: L01+L02 cấm, L03+L04 cấm
	                    String dayKey = s.getId() + "|" + date;
	                    Set<String> todayTypes = assignedTypesPerDay.getOrDefault(dayKey, Collections.emptySet());
	                    if (("L01".equals(shiftTypeId) && todayTypes.contains("L02"))
	                            || ("L02".equals(shiftTypeId) && todayTypes.contains("L01"))
	                            || ("L03".equals(shiftTypeId) && todayTypes.contains("L04"))
	                            || ("L04".equals(shiftTypeId) && todayTypes.contains("L03"))) {
	                        continue;
	                    }
	                    
	                    // Specialty check: chỉ cho đúng specialty
	                    if (specId != null && (s.getSpecialty() == null || !s.getSpecialty().getId().equals(specId))) continue;

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
		                    List<LocalDate> lastDates = staffLastWork.get(s.getId());
		                    if (lastDates != null && !lastDates.isEmpty()) {
		                        LocalDate last = lastDates.get(lastDates.size() - 1);
		                        long gap = date.toEpochDay() - last.toEpochDay();
		                        if (gap >= 1) fatigueBonus = Math.min(gap * 5, 15.0);
		                    }

		                    // L04 per-specialty balance penalty: staff who already have many
		                    // L04 shifts in this specialty get penalized to spread assignments
		                    double specBalancePenalty = 0;
		                    if (specId != null && "L04".equals(shiftTypeId)) {
		                        int l04InSpec = l04CountBySpec
		                                .getOrDefault(s.getId(), Collections.emptyMap())
		                                .getOrDefault(specId, 0);
		                        // Penalty scales with how many L04 they already have in this spec
		                        specBalancePenalty = l04InSpec * 9.0; // 9 pts per existing L04 in spec
		                    }

		                    // Per-type balance penalty for L01/L02/L03: staff who already have
		                    // many shifts of this type get penalized to spread assignments evenly
		                    double typeBalancePenalty = 0;
		                    if (!"L04".equals(shiftTypeId)) {
		                        int typeCount = typeCountByStaff
		                                .getOrDefault(s.getId(), Collections.emptyMap())
		                                .getOrDefault(shiftTypeId, 0);
		                        typeBalancePenalty = typeCount * 9.0; // 9 pts per existing shift of this type
			                    }

			                    double score = 100 - cnt * 6 + fatigueBonus + rotationBonus
			                            - specBalancePenalty - typeBalancePenalty;
			                    candidates.add(new ScoredStaff(s.getId(), score));
		                }

                candidates.sort((a, b) -> Double.compare(b.score, a.score));
                int assign = Math.min(required, candidates.size());
	                for (int i = 0; i < assign; i++) {
	                    int sid = candidates.get(i).staffId;
	                    String dayKey = sid + "|" + date;
	                    assignedTypesPerDay.computeIfAbsent(dayKey, k -> new HashSet<>()).add(shiftTypeId);
	                    staffCount.merge(sid, 1, Integer::sum);
                    staffTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);
                    staffLastWork.computeIfAbsent(sid, k -> new ArrayList<>()).add(date);
                    // Track per-type count for L01/L02/L03 balance
                    typeCountByStaff.computeIfAbsent(sid, k -> new HashMap<>())
                            .merge(shiftTypeId, 1, Integer::sum);
                    // Track L04 per-specialty count for balance
	                    if (specId != null && "L04".equals(shiftTypeId)) {
	                        l04CountBySpec.computeIfAbsent(sid, k -> new HashMap<>())
	                                .merge(specId, 1, Integer::sum);
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

        // POST-PROCESSING: Ensure every staff has ALL 4 shift types
        // If a staff is missing a type, swap from another type to fill the gap
        // Skip swaps that would create conflicts (consecutive L01, L01+L02 same day, L03+L04 same day)
        Map<Integer, Set<String>> staffTypesFinal = new HashMap<>();
        for (Schedule s : result) {
            staffTypesFinal.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>())
                    .add(s.getShiftType().getId());
        }
        for (Map.Entry<Integer, Set<String>> entry : staffTypesFinal.entrySet()) {
            if (entry.getValue().size() >= 4) continue; // Already has all types

            int sid = entry.getKey();
            Set<String> types = entry.getValue();
            String[] needed = {"L01", "L02", "L03", "L04"};
            for (String need : needed) {
                if (types.contains(need)) continue;

                // Find a schedule for this staff that we can swap to the missing type
                for (String swapFrom : new String[]{"L04", "L01", "L02", "L03"}) {
                    if (!types.contains(swapFrom)) continue;
                    // Don't swap if staff only has1 of this type
                    long count = result.stream()
                            .filter(s2 -> s2.getStaff().getId() == sid
                                    && swapFrom.equals(s2.getShiftType().getId()))
                            .count();
                    if (count <= 1) continue;

                    for (Schedule s : result) {
                        if (s.getStaff().getId() == sid && swapFrom.equals(s.getShiftType().getId())) {
                            // Check if swap would create conflict (exclude this schedule from check)
                            if (wouldCreateConflict(result, sid, s.getWorkDate(), need, s)) continue;

                            // Swap this schedule to the missing type
                            s.setShiftType(findShiftType(need, requirements));
                            // Update requirement to match new shift type
                            ShiftRequirement newReq = findMatchingRequirement(
                                    s.getWorkDate(), need, s.getStaff().getSpecialty(), requirements);
                            if (newReq != null) s.setRequirement(newReq);
                            types.add(need);
                            // Remove one from the old type (if count drops to0, remove from set)
                            long newCount = result.stream()
                                    .filter(s2 -> s2.getStaff().getId() == sid
                                            && swapFrom.equals(s2.getShiftType().getId()))
                                    .count();
                            if (newCount <= 0) types.remove(swapFrom);
                            break;
                        }
                    }
                    if (types.contains(need)) break; // Successfully swapped
                }
            }
        }

        // POST-ROTATION: Fix any consecutive L01 violations created by rotation
        // If staff has L01 on adjacent days, swap the newer one back to L04
        for (int i = result.size() - 1; i >= 0; i--) {
            Schedule s = result.get(i);
            if (!"L01".equals(s.getShiftType().getId())) continue;
            int sid = s.getStaff().getId();
            LocalDate date = s.getWorkDate();
            // Check for L01 on adjacent days
            for (Schedule other : result) {
                if (other == s || other.getStaff().getId() != sid) continue;
                if (!"L01".equals(other.getShiftType().getId())) continue;
                long diff = Math.abs(other.getWorkDate().toEpochDay() - date.toEpochDay());
                if (diff == 1) {
                    // Found consecutive L01 - swap this one back to L04
                    s.setShiftType(findShiftType("L04", requirements));
                    ShiftRequirement l04Req = findMatchingRequirement(
                            date, "L04", s.getStaff().getSpecialty(), requirements);
                    if (l04Req != null) s.setRequirement(l04Req);
                    // Update type tracking
                    Set<String> st = staffTypesFinal.getOrDefault(sid, new HashSet<>());
                    st.remove("L01");
                    st.add("L04");
                    break;
                }
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
        for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
            LocalDate date = e.getKey();
            for (ShiftRequirement req : e.getValue()) {
                // Count how many are already assigned for this requirement
                String rKey = req.getShiftType().getId() + "|" + date
                        + (req.getSpecialty() != null ? ":" + req.getSpecialty().getId() : "");
                long alreadyAssigned = result.stream()
                        .filter(s -> s.getWorkDate().equals(date)
                                && s.getShiftType().getId().equals(req.getShiftType().getId()))
                        .filter(s -> {
                            if (req.getSpecialty() == null) return true;
                            return s.getRequirement() != null
                                    && s.getRequirement().getSpecialty() != null
                                    && s.getRequirement().getSpecialty().getId().equals(req.getSpecialty().getId());
                        })
                        .count();
                if (alreadyAssigned >= req.getRequiredStaffCount()) continue;

                int stillNeeded = req.getRequiredStaffCount() - (int) alreadyAssigned;
                String shiftTypeId = req.getShiftType().getId();
                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

		                // Score candidates with relaxed penalty (half of main pass)
		                List<ScoredStaff> gapCandidates = new ArrayList<>();
		                for (Staff s : activeStaff) {
		                    if (excludedStaffIds != null && excludedStaffIds.contains(s.getId())) continue;
		                    // Không block nếu đã có ca ngày đó, chỉ block nếu conflict
		                    // (L01+L02, L03+L04, hoặc cùng 2 lần 1 loại ca)
		                    if (wouldCreateConflict(result, s.getId(), date, shiftTypeId, null)) continue;
		                    // Check duplicate: không gán 2 ca cùng loại cho 1 NS trong 1 ngày
		                    boolean alreadyHasSameType = result.stream()
		                            .anyMatch(r -> r.getStaff().getId() == s.getId() && r.getWorkDate().equals(date)
		                                    && r.getShiftType().getId().equals(shiftTypeId));
		                    if (alreadyHasSameType) continue;
	                    // Cross-specialty cho L04: không cho phép, chỉ đúng specialty
	                    if (specId != null && (s.getSpecialty() == null || !s.getSpecialty().getId().equals(specId))) continue;

                    int cnt = totalCountGap.getOrDefault(s.getId(), 0);
                    int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                            ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
	                    // Gap-fill: penalties như main pass để giữ balance
	                    if (cnt >= maxShifts + 1) continue;

	                    Set<String> types = staffTypes.getOrDefault(s.getId(), Collections.emptySet());
	                    int missingTypes = 4 - types.size();
	                    double rotationBonus = missingTypes * 10.0;
	                    double fatigueBonus = 0;
	                    List<LocalDate> lastDates = staffLastWork.get(s.getId());
	                    if (lastDates != null && !lastDates.isEmpty()) {
	                        LocalDate last = lastDates.get(lastDates.size() - 1);
	                        long gap = date.toEpochDay() - last.toEpochDay();
	                        if (gap >= 1) fatigueBonus = Math.min(gap * 3, 10.0);
	                    }
	                    double totalPenalty = cnt * 5.0; // Tăng: 5 thay vì 3
	                    double typePenalty = 0;
	                    if (!"L04".equals(shiftTypeId)) {
	                        int typeCnt = typeCountGap.getOrDefault(s.getId(), Collections.emptyMap())
	                                .getOrDefault(shiftTypeId, 0);
	                        typePenalty = typeCnt * 7.0; // Tăng: 7 thay vì 4
                    }
	                    double specPenalty = 0;
	                    if (specId != null && "L04".equals(shiftTypeId)) {
	                        int l04Spec = l04SpecGap.getOrDefault(s.getId(), Collections.emptyMap())
	                                .getOrDefault(specId, 0);
		                        specPenalty = l04Spec * 9.0; // Tăng: 9 thay vì 5
	                    }

	                    double score = 100 - totalPenalty + fatigueBonus + rotationBonus - typePenalty - specPenalty;
                    gapCandidates.add(new ScoredStaff(s.getId(), score));
                }

                gapCandidates.sort((a, b) -> Double.compare(b.score, a.score));
                int assign = Math.min(stillNeeded, gapCandidates.size());
	                for (int i = 0; i < assign; i++) {
	                    int sid = gapCandidates.get(i).staffId;
	                    totalCountGap.merge(sid, 1, Integer::sum);
                    typeCountGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(shiftTypeId, 1, Integer::sum);
                    staffTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);
                    staffLastWork.computeIfAbsent(sid, k -> new ArrayList<>()).add(date);
                    if (specId != null && "L04".equals(shiftTypeId)) {
                        l04SpecGap.computeIfAbsent(sid, k -> new HashMap<>()).merge(specId, 1, Integer::sum);
                    }

	                    Schedule sch = new Schedule();
	                    sch.setStaff(staffMap.get(sid));
	                    sch.setPeriod(period);
	                    sch.setWorkDate(date);
	                    sch.setShiftType(req.getShiftType());
	                    sch.setRequirement(req);
	                    sch.setHasConflict(false);
	                    result.add(sch);
	                    gapFilled++;
                }
            }
        }
        if (gapFilled > 0) {
            log.info("EnhancedGreedy: gap-fill added {} schedules", gapFilled);
        }

        log.info("EnhancedGreedy: {} schedules in {}ms (rotation + gap-fill done)",
                result.size(), System.currentTimeMillis() - start);
        return result;
    }

    private record ScoredStaff(int staffId, double score) {}

    /**
     * Check if swapping a schedule to newType on workDate for staffId would create a conflict.
     * Conflicts: L01+L02 same day, L03+L04 same day, consecutive L01.
     * @param excludeSchedule the schedule being swapped (excluded from check)
     */
    private boolean wouldCreateConflict(List<Schedule> schedules, int staffId,
            LocalDate workDate, String newType, Schedule excludeSchedule) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s == excludeSchedule) continue; // Skip the schedule being swapped

            // Same-day conflicts
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (("L01".equals(newType) && "L02".equals(existingType))
                        || ("L02".equals(newType) && "L01".equals(existingType))
                        || ("L03".equals(newType) && "L04".equals(existingType))
                        || ("L04".equals(newType) && "L03".equals(existingType))) {
                    return true;
                }
            }

            // Consecutive L01 check
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                long diff = Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay());
                if (diff == 1) return true;
            }
        }
        return false;
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    /**
     * Find matching requirement for a given date, shiftType, and optional specialty.
     * Prefers the requirement whose specialty matches the staff's specialty.
     */
    private ShiftRequirement findMatchingRequirement(LocalDate workDate, String shiftTypeId,
            Specialty specialty, List<ShiftRequirement> reqs) {
        List<ShiftRequirement> candidates = reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
                        && r.getWorkDate().equals(workDate))
                .toList();
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        // Prefer requirement whose specialty matches
        if (specialty != null) {
            for (ShiftRequirement r : candidates) {
                if (r.getSpecialty() != null && r.getSpecialty().getId().equals(specialty.getId())) {
                    return r;
                }
            }
        }
        return candidates.get(0);
    }
}
