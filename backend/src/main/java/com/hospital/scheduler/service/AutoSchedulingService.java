package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoSchedulingService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final AlgorithmMetricsRepository metricsRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;

    public AutoScheduleResponse previewSchedule(AutoScheduleRequestDTO request) {
        return runScheduling(request, false);
    }

    public AutoScheduleResponse autoSchedule(AutoScheduleRequestDTO request) {
        return runScheduling(request, true);
    }

    private AutoScheduleResponse runScheduling(AutoScheduleRequestDTO request, boolean save) {
        long startTime = System.currentTimeMillis();

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xếp lịch tự động khi kỳ lịch ở trạng thái DRAFT");
        }

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(period.getId());

        if (activeStaff.isEmpty()) {
            throw new BadRequestException("Không có nhân sự nào đang hoạt động");
        }

        String algorithmType = request.getAlgorithmType() != null ? request.getAlgorithmType().toUpperCase() : "GREEDY";

        List<Schedule> createdSchedules;
        if ("ROUND_ROBIN".equals(algorithmType)) {
            createdSchedules = runRoundRobin(period, requirements, activeStaff, save);
        } else if ("BACKTRACKING".equals(algorithmType)) {
            createdSchedules = runBacktracking(period, requirements, activeStaff, save, request.getMaxIterations() != null ? request.getMaxIterations() : 1000);
        } else {
            createdSchedules = runGreedy(period, requirements, activeStaff, save);
        }
        List<String> warnings = buildWarnings(requirements, createdSchedules);

        long executionTime = System.currentTimeMillis() - startTime;
        int totalRequired = requirements.size();
        BigDecimal coverageRate = totalRequired > 0
                ? BigDecimal.valueOf((double) createdSchedules.size() / totalRequired * 100)
                : BigDecimal.ZERO;
        BigDecimal balanceScore = calculateBalanceScore(createdSchedules, activeStaff.size());

        if (save) {
            saveMetrics(period, algorithmType, (int) executionTime, coverageRate, balanceScore, warnings.size());
        }

        List<AutoScheduleResponse.ScheduleSummary> scheduleSummaries = createdSchedules.stream()
                .map(s -> AutoScheduleResponse.ScheduleSummary.builder()
                        .scheduleId(s.getId())
                        .staffId(s.getStaff().getId())
                        .staffName(s.getStaff().getFullName())
                        .workDate(s.getWorkDate().toString())
                        .shiftTypeId(s.getShiftType().getId())
                        .shiftTypeName(s.getShiftType().getName())
                        .build())
                .collect(Collectors.toList());

        String actionType = save ? "Xếp lịch tự động thành công" : "Xem trước lịch";

        return AutoScheduleResponse.builder()
                .success(true)
                .message(warnings.isEmpty() ? actionType : actionType + " với " + warnings.size() + " cảnh báo")
                .periodId(period.getId())
                .algorithmType(algorithmType)
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate.setScale(2, RoundingMode.HALF_UP))
                .balanceScore(balanceScore.setScale(2, RoundingMode.HALF_UP))
                .conflictCount(warnings.size())
                .totalSchedulesCreated(createdSchedules.size())
                .schedules(scheduleSummaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    // ==================== GREEDY ALGORITHM ====================
    private List<Schedule> runGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
                                     List<Staff> activeStaff, boolean save) {
        List<Schedule> createdSchedules = new ArrayList<>();
        LocalDate currentDate = period.getStartDate();

        while (!currentDate.isAfter(period.getEndDate())) {
            for (ShiftRequirement req : requirements) {
                if (!req.getWorkDate().equals(currentDate)) continue;

                List<Staff> availableStaff = conflictDetectionService.findReplacements(
                        period.getId(), currentDate, req.getShiftType().getId(), null, req.getRequiredStaffCount());

                availableStaff = filterBySpecialty(availableStaff, req.getSpecialty() != null ? req.getSpecialty().getId() : null);

                int staffToAssign = Math.min(req.getRequiredStaffCount(), availableStaff.size());

                for (int i = 0; i < staffToAssign; i++) {
                    Staff staff = selectStaffByWorkload(availableStaff, period.getId(), req.getShiftType().getId());
                    if (staff == null) break;

                    Schedule schedule = buildSchedule(period, staff, req.getShiftType(), currentDate, req);
                    if (schedule == null) break;

                    if (save) {
                        Schedule saved = scheduleRepository.save(schedule);
                        createdSchedules.add(saved);
                        if ("L01".equals(req.getShiftType().getId())) {
                            createCompensationDayForAuto(saved);
                        }
                        auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
                    } else {
                        schedule.setId(null);
                        createdSchedules.add(schedule);
                    }
                    availableStaff.remove(staff);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== ROUND ROBIN ALGORITHM ====================
    private List<Schedule> runRoundRobin(SchedulePeriod period, List<ShiftRequirement> requirements,
                                          List<Staff> activeStaff, boolean save) {
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<Integer, Integer> staffRotationIndex = new HashMap<>();
        for (Staff staff : activeStaff) {
            staffRotationIndex.put(staff.getId(), 0);
        }

        LocalDate currentDate = period.getStartDate();

        while (!currentDate.isAfter(period.getEndDate())) {
            for (ShiftRequirement req : requirements) {
                if (!req.getWorkDate().equals(currentDate)) continue;

                List<Staff> availableStaff = conflictDetectionService.findReplacements(
                        period.getId(), currentDate, req.getShiftType().getId(), null, req.getRequiredStaffCount());

                availableStaff = filterBySpecialty(availableStaff, req.getSpecialty() != null ? req.getSpecialty().getId() : null);

                if (availableStaff.isEmpty()) continue;

                availableStaff.sort(Comparator.comparingInt(s -> staffRotationIndex.getOrDefault(s.getId(), 0)));

                int staffToAssign = Math.min(req.getRequiredStaffCount(), availableStaff.size());

                for (int i = 0; i < staffToAssign; i++) {
                    Staff staff = availableStaff.get(i);
                    Schedule schedule = buildSchedule(period, staff, req.getShiftType(), currentDate, req);
                    if (schedule == null) continue;

                    if (save) {
                        Schedule saved = scheduleRepository.save(schedule);
                        createdSchedules.add(saved);
                        if ("L01".equals(req.getShiftType().getId())) {
                            createCompensationDayForAuto(saved);
                        }
                        auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
                    } else {
                        schedule.setId(null);
                        createdSchedules.add(schedule);
                    }
                    staffRotationIndex.put(staff.getId(), staffRotationIndex.getOrDefault(staff.getId(), 0) + 1);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== BACKTRACKING ALGORITHM ====================
    private List<Schedule> runBacktracking(SchedulePeriod period, List<ShiftRequirement> requirements,
                                            List<Staff> activeStaff, boolean save, int maxIterations) {
        List<Schedule> bestSolution = new ArrayList<>();
        List<Schedule> currentSolution = new ArrayList<>();
        Set<Integer> assignedStaffPerDay = new HashSet<>();
        int[] iterationCount = {0};

        List<ShiftRequirement> sortedRequirements = requirements.stream()
                .sorted(Comparator.comparing((ShiftRequirement r) -> r.getSpecialty() != null ? 0 : 1)
                        .thenComparing(r -> r.getRequiredStaffCount())
                        .reversed())
                .collect(Collectors.toList());

        backtrack(period, sortedRequirements, activeStaff, 0, currentSolution, bestSolution,
                  new HashMap<>(), assignedStaffPerDay, iterationCount, maxIterations);

        if (save) {
            for (Schedule schedule : bestSolution) {
                Schedule saved = scheduleRepository.save(schedule);
                schedule.setId(saved.getId());
                if ("L01".equals(schedule.getShiftType().getId())) {
                    createCompensationDayForAuto(saved);
                }
                auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
            }
        } else {
            for (Schedule schedule : bestSolution) {
                schedule.setId(null);
            }
        }

        return save ? bestSolution : bestSolution;
    }

    private void backtrack(SchedulePeriod period, List<ShiftRequirement> requirements,
                           List<Staff> activeStaff, int index,
                           List<Schedule> currentSolution, List<Schedule> bestSolution,
                           Map<Integer, Integer> staffWorkload,
                           Set<Integer> assignedToday, int[] iterationCount, int maxIterations) {
        if (iterationCount[0] >= maxIterations) return;

        if (currentSolution.size() > bestSolution.size()) {
            bestSolution.clear();
            bestSolution.addAll(currentSolution);
        }

        if (index >= requirements.size()) return;

        iterationCount[0]++;

        ShiftRequirement req = requirements.get(index);
        LocalDate workDate = req.getWorkDate();

        if (!assignedToday.isEmpty() && !assignedToday.contains(workDate.hashCode())) {
            assignedToday.clear();
        }
        assignedToday.add(workDate.hashCode());

        List<Staff> candidates = conflictDetectionService.findReplacements(
                period.getId(), workDate, req.getShiftType().getId(), null, req.getRequiredStaffCount());

        candidates = filterBySpecialty(candidates, req.getSpecialty() != null ? req.getSpecialty().getId() : null);

        candidates.sort(Comparator.comparingInt(s -> staffWorkload.getOrDefault(s.getId(), 0)));

        int staffToAssign = Math.min(req.getRequiredStaffCount(), candidates.size());

        for (int i = 0; i < staffToAssign; i++) {
            Staff staff = candidates.get(i);

            Schedule schedule = buildSchedule(period, staff, req.getShiftType(), workDate, req);
            if (schedule == null) continue;

            currentSolution.add(schedule);
            staffWorkload.merge(staff.getId(), 1, Integer::sum);

            backtrack(period, requirements, activeStaff, index + 1,
                    currentSolution, bestSolution, staffWorkload, assignedToday,
                    iterationCount, maxIterations);

            currentSolution.remove(currentSolution.size() - 1);
            staffWorkload.merge(staff.getId(), -1, (oldVal, ignore) -> oldVal <= 0 ? 0 : oldVal);
        }
    }

    // ==================== M07-F06: Báo cáo ngày chưa phân công ====================
    public Map<String, Object> getUnassignedDaysReport(Integer periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<String, List<ShiftRequirement>> requirementsByDateAndShift = new LinkedHashMap<>();
        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            requirementsByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        Map<String, List<Schedule>> schedulesByDateAndShift = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            String key = s.getWorkDate() + "_" + s.getShiftType().getId();
            schedulesByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> unassignedDays = new ArrayList<>();

        for (Map.Entry<String, List<ShiftRequirement>> entry : requirementsByDateAndShift.entrySet()) {
            String key = entry.getKey();
            List<ShiftRequirement> reqs = entry.getValue();
            List<Schedule> assigned = schedulesByDateAndShift.getOrDefault(key, Collections.emptyList());

            for (ShiftRequirement req : reqs) {
                int required = req.getRequiredStaffCount();
                int assignedCount = (int) assigned.stream()
                        .filter(s -> s.getShiftType().getId().equals(req.getShiftType().getId()))
                        .count();

                if (assignedCount < required) {
                    Map<String, Object> dayInfo = new LinkedHashMap<>();
                    dayInfo.put("workDate", req.getWorkDate());
                    dayInfo.put("dayOfWeek", getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek().getValue()));
                    dayInfo.put("shiftTypeId", req.getShiftType().getId());
                    dayInfo.put("shiftTypeName", req.getShiftType().getName());
                    dayInfo.put("specialty", req.getSpecialty() != null ? req.getSpecialty().getName() : null);
                    dayInfo.put("requiredStaffCount", required);
                    dayInfo.put("assignedStaffCount", assignedCount);
                    dayInfo.put("missingCount", required - assignedCount);
                    unassignedDays.add(dayInfo);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalUnassignedDays", unassignedDays.size());
        result.put("unassignedDays", unassignedDays);

        return result;
    }

    // ==================== M07-F08: Đề xuất người thay thế ====================
    public Map<String, Object> suggestReplacements(Integer scheduleId) {
        Schedule original = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + scheduleId));

        List<Staff> allStaff = staffRepository.findByIsActiveTrue();

        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Staff candidate : allStaff) {
            if (candidate.getId().equals(original.getStaff().getId())) continue;

            if (original.getStaff().getSpecialty() != null) {
                if (candidate.getSpecialty() == null ||
                        !candidate.getSpecialty().getId().equals(original.getStaff().getSpecialty().getId())) {
                    continue;
                }
            }

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    candidate.getId(), original.getWorkDate(), original.getShiftType().getId(), scheduleId);

            if (conflicts.isEmpty()) {
                long currentWorkload = scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId());

                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", currentWorkload);
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", true);
                suggestions.add(suggestion);
            } else {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId()));
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", false);
                suggestions.add(suggestion);
            }
        }

        suggestions.sort(Comparator.comparing(m -> !(Boolean) m.get("isAvailable")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalScheduleId", scheduleId);
        result.put("originalStaffId", original.getStaff().getId());
        result.put("originalStaffName", original.getStaff().getFullName());
        result.put("workDate", original.getWorkDate());
        result.put("shiftTypeId", original.getShiftType().getId());
        result.put("shiftTypeName", original.getShiftType().getName());
        result.put("totalCandidates", suggestions.size());
        result.put("availableCount", (int) suggestions.stream().filter(m -> (Boolean) m.get("isAvailable")).count());
        result.put("suggestions", suggestions);

        return result;
    }

    // ==================== M07-F09: Data biểu đồ cân bằng tải ====================
    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        List<Map<String, Object>> staffWorkloadData = new ArrayList<>();

        for (Staff staff : activeStaff) {
            List<Schedule> staffSchedules = schedules.stream()
                    .filter(s -> s.getStaff().getId().equals(staff.getId()))
                    .collect(Collectors.toList());

            long L01Count = staffSchedules.stream().filter(s -> "L01".equals(s.getShiftType().getId())).count();
            long L02Count = staffSchedules.stream().filter(s -> "L02".equals(s.getShiftType().getId())).count();
            long L03Count = staffSchedules.stream().filter(s -> "L03".equals(s.getShiftType().getId())).count();
            long L04Count = staffSchedules.stream().filter(s -> "L04".equals(s.getShiftType().getId())).count();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("staffId", staff.getId());
            data.put("staffName", staff.getFullName());
            data.put("specialty", staff.getSpecialty() != null ? staff.getSpecialty().getName() : null);
            data.put("totalShifts", staffSchedules.size());
            data.put("L01", L01Count);
            data.put("L02", L02Count);
            data.put("L03", L03Count);
            data.put("L04", L04Count);
            data.put("workloadPercentage", schedules.isEmpty() ? 0.0 :
                    Math.round((double) staffSchedules.size() / schedules.size() * 10000.0) / 100.0);

            staffWorkloadData.add(data);
        }

        staffWorkloadData.sort((a, b) -> {
            int t1 = ((Number) a.get("totalShifts")).intValue();
            int t2 = ((Number) b.get("totalShifts")).intValue();
            return Integer.compare(t2, t1);
        });

        double avgWorkload = schedules.isEmpty() ? 0.0 :
                Math.round((double) schedules.size() / activeStaff.size() * 100.0) / 100.0;

        long minWorkload = staffWorkloadData.stream()
                .mapToLong(m -> ((Number) m.get("totalShifts")).longValue())
                .min().orElse(0);
        long maxWorkload = staffWorkloadData.stream()
                .mapToLong(m -> ((Number) m.get("totalShifts")).longValue())
                .max().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalSchedules", schedules.size());
        result.put("totalStaff", activeStaff.size());
        result.put("averageWorkload", avgWorkload);
        result.put("minWorkload", minWorkload);
        result.put("maxWorkload", maxWorkload);
        result.put("staffWorkloadData", staffWorkloadData);

        return result;
    }

    // ==================== HELPER METHODS ====================
    private List<String> buildWarnings(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        List<String> warnings = new ArrayList<>();

        Map<String, Long> assignedCount = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getWorkDate() + "_" + s.getShiftType().getId(),
                        Collectors.counting()));

        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            long assigned = assignedCount.getOrDefault(key, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                warnings.add(String.format("Ngày %s (%s), ca %s: thiếu %d nhân sự (có %d)",
                        req.getWorkDate(), getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek().getValue()),
                        req.getShiftType().getName(),
                        req.getRequiredStaffCount() - assigned, assigned));
            }
        }

        return warnings;
    }

    private List<Staff> filterBySpecialty(List<Staff> staffList, Integer specialtyId) {
        if (specialtyId == null) return staffList;
        return staffList.stream()
                .filter(s -> s.getSpecialty() != null && s.getSpecialty().getId().equals(specialtyId))
                .collect(Collectors.toList());
    }

    private Staff selectStaffByWorkload(List<Staff> availableStaff, Integer periodId, String shiftTypeId) {
        Staff selected = null;
        long minCount = Long.MAX_VALUE;

        for (Staff staff : availableStaff) {
            long count = scheduleRepository.countByStaffIdAndPeriodId(staff.getId(), periodId);
            if (count < minCount) {
                minCount = count;
                selected = staff;
            }
        }

        return selected;
    }

    private Schedule buildSchedule(SchedulePeriod period, Staff staff, ShiftType shiftType,
                                   LocalDate workDate, ShiftRequirement requirement) {
        Optional<Schedule> existing = scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                period.getId(), staff.getId(), shiftType.getId(), workDate);
        if (existing.isPresent()) return null;

        return Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(shiftType)
                .workDate(workDate)
                .requirement(requirement)
                .hasConflict(false)
                .build();
    }

    private void createCompensationDayForAuto(Schedule schedule) {
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = calculateCompensationDate(shiftDate);

        if (compensationDayRepository.findByStaffIdAndCompensationDate(schedule.getStaff().getId(), compensationDate).isPresent()) {
            return;
        }

        CompensationDay compDay = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(schedule.getPeriod())
                .shiftDate(shiftDate)
                .compensationDate(compensationDate)
                .note("Ngày nghỉ bù tự động từ ca L01")
                .build();

        compensationDayRepository.save(compDay);
    }

    private LocalDate calculateCompensationDate(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> shiftDate.plusDays(1);
            case TUESDAY -> shiftDate.plusDays(1);
            case WEDNESDAY -> shiftDate.plusDays(1);
            case THURSDAY -> shiftDate.plusDays(1);
            case FRIDAY -> shiftDate.plusDays(4);
            case SATURDAY -> shiftDate.plusDays(3);
            case SUNDAY -> shiftDate.plusDays(1);
        };
    }

    private BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        if (staffScheduleCount.size() <= 1) return BigDecimal.valueOf(100);

        double avg = (double) schedules.size() / totalStaff;
        double variance = staffScheduleCount.values().stream()
                .mapToDouble(Long::doubleValue)
                .map(count -> (count - avg) * (count - avg))
                .average()
                .orElse(0);

        double stdDev = Math.sqrt(variance);
        double cv = avg > 0 ? (stdDev / avg) * 100 : 0;

        return BigDecimal.valueOf(Math.max(0, 100 - cv)).setScale(2, RoundingMode.HALF_UP);
    }

    private void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                             BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount) {
        AlgorithmMetrics metrics = AlgorithmMetrics.builder()
                .period(period)
                .algorithmType(algorithmType)
                .executionTimeMs(executionTime)
                .coverageRate(coverageRate)
                .balanceScore(balanceScore)
                .conflictCount(conflictCount)
                .build();

        metricsRepository.save(metrics);
    }

    private String getDayOfWeekVietnamese(int day) {
        return switch (day) {
            case 1 -> "Thứ 2";
            case 2 -> "Thứ 3";
            case 3 -> "Thứ 4";
            case 4 -> "Thứ 5";
            case 5 -> "Thứ 6";
            case 6 -> "Thứ 7";
            case 7 -> "Chủ Nhật";
            default -> "";
        };
    }
}
