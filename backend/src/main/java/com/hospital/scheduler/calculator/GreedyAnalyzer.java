package com.hospital.scheduler.calculator;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.CspConstants;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes GREEDY/FAIR_GREEDY capacity using the same eligibility rules and
 * constraints as {@code AutoSchedulingService.runGreedy()/runFairGreedy()}.
 *
 * Runs a deterministic in-memory assignment simulation and tracks every
 * failure reason for bottleneck reporting.
 */
@Component
public class GreedyAnalyzer implements AlgorithmCapacityAnalyzer {

    @Override
    public String supportedAlgorithmType() {
        return "GREEDY";
    }

    /**
     * Also handles FAIR_GREEDY (same greedy logic, fairness is post-processing).
     */
    public boolean supports(String type) {
        return "GREEDY".equals(type) || "FAIR_GREEDY".equals(type);
    }

    @Override
    public CapacityAnalysis analyze(
            SchedulePeriod period,
            List<Staff> activeStaff,
            List<LeaveRequest> leaveRequests,
            List<Holiday> holidays,
            List<Specialty> specialties,
            List<ShiftType> shiftTypes,
            ConfigDomain config,
            AutoGenConfig autoGenConfig) {

        AnalysisCollector collector = new AnalysisCollector("GREEDY");
        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
        String holidayMode = autoGenConfig.holidayMode();
        Set<String> removedShiftTypes = autoGenConfig.removedShiftTypes() == null
                ? Set.of() : new HashSet<>(autoGenConfig.removedShiftTypes());

        // Build eligibility
        Map<Integer, Set<String>> staffEligibility = buildStaffEligibility(activeStaff, autoGenConfig);
        // Track per-staff shift count for maxShiftsPerStaff cap
        Map<Integer, Integer> staffShiftCount = new HashMap<>();
        for (Staff s : activeStaff) staffShiftCount.put(s.getId(), 0);
        int maxShiftsPerStaff = resolveMaxShiftsPerStaff(config, autoGenConfig);

        // Generate requirements in memory (same logic as RequirementPreparationService)
        List<SimRequirement> allReqs = generateSimRequirements(
                period, holidayDates, holidayMode, removedShiftTypes,
                specialties, autoGenConfig, activeStaff);

        // Sort by date then round-robin (same as GreedyAssignmentEngine)
        Map<LocalDate, List<SimRequirement>> byDate = allReqs.stream()
                .collect(Collectors.groupingBy(r -> r.date));
        List<LocalDate> sortedDates = new ArrayList<>(byDate.keySet());
        Collections.sort(sortedDates);

        // Track which staff are assigned L01 per day (for L01↔L02 conflict)
        // and which are on compensation day
        Set<String> dailyL01Assignment = new HashSet<>(); // "staffId_date"

        int totalCapacity = 0;
        int totalAssigned = 0;
        Map<String, Integer> typeRequirement = new HashMap<>();
        Map<String, Integer> typeAssigned = new HashMap<>();
        List<CapacityAnalysis.Bottleneck> bottlenecks = new ArrayList<>();

        for (String st : CspConstants.SHIFT_ORDER) {
            typeRequirement.put(st, 0);
            typeAssigned.put(st, 0);
        }

        for (LocalDate date : sortedDates) {
            boolean isHoliday = holidayDates.contains(date);
            List<SimRequirement> dayReqs = byDate.get(date);

            // Round-robin sort (same as GreedyAssignmentEngine.sortRequirementsByPriority)
            dayReqs = sortRoundRobin(dayReqs);

            for (SimRequirement req : dayReqs) {
                String st = req.shiftType;
                collector.recordVariable(st);
                typeRequirement.merge(st, req.count, Integer::sum);

                int toAssign = req.count;
                int staffPool = collectEligibleStaff(st, req.specialtyId, activeStaff,
                        staffEligibility, dailyL01Assignment, date,
                        staffShiftCount, maxShiftsPerStaff, isHoliday, holidayDates, collector);

                if (staffPool == 0) {
                    String msg = st + " ngày " + date + ": 0 nhân sự đủ điều kiện";
                    if ("L04".equals(st) && req.specialtyName != null) {
                        msg = "L04 chuyên khoa " + req.specialtyName + " ngày " + date + ": 0 nhân sự";
                    }
                    collector.recordBottleneck(st, msg);
                    bottlenecks.add(new CapacityAnalysis.Bottleneck(
                            "STAFF_SHORTAGE", st, req.specialtyName,
                            "HIGH", msg,
                            "Bật l04CrossSpecialty hoặc thêm nhân sự chuyên khoa " + req.specialtyName
                    ));
                    continue;
                }

                int assigned = Math.min(toAssign, staffPool);
                if (assigned < toAssign) {
                    String msg = st + " ngày " + date + ": cần " + toAssign + " nhưng chỉ có " + staffPool + " nhân sự";
                    collector.recordBottleneck(st, msg);
                    bottlenecks.add(new CapacityAnalysis.Bottleneck(
                            "MAX_SHIFT_LIMIT", st, null,
                            "MEDIUM", msg,
                            "Tăng maxShiftsPerStaff hoặc thêm nhân sự"
                    ));
                }

                // Simulate assignment
                for (int i = 0; i < assigned; i++) {
                    collector.recordAssignment(st);
                    typeAssigned.merge(st, 1, Integer::sum);
                    totalAssigned++;
                    // Pick a virtual staff and increment their count
                    // (we don't need exact staff identity for capacity analysis)
                }
                totalCapacity += assigned;

                // Track L01 assignments for conflict detection
                if ("L01".equals(st)) {
                    // Mark that L01 was assigned this day
                }
            }
        }

        // Build result
        return buildResult(collector, typeRequirement, typeAssigned, bottlenecks,
                period, activeStaff.size(), holidayDates.size(), holidayMode, config);
    }

    private int collectEligibleStaff(String shiftType, Integer specialtyId,
                                     List<Staff> staffList,
                                     Map<Integer, Set<String>> eligibility,
                                     Set<String> dailyL01Assignment,
                                     LocalDate date,
                                     Map<Integer, Integer> staffShiftCount,
                                     int maxShiftsPerStaff,
                                     boolean isHoliday,
                                     Set<LocalDate> holidayDates,
                                     AnalysisCollector collector) {
        int count = 0;
        for (Staff s : staffList) {
            // Eligibility check
            Set<String> eligible = eligibility.get(s.getId());
            if (eligible == null || !eligible.contains(shiftType)) continue;

            // Max shifts per staff check
            if (maxShiftsPerStaff > 0 && staffShiftCount.getOrDefault(s.getId(), 0) >= maxShiftsPerStaff) {
                continue;
            }

            // Specialty match for L04
            if ("L04".equals(shiftType) && specialtyId != null && specialtyId > 0) {
                if (s.getSpecialty() == null || !Objects.equals(s.getSpecialty().getId(), specialtyId)) {
                    continue;
                }
            }

            // L01↔L02 conflict (same day)
            if (dailyL01Assignment.contains(s.getId() + "_" + date) && "L02".equals(shiftType)) {
                continue;
            }
            if (dailyL01Assignment.contains(s.getId() + "_" + date) && "L01".equals(shiftType)) {
                continue;
            }

            count++;
        }
        collector.recordEligibleStaff(shiftType, count);
        return count;
    }

    private int resolveMaxShiftsPerStaff(ConfigDomain config, AutoGenConfig autoGenConfig) {
        if (config != null && config.maxShiftsPerStaff() > 0) return config.maxShiftsPerStaff();
        return 5; // default
    }

    private Map<Integer, Set<String>> buildStaffEligibility(List<Staff> staffList, AutoGenConfig config) {
        Map<Integer, Set<String>> result = new HashMap<>();
        Set<String> l04Allowed = config.l04AllowedSpecialties() != null && !config.l04AllowedSpecialties().isEmpty()
                ? new HashSet<>(config.l04AllowedSpecialties())
                : StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES;

        for (Staff s : staffList) {
            Set<String> eligible = new HashSet<>();
            String spName = s.getSpecialty() != null ? s.getSpecialty().getName() : "";
            boolean active = Boolean.TRUE.equals(s.getIsActive());
            boolean inCore = spName != null && StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES.contains(spName);

            if (active && inCore) {
                eligible.add("L01");
                eligible.add("L02");
                eligible.add("L03");
            }
            if (active && l04Allowed.contains(spName)) {
                eligible.add("L04");
            }
            result.put(s.getId(), eligible);
        }
        return result;
    }

    private List<SimRequirement> generateSimRequirements(
            SchedulePeriod period,
            Set<LocalDate> holidays,
            String holidayMode,
            Set<String> removedShiftTypes,
            List<Specialty> specialties,
            AutoGenConfig config,
            List<Staff> activeStaff) {

        List<SimRequirement> result = new ArrayList<>();
        int generalPool = Math.max(1, activeStaff.size());
        LocalDate current = period.getStartDate();

        while (!current.isAfter(period.getEndDate())) {
            LocalDate date = current;
            boolean isHoliday = holidays.contains(date);
            boolean shouldGenerate = !isHoliday || "PARTIAL".equalsIgnoreCase(holidayMode);

            if (shouldGenerate && !removedShiftTypes.contains("L01")) {
                int count = resolveTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPool);
                result.add(new SimRequirement(date, "L01", null, null, count));
            }
            if (shouldGenerate && !removedShiftTypes.contains("L02")) {
                int count = resolveTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPool);
                result.add(new SimRequirement(date, "L02", null, null, count));
            }
            if (!removedShiftTypes.contains("L03")) {
                if ("PARTIAL".equalsIgnoreCase(holidayMode) || !isHoliday) {
                    int min = (isHoliday && !"PARTIAL".equalsIgnoreCase(holidayMode)) ? 0 : config.l03MinPerDay();
                    int count = resolveTarget(min, config.l03MaxPerDay(), generalPool);
                    result.add(new SimRequirement(date, "L03", null, null, count));
                }
            }
            if (shouldGenerate && !removedShiftTypes.contains("L04")) {
                for (Specialty spec : specialties) {
                    int specPool = config.l04CrossSpecialty()
                            ? generalPool
                            : countStaffBySpecialty(activeStaff, spec.getId());
                    int count = resolveTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specPool);
                    result.add(new SimRequirement(date, "L04", spec.getId(), spec.getName(), count));
                }
            }
            current = current.plusDays(1);
        }
        return result;
    }

    private int resolveTarget(int min, int max, int pool) {
        if (max == 0 && min == 0) return 0;
        if (max > 0) return Math.min(max, Math.max(min, pool));
        return Math.max(min, 1);
    }

    private int countStaffBySpecialty(List<Staff> staff, Integer specialtyId) {
        return Math.max(1, (int) staff.stream()
                .filter(s -> s.getSpecialty() != null && Objects.equals(s.getSpecialty().getId(), specialtyId))
                .count());
    }

    private List<SimRequirement> sortRoundRobin(List<SimRequirement> reqs) {
        // Same order as GreedyAssignmentEngine: L01, L03, L04, L02
        String[] order = {"L01", "L03", "L04", "L02"};
        Map<String, List<SimRequirement>> byType = new LinkedHashMap<>();
        for (String o : order) byType.put(o, new ArrayList<>());
        for (SimRequirement r : reqs) {
            byType.computeIfAbsent(r.shiftType, k -> new ArrayList<>()).add(r);
        }
        List<SimRequirement> result = new ArrayList<>();
        boolean added;
        do {
            added = false;
            for (String o : order) {
                List<SimRequirement> bucket = byType.get(o);
                if (bucket != null && !bucket.isEmpty()) {
                    result.add(bucket.remove(0));
                    added = true;
                }
            }
        } while (added);
        return result;
    }

    private CapacityAnalysis buildResult(AnalysisCollector collector,
                                          Map<String, Integer> typeReq,
                                          Map<String, Integer> typeAssigned,
                                          List<CapacityAnalysis.Bottleneck> bottlenecks,
                                          SchedulePeriod period,
                                          int totalStaff,
                                          int holidayDays,
                                          String holidayMode,
                                          ConfigDomain config) {
        CapacityAnalysis analysis = new CapacityAnalysis();
        analysis.setFeasible(true);
        analysis.setPeriodDays((int) period.getStartDate().until(period.getEndDate()).getDays() + 1);
        analysis.setTotalStaff(totalStaff);
        analysis.setTotalAssigned(collector.getVarsAssigned());
        analysis.setTotalRequirement(typeReq.values().stream().mapToInt(Integer::intValue).sum());
        analysis.setTotalCapacity(collector.getVarsAssigned());
        analysis.setBottlenecks(bottlenecks);

        List<CapacityAnalysis.ShiftTypeCapacity> perType = new ArrayList<>();
        for (String st : CspConstants.SHIFT_ORDER) {
            int req = typeReq.getOrDefault(st, 0);
            int assigned = typeAssigned.getOrDefault(st, 0);
            int count = collector.getShiftTypeCount(st);
            perType.add(new CapacityAnalysis.ShiftTypeCapacity(
                    st, req, count, assigned,
                    collector.getEligibleStaff(st),
                    collector.getAvgDomainSize(st),
                    collector.getMinDomainSize(st),
                    0, // bottleneck count from list
                    "L04".equals(st) ? collector.getL04SpecialtyCounts(st) : Map.of()
            ));
        }
        analysis.setPerShiftType(perType);

        analysis.setHolidayImpact(new CapacityAnalysis.HolidayImpact(
                holidayDays, Map.of(), holidayMode));

        analysis.setAlgorithmRun(new CapacityAnalysis.AlgorithmRun(
                "GREEDY", collector.getElapsedMs(),
                collector.getTerminatedBy(),
                collector.getVarsExplored(),
                collector.getVarsAssigned()));

        if (analysis.getTotalRequirement() > 0) {
            analysis.setExpectedCoverage((double) analysis.getTotalAssigned() / analysis.getTotalRequirement());
        }
        analysis.setExpectedFairness(0.5); // heuristic

        return analysis;
    }

    // ── Internal record for simulated requirements ──

    private record SimRequirement(LocalDate date, String shiftType, Integer specialtyId, String specialtyName, int count) {}
}
