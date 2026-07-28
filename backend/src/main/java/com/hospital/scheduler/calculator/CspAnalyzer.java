package com.hospital.scheduler.calculator;

import com.hospital.scheduler.algorithm.*;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes CSP_MRV_FC capacity by building actual ProblemData via CspDataBuilder
 * and analyzing domains pre/post AC-3.
 *
 * Reuses the REAL CspDataBuilder.build() with actual staff, leave, and config.
 */
@Component
@RequiredArgsConstructor
public class CspAnalyzer implements AlgorithmCapacityAnalyzer {

    private final CspDataBuilder cspDataBuilder;

    @Override
    public String supportedAlgorithmType() {
        return "CSP_MRV_FC";
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

        AnalysisCollector collector = new AnalysisCollector("CSP_MRV_FC");
        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
        String holidayMode = autoGenConfig.holidayMode();
        Set<String> removedShiftTypes = autoGenConfig.removedShiftTypes() == null
                ? Set.of() : new HashSet<>(autoGenConfig.removedShiftTypes());

        // 1. Generate requirements in-memory
        List<ShiftRequirementInfo> reqInfos = buildShiftRequirementInfos(
                period, holidayDates, holidayMode, removedShiftTypes,
                specialties, autoGenConfig, activeStaff);

        if (reqInfos.isEmpty()) {
            return emptyResult(period, activeStaff.size(), holidayDates.size(), holidayMode);
        }

        // 2. Build dates list
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cur = period.getStartDate();
        while (!cur.isAfter(period.getEndDate())) {
            dates.add(cur);
            cur = cur.plusDays(1);
        }

        // 3. Run CspDataBuilder.build() — the actual algorithm code
        int maxShiftsOverride = resolveMaxShiftsOverride(config, autoGenConfig);
        List<String> l04Allowed = autoGenConfig.l04AllowedSpecialties() != null && !autoGenConfig.l04AllowedSpecialties().isEmpty()
                ? new ArrayList<>(autoGenConfig.l04AllowedSpecialties())
                : new ArrayList<>(StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES);

        Set<String> activeShiftTypeIds = new HashSet<>();
        for (ShiftType st : shiftTypes) {
            if (!removedShiftTypes.contains(st.getId())) {
                activeShiftTypeIds.add(st.getId());
            }
        }

        ProblemData data;
        try {
            data = cspDataBuilder.build(
                    activeStaff, dates, reqInfos, leaveRequests,
                    l04Allowed, null, activeShiftTypeIds,
                    autoGenConfig.l04CrossSpecialty(), maxShiftsOverride);
        } catch (Exception e) {
            CapacityAnalysis err = new CapacityAnalysis();
            err.setFeasible(false);
            err.setTotalRequirement(reqInfos.stream().mapToInt(ShiftRequirementInfo::requiredCount).sum());
            err.setTotalStaff(activeStaff.size());
            err.setPeriodDays(dates.size());
            err.setBottlenecks(List.of(new CapacityAnalysis.Bottleneck(
                    "BUILD_ERROR", "ALL", null, "HIGH",
                    "Lỗi xây dựng dữ liệu CSP: " + e.getMessage(),
                    "Kiểm tra cấu hình và dữ liệu đầu vào"
            )));
            return err;
        }

        // 4. Analyze domains per shift type
        Map<String, List<Integer>> varIndicesByShift = new HashMap<>();
        Map<String, Integer> typeRequirement = new HashMap<>();
        Map<String, Map<String, Integer>> typeL04Specialty = new HashMap<>();

        for (String st : CspConstants.SHIFT_ORDER) {
            varIndicesByShift.put(st, new ArrayList<>());
            typeRequirement.put(st, 0);
            typeL04Specialty.put(st, new HashMap<>());
        }

        int[] varShift = data.getVarShift();
        int[] varDay = data.getVarDay();
        int[] varSpecialty = data.getVarSpecialty();
        BitSet[] domains = data.getDomains();
        String[] shiftTypeIds = data.getShiftTypeIds();
        int numVars = data.getNumVars();

        for (int v = 0; v < numVars; v++) {
            int shiftIdx = varShift[v];
            // varShift[v] always stores the canonical SHIFT_ORDER index.
            // shiftTypeIds from resolveShiftOrder() may be re-indexed when
            // a shift type is removed, causing misclassification — always
            // use SHIFT_ORDER directly.
            String st = CspConstants.SHIFT_ORDER[shiftIdx];
            varIndicesByShift.get(st).add(v);
            typeRequirement.merge(st, 1, Integer::sum);

            int domainSize = domains[v].cardinality();
            collector.recordVariable(st);
            collector.recordDomainSize(st, domainSize);

            if (domainSize <= 2) {
                if (domainSize == 0) {
                    int day = varDay[v];
                    String dayStr = day < dates.size() ? dates.get(day).toString() : "?";
                    collector.recordBottleneck(st,
                            st + " ngày " + dayStr + ": domain rỗng (0 nhân sự đủ điều kiện)");
                }
            }

            // L04 specialty tracking
            if ("L04".equals(st) && varSpecialty != null && v < varSpecialty.length) {
                int specId = varSpecialty[v];
                if (specId > 0) {
                    Optional<Specialty> spec = specialties.stream().filter(sp -> sp.getId() == specId).findFirst();
                    String specName = spec.map(Specialty::getName).orElse("ID:" + specId);
                    typeL04Specialty.get(st).merge(specName, 1, Integer::sum);
                }
            }
        }

        // 5. Count eligible staff per shift type (max domain per type)
        for (String st : CspConstants.SHIFT_ORDER) {
            int maxEligible = 0;
            for (int v : varIndicesByShift.get(st)) {
                int size = domains[v].cardinality();
                if (size > maxEligible) maxEligible = size;
            }
            collector.recordEligibleStaff(st, maxEligible);
        }

        // 6. Calculate max possible per type
        Map<String, Integer> maxPossible = new HashMap<>();
        int totalStaffMax = 0;
        int[] staffMaxShifts = data.getStaffMaxShifts();
        for (int i = 0; i < data.getNumStaff(); i++) {
            totalStaffMax += staffMaxShifts[i];
        }
        for (String st : CspConstants.SHIFT_ORDER) {
            int vars = varIndicesByShift.get(st).size();
            // Upper bound: cannot exceed total staff capacity or variable count
            maxPossible.put(st, Math.min(vars, totalStaffMax));
        }

        // 7. Build bottleneck details
        List<CapacityAnalysis.Bottleneck> bottlenecks = new ArrayList<>();
        for (String st : CspConstants.SHIFT_ORDER) {
            long bottleneckCount = varIndicesByShift.get(st).stream()
                    .filter(v -> domains[v].cardinality() <= 2).count();
            if (bottleneckCount > 0) {
                boolean hasEmpty = varIndicesByShift.get(st).stream().anyMatch(v -> domains[v].isEmpty());
                String severity = hasEmpty ? "HIGH" : (bottleneckCount > 5 ? "HIGH" : "MEDIUM");
                String msg;
                String suggestion;

                if (hasEmpty) {
                    msg = st + ": có variable với domain rỗng — không thể assign";
                    suggestion = "Thêm nhân sự hoặc điều chỉnh ràng buộc chuyên khoa";
                } else {
                    msg = st + ": " + bottleneckCount + " variable có domain ≤ 2";
                    suggestion = "Tăng maxShiftsPerStaff hoặc bật l04CrossSpecialty";
                }

                if ("L04".equals(st) && !autoGenConfig.l04CrossSpecialty()) {
                    msg += " (l04CrossSpecialty=OFF)";
                    suggestion = "Bật l04CrossSpecialty để mở rộng domain cho L04";
                }

                bottlenecks.add(new CapacityAnalysis.Bottleneck(
                        "CONSTRAINT_BLOCK", st, null, severity, msg, suggestion));
            }
        }

        // 8. Count assigned = vars with non-empty domain
        long totalAssigned = 0;
        for (int v = 0; v < numVars; v++) {
            if (!domains[v].isEmpty()) totalAssigned++;
        }

        CapacityAnalysis analysis = new CapacityAnalysis();
        analysis.setFeasible(bottlenecks.stream().noneMatch(b -> "HIGH".equals(b.severity())));
        analysis.setPeriodDays(dates.size());
        analysis.setTotalStaff(activeStaff.size());
        analysis.setTotalRequirement(numVars);
        analysis.setTotalCapacity(maxPossible.values().stream().mapToInt(Integer::intValue).sum());
        analysis.setTotalAssigned((int) totalAssigned);
        analysis.setBottlenecks(bottlenecks);

        List<CapacityAnalysis.ShiftTypeCapacity> perType = new ArrayList<>();
        for (String st : CspConstants.SHIFT_ORDER) {
            long assignedForType = varIndicesByShift.get(st).stream()
                    .filter(v -> !domains[v].isEmpty()).count();
            perType.add(new CapacityAnalysis.ShiftTypeCapacity(
                    st,
                    typeRequirement.getOrDefault(st, 0),
                    maxPossible.getOrDefault(st, 0),
                    (int) assignedForType,
                    collector.getEligibleStaff(st),
                    collector.getAvgDomainSize(st),
                    collector.getMinDomainSize(st),
                    (int) varIndicesByShift.get(st).stream().filter(v -> domains[v].cardinality() <= 2).count(),
                    typeL04Specialty.getOrDefault(st, Map.of())
            ));
        }
        analysis.setPerShiftType(perType);

        analysis.setHolidayImpact(new CapacityAnalysis.HolidayImpact(
                holidayDates.size(), Map.of(), holidayMode));

        long elapsed = collector.getElapsedMs();
        analysis.setAlgorithmRun(new CapacityAnalysis.AlgorithmRun(
                "CSP_MRV_FC", elapsed,
                analysis.isFeasible() ? "COMPLETE" : "BOTTLENECK",
                numVars, (int) totalAssigned));

        if (analysis.getTotalRequirement() > 0) {
            analysis.setExpectedCoverage((double) totalAssigned / analysis.getTotalRequirement());
        }
        analysis.setExpectedFairness(0.6);

        return analysis;
    }

    private int resolveMaxShiftsOverride(ConfigDomain config, AutoGenConfig autoGenConfig) {
        if (config != null && config.maxShiftsPerStaff() > 0) return config.maxShiftsPerStaff();
        return 0;
    }

    private List<ShiftRequirementInfo> buildShiftRequirementInfos(
            SchedulePeriod period, Set<LocalDate> holidays, String holidayMode,
            Set<String> removedShiftTypes, List<Specialty> specialties,
            AutoGenConfig config, List<Staff> activeStaff) {

        List<ShiftRequirementInfo> result = new ArrayList<>();
        int generalPool = Math.max(1, activeStaff.size());
        LocalDate current = period.getStartDate();

        while (!current.isAfter(period.getEndDate())) {
            LocalDate date = current;
            boolean isHoliday = holidays.contains(date);
            boolean shouldGenerate = !isHoliday || "PARTIAL".equalsIgnoreCase(holidayMode);

            if (shouldGenerate && !removedShiftTypes.contains("L01")) {
                int count = resolveTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPool);
                if (count > 0) result.add(new ShiftRequirementInfo("L01", date, count, null));
            }
            if (shouldGenerate && !removedShiftTypes.contains("L02")) {
                int count = resolveTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPool);
                if (count > 0) result.add(new ShiftRequirementInfo("L02", date, count, null));
            }
            if (shouldGenerate && !removedShiftTypes.contains("L03")) {
                int count = resolveTarget(config.l03MinPerDay(), config.l03MaxPerDay(), generalPool);
                if (count > 0) result.add(new ShiftRequirementInfo("L03", date, count, null));
            }
            if (shouldGenerate && !removedShiftTypes.contains("L04")) {
                for (Specialty spec : specialties) {
                    int specPool = config.l04CrossSpecialty()
                            ? generalPool : countStaffBySpecialty(activeStaff, spec.getId());
                    int count = resolveTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specPool);
                    if (count > 0) {
                        result.add(new ShiftRequirementInfo("L04", date, count, spec.getId()));
                    }
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

    private CapacityAnalysis emptyResult(SchedulePeriod period, int totalStaff, int holidayDays, String holidayMode) {
        CapacityAnalysis empty = new CapacityAnalysis();
        empty.setFeasible(true);
        empty.setTotalRequirement(0);
        empty.setTotalCapacity(0);
        empty.setTotalAssigned(0);
        empty.setTotalStaff(totalStaff);
        empty.setPeriodDays((int) period.getStartDate().until(period.getEndDate()).getDays() + 1);
        empty.setPerShiftType(List.of());
        empty.setBottlenecks(List.of());
        empty.setHolidayImpact(new CapacityAnalysis.HolidayImpact(holidayDays, Map.of(), holidayMode));
        empty.setAlgorithmRun(new CapacityAnalysis.AlgorithmRun("CSP_MRV_FC", 0, "NO_REQUIREMENTS", 0, 0));
        return empty;
    }
}
