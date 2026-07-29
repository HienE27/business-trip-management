package com.hospital.scheduler.calculator;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.dto.ConfigCalculatorRequest;
import com.hospital.scheduler.dto.ConfigCalculatorResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the 3 calculator modes by delegating to the appropriate
 * AlgorithmCapacityAnalyzer implementations.
 *
 * Mode 1: Config + Algorithm → Capacity
 * Mode 2: Target + Algorithm → Config
 * Mode 3: Target → Config + Algorithm (best recommendation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigCalculatorOrchestrator {

    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final ConfigService configService;

    private final List<AlgorithmCapacityAnalyzer> analyzers;

    /**
     * Main entry point — dispatches by mode.
     */
    @Transactional(readOnly = true)
    public ConfigCalculatorResponse calculate(ConfigCalculatorRequest request) {
        if (request.getPeriodId() == null) {
            throw new IllegalArgumentException("periodId là bắt buộc");
        }

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kỳ lịch: " + request.getPeriodId()));

        // Load data once
        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findByStatus(LeaveRequest.LeaveStatus.APPROVED);
        List<Holiday> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate());
        List<Specialty> specialties = specialtyRepository.findByIsActiveTrue();
        List<ShiftType> shiftTypes = shiftTypeRepository.findAll();

        // Resolve config
        ConfigDomain config = resolveConfig(request);
        AutoGenConfig autoGenConfig = toAutoGenConfig(config);

        return switch (request.getMode()) {
            case 1 -> mode1(period, activeStaff, approvedLeaves, holidays, specialties, shiftTypes,
                    config, autoGenConfig, request.getAlgorithmType());
            case 2 -> mode2(period, activeStaff, approvedLeaves, holidays, specialties, shiftTypes,
                    config, autoGenConfig, request.getAlgorithmType(), request.getTargetShifts(),
                    request.getEnabledGroups(), request.getTargetTotalRequirement(),
                    request.getTargetCoverage());
            case 3 -> mode3(period, activeStaff, approvedLeaves, holidays, specialties, shiftTypes,
                    config, autoGenConfig, request.getTargetShifts(), request.getEnabledGroups(),
                    request.getTargetTotalRequirement(), request.getTargetCoverage());
            default -> throw new IllegalArgumentException("mode không hợp lệ: " + request.getMode());
        };
    }

    // ── Mode 1: Config + Algorithm → Capacity ──

    private ConfigCalculatorResponse mode1(SchedulePeriod period, List<Staff> staff,
                                            List<LeaveRequest> leaves, List<Holiday> holidays,
                                            List<Specialty> specialties, List<ShiftType> shiftTypes,
                                            ConfigDomain config, AutoGenConfig autoGenConfig,
                                            String algorithmType) {
        String algo = algorithmType != null ? algorithmType.toUpperCase() : "GREEDY";
        AlgorithmCapacityAnalyzer analyzer = findAnalyzer(algo);
        if (analyzer == null) {
            // Fallback to GREEDY
            analyzer = findAnalyzer("GREEDY");
        }

        CapacityAnalysis result = analyzer.analyze(period, staff, leaves, holidays, specialties,
                shiftTypes, config, autoGenConfig);

        return toResponse(result, config, algo, 1);
    }

    // ── Mode 2: Target + Algorithm → Config ──

    private ConfigCalculatorResponse mode2(SchedulePeriod period, List<Staff> staff,
                                            List<LeaveRequest> leaves, List<Holiday> holidays,
                                            List<Specialty> specialties, List<ShiftType> shiftTypes,
                                            ConfigDomain currentConfig, AutoGenConfig currentAutoGen,
                                            String algorithmType, Map<String, Integer> targets,
                                            java.util.Set<String> enabledGroups,
                                            Integer targetTotalRequirement,
                                            Double targetCoverage) {

        // ── Aggregate input path: totalRequirement → derive L01-L04 targets ──
        boolean hasAggregate = targetTotalRequirement != null && targetTotalRequirement > 0
                && (targets == null || targets.isEmpty());
        if (hasAggregate) {
            return mode2FromAggregate(period, staff, leaves, holidays, specialties, shiftTypes,
                    currentConfig, currentAutoGen, algorithmType, enabledGroups,
                    targetTotalRequirement, targetCoverage);
        }
        // ── Legacy per-shift targets path (original logic) ──

        String algo = algorithmType != null ? algorithmType.toUpperCase() : "GREEDY";
        AlgorithmCapacityAnalyzer analyzer = findAnalyzer(algo);
        if (analyzer == null) analyzer = findAnalyzer("GREEDY");

        ConfigDomain workingConfig = currentConfig;
        List<CapacityAnalysis.ConfigChange> changes = new ArrayList<>();

        // If enabledGroups is null/empty → all groups tunable (back-compat).
        // Otherwise → only groups in the set are tunable.
        boolean allEnabled = enabledGroups == null || enabledGroups.isEmpty();

        // Iterative parameter tuning
        for (int iteration = 0; iteration < 20; iteration++) {
            CapacityAnalysis analysis = analyzer.analyze(period, staff, leaves, holidays, specialties,
                    shiftTypes, workingConfig, toAutoGenConfig(workingConfig));

            if (meetsTarget(analysis, targets)) {
                ConfigCalculatorResponse resp = toResponse(analysis, workingConfig, algo, 2);
                resp.setRecommendedConfig(workingConfig);
                resp.setConfigChanges(changes.stream()
                        .map(c -> new ConfigCalculatorResponse.ConfigChange(c.field(), c.fromValue(), c.toValue(), c.reason()))
                        .toList());
                resp.setFeasible(true);
                return resp;
            }

            // Try tuning: maxShiftsPerStaff → l04CrossSpecialty → holidayMode → per-shift bounds
            boolean tuned = false;

            // Priority 1: increase maxShiftsPerStaff (group: staffing)
            if ((allEnabled || enabledGroups.contains("staffing")) && workingConfig.maxShiftsPerStaff() < 15) {
                int newVal = Math.min(workingConfig.maxShiftsPerStaff() + 2, 15);
                changes.add(new CapacityAnalysis.ConfigChange("maxShiftsPerStaff",
                        workingConfig.maxShiftsPerStaff(), newVal,
                        "Tăng capacity để đạt target"));
                workingConfig = ConfigDomain.builder().from(workingConfig).maxShiftsPerStaff(newVal).build();
                tuned = true;
            }

            // Priority 2: enable l04CrossSpecialty if L04 target not met (group: l04)
            if (!tuned && (allEnabled || enabledGroups.contains("l04"))
                    && !workingConfig.l04CrossSpecialtyEnabled() && hasL04Target(targets)) {
                changes.add(new CapacityAnalysis.ConfigChange("l04CrossSpecialtyEnabled",
                        false, true,
                        "Bật cross-specialty để tăng L04 capacity"));
                workingConfig = ConfigDomain.builder().from(workingConfig).l04CrossSpecialtyEnabled(true).build();
                tuned = true;
            }

            // Priority 3: switch holidayMode to PARTIAL (group: holiday)
            if (!tuned && (allEnabled || enabledGroups.contains("holiday"))
                    && !"PARTIAL".equals(workingConfig.holidayMode())) {
                changes.add(new CapacityAnalysis.ConfigChange("holidayMode",
                        workingConfig.holidayMode(), "PARTIAL",
                        "Chuyển sang PARTIAL để tận dụng ngày lễ"));
                workingConfig = ConfigDomain.builder().from(workingConfig).holidayMode("PARTIAL").build();
                tuned = true;
            }

            // Priority 4: increase per-shift-type maxPerDay (group: perShift)
            if (!tuned && (allEnabled || enabledGroups.contains("perShift"))) {
                for (String st : new String[]{"L01", "L02", "L03", "L04"}) {
                    int target = targets.getOrDefault(st, 0);
                    int currentMax = switch (st) {
                        case "L01" -> workingConfig.l01MaxPerDay();
                        case "L02" -> workingConfig.l02MaxPerDay();
                        case "L03" -> workingConfig.l03MaxPerDay();
                        case "L04" -> workingConfig.l04MaxPerDay();
                        default -> 0;
                    };
                    if (target > 0 && currentMax < 15) {
                        int newMax = currentMax + 2;
                        ConfigDomain.Builder b = ConfigDomain.builder().from(workingConfig);
                        switch (st) {
                            case "L01" -> b.l01MaxPerDay(newMax);
                            case "L02" -> b.l02MaxPerDay(newMax);
                            case "L03" -> b.l03MaxPerDay(newMax);
                            case "L04" -> b.l04MaxPerDay(newMax);
                        }
                        changes.add(new CapacityAnalysis.ConfigChange(st + "MaxPerDay", currentMax, newMax,
                                "Tăng giới hạn " + st + " để đạt target " + target));
                        workingConfig = b.build();
                        tuned = true;
                        break;
                    }
                }
            }

            if (!tuned) break; // All params maxed out OR all enabled groups already tuned
        }

        // Infeasible — return best effort analysis
        CapacityAnalysis finalAnalysis = analyzer.analyze(period, staff, leaves, holidays, specialties,
                shiftTypes, workingConfig, toAutoGenConfig(workingConfig));
        ConfigCalculatorResponse resp = toResponse(finalAnalysis, workingConfig, algo, 2);
        resp.setFeasible(false);
        resp.setRecommendedConfig(workingConfig);
        resp.setConfigChanges(changes.stream()
                .map(c -> new ConfigCalculatorResponse.ConfigChange(c.field(), c.fromValue(), c.toValue(), c.reason()))
                .toList());
        resp.setMessage("Không thể đạt target với thuật toán " + algo + ". Đã điều chỉnh tối đa các tham số được phép.");
        return resp;
    }

    // ── Mode 3: Target → Config + Algorithm (best combo) ──

    private ConfigCalculatorResponse mode3(SchedulePeriod period, List<Staff> staff,
                                            List<LeaveRequest> leaves, List<Holiday> holidays,
                                            List<Specialty> specialties, List<ShiftType> shiftTypes,
                                            ConfigDomain currentConfig, AutoGenConfig currentAutoGen,
                                            Map<String, Integer> targets,
                                            java.util.Set<String> enabledGroups,
                                            Integer targetTotalRequirement,
                                            Double targetCoverage) {

        // Try each algorithm in priority order
        String[] algoPriority = {"GREEDY", "FAIR_GREEDY", "CSP_MRV_FC", "V10_LOCAL_SEARCH"};
        ConfigCalculatorResponse bestResponse = null;
        int bestConfigChanges = Integer.MAX_VALUE;

        for (String algo : algoPriority) {
            AlgorithmCapacityAnalyzer analyzer = findAnalyzer(algo);
            if (analyzer == null) continue;

            // Run Mode 2 for this algorithm
            ConfigCalculatorResponse mode2Resp = mode2(period, staff, leaves, holidays, specialties,
                    shiftTypes, currentConfig, currentAutoGen, algo, targets, enabledGroups,
                    targetTotalRequirement, targetCoverage);

            if (mode2Resp.isFeasible()) {
                int changeCount = mode2Resp.getConfigChanges() != null ? mode2Resp.getConfigChanges().size() : 0;
                if (changeCount < bestConfigChanges) {
                    bestConfigChanges = changeCount;
                    bestResponse = mode2Resp;
                    bestResponse.setRecommendedAlgorithm(algo);
                    bestResponse.setMode(3);
                }
            }
        }

        if (bestResponse != null) {
            return bestResponse;
        }

        // No algorithm feasible — return best effort
        ConfigCalculatorResponse fallback = mode2(period, staff, leaves, holidays, specialties,
                shiftTypes, currentConfig, currentAutoGen, "GREEDY", targets, enabledGroups,
                targetTotalRequirement, targetCoverage);
        fallback.setMode(3);
        fallback.setMessage("Không thuật toán nào đạt được target. Dưới đây là kết quả tốt nhất với GREEDY.");
        return fallback;
    }

    // ── Helpers ──

    private ConfigDomain resolveConfig(ConfigCalculatorRequest request) {
        ConfigDomain current = configService.load();
        if (request.getConfigOverride() != null && !request.getConfigOverride().isEmpty()) {
            ConfigDomain.Builder merged = ConfigDomain.builder().from(current);
            Map<String, Object> ov = request.getConfigOverride();

            if (ov.containsKey("holidayMode") && ov.get("holidayMode") instanceof String s && !s.isEmpty())
                merged.holidayMode(s);
            if (ov.containsKey("removedShiftTypes") && ov.get("removedShiftTypes") instanceof java.util.List<?> list)
                merged.removedShiftTypes(list.toArray(new String[0]));
            if (ov.containsKey("maxShiftsPerStaff") && ov.get("maxShiftsPerStaff") instanceof Number n)
                merged.maxShiftsPerStaff(n.intValue());
            if (ov.containsKey("maxStaffPerShift") && ov.get("maxStaffPerShift") instanceof Number n)
                merged.maxStaffPerShift(n.intValue());
            if (ov.containsKey("minStaffPerShift") && ov.get("minStaffPerShift") instanceof Number n)
                merged.minStaffPerShift(n.intValue());
            if (ov.containsKey("l04CrossSpecialtyEnabled"))
                merged.l04CrossSpecialtyEnabled(Boolean.TRUE.equals(ov.get("l04CrossSpecialtyEnabled")));
            if (ov.containsKey("l04CrossSpecialtyRatio") && ov.get("l04CrossSpecialtyRatio") instanceof Number n)
                merged.l04CrossSpecialtyRatio(n.doubleValue());
            if (ov.containsKey("l04AllowedSpecialties") && ov.get("l04AllowedSpecialties") instanceof java.util.List<?> specList) {
                merged.l04AllowedSpecialties(specList.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .toArray(String[]::new));
            }
            if (ov.containsKey("l04BalanceStrategy") && ov.get("l04BalanceStrategy") instanceof String s && !s.isEmpty())
                merged.l04BalanceStrategy(s);
            if (ov.containsKey("l01MinPerDay") && ov.get("l01MinPerDay") instanceof Number n) merged.l01MinPerDay(n.intValue());
            if (ov.containsKey("l01MaxPerDay") && ov.get("l01MaxPerDay") instanceof Number n) merged.l01MaxPerDay(n.intValue());
            if (ov.containsKey("l02MinPerDay") && ov.get("l02MinPerDay") instanceof Number n) merged.l02MinPerDay(n.intValue());
            if (ov.containsKey("l02MaxPerDay") && ov.get("l02MaxPerDay") instanceof Number n) merged.l02MaxPerDay(n.intValue());
            if (ov.containsKey("l03MinPerDay") && ov.get("l03MinPerDay") instanceof Number n) merged.l03MinPerDay(n.intValue());
            if (ov.containsKey("l03MaxPerDay") && ov.get("l03MaxPerDay") instanceof Number n) merged.l03MaxPerDay(n.intValue());
            if (ov.containsKey("l04MinPerDay") && ov.get("l04MinPerDay") instanceof Number n) merged.l04MinPerDay(n.intValue());
            if (ov.containsKey("l04MaxPerDay") && ov.get("l04MaxPerDay") instanceof Number n) merged.l04MaxPerDay(n.intValue());
            if (ov.containsKey("l01MaxPerWeek") && ov.get("l01MaxPerWeek") instanceof Number n) merged.l01MaxPerWeek(n.intValue());
            if (ov.containsKey("l02MaxPerWeek") && ov.get("l02MaxPerWeek") instanceof Number n) merged.l02MaxPerWeek(n.intValue());
            if (ov.containsKey("l03MaxPerWeek") && ov.get("l03MaxPerWeek") instanceof Number n) merged.l03MaxPerWeek(n.intValue());
            if (ov.containsKey("l04MaxPerWeek") && ov.get("l04MaxPerWeek") instanceof Number n) merged.l04MaxPerWeek(n.intValue());

            return merged.build();
        }
        return current;
    }

    private AutoGenConfig toAutoGenConfig(ConfigDomain config) {
        return new AutoGenConfig(
                config.enabled(),
                config.l01MinPerDay(), config.l02MinPerDay(), config.l03MinPerDay(), config.l04MinPerDay(),
                config.l01MaxPerDay(), config.l02MaxPerDay(), config.l03MaxPerDay(), config.l04MaxPerDay(),
                config.l01MaxPerWeek(), config.l02MaxPerWeek(), config.l03MaxPerWeek(), config.l04MaxPerWeek(),
                config.holidayMode(),
                java.util.List.of(), // removedShiftTypes — luôn rỗng để không skip L01-L04
                config.l04CrossSpecialtyEnabled(),
                (float) config.l04CrossSpecialtyRatio(),
                config.l04AllowedSpecialties() != null ? List.of(config.l04AllowedSpecialties()) : List.of(),
                config.l04BalanceStrategy()
        );
    }

    private AlgorithmCapacityAnalyzer findAnalyzer(String algorithmType) {
        String type = algorithmType != null ? algorithmType.toUpperCase() : "GREEDY";
        // Normalize aliases
        if ("ROUND_ROBIN".equals(type) || "FAIR_ROUND_ROBIN".equals(type) || "FAIR".equals(type) || "FAIR_GREEDY".equals(type)) {
            type = "GREEDY"; // GreedyAnalyzer handles both
        }
        if ("CSP".equals(type)) type = "CSP_MRV_FC";
        if ("V10".equals(type)) type = "V10_LOCAL_SEARCH";

        for (AlgorithmCapacityAnalyzer a : analyzers) {
            if (a.supportedAlgorithmType().equals(type)) return a;
        }
        return null;
    }

    private boolean meetsTarget(CapacityAnalysis analysis, Map<String, Integer> targets) {
        if (targets == null) return false;
        for (var entry : targets.entrySet()) {
            int target = entry.getValue() != null ? entry.getValue() : 0;
            if (target <= 0) continue;
            int achieved = 0;
            if (analysis.getPerShiftType() != null) {
                for (var st : analysis.getPerShiftType()) {
                    if (st.shiftType().equals(entry.getKey())) {
                        achieved = st.assigned();
                        break;
                    }
                }
            }
            if (achieved < target) return false;
        }
        return true;
    }

    private boolean hasL04Target(Map<String, Integer> targets) {
        return targets != null && targets.getOrDefault("L04", 0) > 0;
    }

    // ── Mode 2 aggregate path: totalRequirement → derive L01-L04 targets → Config ──

    private ConfigCalculatorResponse mode2FromAggregate(SchedulePeriod period, List<Staff> staff,
                                                         List<LeaveRequest> leaves, List<Holiday> holidays,
                                                         List<Specialty> specialties, List<ShiftType> shiftTypes,
                                                         ConfigDomain currentConfig, AutoGenConfig currentAutoGen,
                                                         String algorithmType, java.util.Set<String> enabledGroups,
                                                         Integer targetTotalRequirement, Double targetCoverage) {

        String algo = algorithmType != null ? algorithmType.toUpperCase() : "GREEDY";
        AlgorithmCapacityAnalyzer analyzer = findAnalyzer(algo);
        if (analyzer == null) analyzer = findAnalyzer("GREEDY");

        // Step 1: Run Mode 1 once to get current L01-L04 distribution ratio
        CapacityAnalysis baseline = analyzer.analyze(period, staff, leaves, holidays, specialties,
                shiftTypes, currentConfig, currentAutoGen);

        if (baseline.getPerShiftType() == null || baseline.getPerShiftType().isEmpty()) {
            ConfigCalculatorResponse resp = toResponse(baseline, currentConfig, algo, 2);
            resp.setFeasible(false);
            resp.setMessage("Không có dữ liệu phân bổ L01-L04 từ cấu hình hiện tại để làm baseline.");
            return resp;
        }

        // Step 2: Derive L01-L04 targets proportionally from totalRequirement
        int totalReq = baseline.getTotalRequirement();
        Map<String, Integer> derivedTargets = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> rawTargets = new java.util.LinkedHashMap<>();

        for (var st : baseline.getPerShiftType()) {
            String key = st.shiftType();
            int assigned = st.assigned() > 0 ? st.assigned() : st.requirement();
            rawTargets.put(key, assigned);
        }

        if (totalReq > 0) {
            // Proportional allocation
            for (var entry : rawTargets.entrySet()) {
                double ratio = (double) entry.getValue() / totalReq;
                int derived = (int) Math.round(ratio * targetTotalRequirement);
                // minimum 1 per shift type if it had any requirement in baseline
                if (entry.getValue() > 0 && derived == 0) derived = 1;
                derivedTargets.put(entry.getKey(), Math.max(0, derived));
            }
        } else {
            // Fallback: equal distribution across L01-L04
            int equalShare = targetTotalRequirement / 4;
            for (String st : new String[]{"L01", "L02", "L03", "L04"}) {
                derivedTargets.put(st, equalShare);
            }
        }

        // Step 3: Run the existing iterative Mode 2 tuner with derived targets
        ConfigCalculatorResponse resp = mode2(period, staff, leaves, holidays, specialties, shiftTypes,
                currentConfig, currentAutoGen, algo, derivedTargets, enabledGroups, null, null);

        // Step 4: Attach trace info
        resp.setDerivedTargetShifts(derivedTargets);
        return resp;
    }

    private ConfigCalculatorResponse toResponse(CapacityAnalysis analysis, ConfigDomain config,
                                                  String algorithmType, int mode) {
        ConfigCalculatorResponse resp = new ConfigCalculatorResponse();
        resp.setMode(mode);
        resp.setFeasible(analysis.isFeasible());
        resp.setPeriodId(null); // caller fills
        resp.setTotalStaff(analysis.getTotalStaff());
        resp.setPeriodDays(analysis.getPeriodDays());
        resp.setTotalRequirement(analysis.getTotalRequirement());
        resp.setTotalCapacity(analysis.getTotalCapacity());
        resp.setTotalAssigned(analysis.getTotalAssigned());
        resp.setExpectedCoverage(analysis.getExpectedCoverage());
        resp.setExpectedFairness(analysis.getExpectedFairness());

        if (analysis.getPerShiftType() != null) {
            resp.setPerShiftType(analysis.getPerShiftType().stream().map(st -> {
                var dto = new ConfigCalculatorResponse.ShiftTypeCapacity();
                dto.setShiftType(st.shiftType());
                dto.setRequirement(st.requirement());
                dto.setMaxPossible(st.maxPossible());
                dto.setAssigned(st.assigned());
                dto.setEligibleStaffCount(st.eligibleStaffCount());
                dto.setAvgDomainSize(st.avgDomainSize());
                dto.setMinDomainSize(st.minDomainSize());
                dto.setBottleneckCount(st.bottleneckCount());
                dto.setPerSpecialty(st.perSpecialty());
                return dto;
            }).toList());
        }

        if (analysis.getBottlenecks() != null) {
            resp.setBottlenecks(analysis.getBottlenecks().stream().map(b -> {
                var dto = new ConfigCalculatorResponse.Bottleneck();
                dto.setType(b.type());
                dto.setShiftType(b.shiftType());
                dto.setSpecialty(b.specialty());
                dto.setSeverity(b.severity());
                dto.setMessage(b.message());
                dto.setSuggestion(b.suggestion());
                return dto;
            }).toList());
        }

        if (analysis.getHolidayImpact() != null) {
            var hi = new ConfigCalculatorResponse.HolidayImpact();
            hi.setHolidayDaysCount(analysis.getHolidayImpact().holidayDaysCount());
            hi.setSkippedShifts(analysis.getHolidayImpact().skippedShifts());
            hi.setMode(analysis.getHolidayImpact().mode());
            resp.setHolidayImpact(hi);
        }

        if (analysis.getAlgorithmRun() != null) {
            var ai = new ConfigCalculatorResponse.AlgorithmInfo();
            ai.setType(analysis.getAlgorithmRun().type());
            ai.setExecutionTimeMs(analysis.getAlgorithmRun().executionTimeMs());
            ai.setTerminatedBy(analysis.getAlgorithmRun().terminatedBy());
            ai.setVarsExplored(analysis.getAlgorithmRun().varsExplored());
            ai.setAssignmentsMade(analysis.getAlgorithmRun().assignmentsMade());
            resp.setAlgorithmInfo(ai);
        }

        return resp;
    }
}
