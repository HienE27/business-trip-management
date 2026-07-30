package com.hospital.scheduler.calculator;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.dto.ConfigCalculatorRequest;
import com.hospital.scheduler.dto.ConfigCalculatorResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.config.ConfigService;
import com.hospital.scheduler.scheduling.config.ConfigValidator;
import com.hospital.scheduler.scheduling.config.ConfigValidationException;
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
    private final ConfigValidator configValidator;

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

        if (request.getMode() != 1) {
            throw new IllegalArgumentException("Chỉ hỗ trợ Configuration Calculator Mode 1");
        }

        // Resolve config
        ConfigDomain config = resolveConfig(request);
        ConfigValidator.ValidationResult validation = configValidator.validate(config);
        if (validation.hasErrors()) {
            throw new ConfigValidationException(validation);
        }
        AutoGenConfig autoGenConfig = toAutoGenConfig(config);

        return mode1(period, activeStaff, approvedLeaves, holidays, specialties, shiftTypes,
                config, autoGenConfig, request.getAlgorithmType());
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
                config.removedShiftTypes() != null ? List.of(config.removedShiftTypes()) : List.of(),
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
