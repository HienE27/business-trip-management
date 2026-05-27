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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoSchedulingService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final AlgorithmMetricsRepository metricsRepository;

    public AutoScheduleResponse autoSchedule(AutoScheduleRequestDTO request) {
        long startTime = System.currentTimeMillis();

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xếp lịch tự động khi kỳ lịch ở trạng thái DRAFT");
        }

        List<ShiftType> shiftTypes = shiftTypeRepository.findByIsActiveTrue();
        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(period.getId());

        if (activeStaff.isEmpty()) {
            throw new BadRequestException("Không có nhân sự nào đang hoạt động");
        }

        List<Schedule> createdSchedules = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        LocalDate currentDate = period.getStartDate();
        while (!currentDate.isAfter(period.getEndDate())) {
            for (ShiftRequirement req : requirements) {
                if (!req.getWorkDate().equals(currentDate)) continue;
                if (!req.getShiftType().getId().equals(shiftTypes.get(0).getId())) continue;

                List<Staff> availableStaff = getAvailableStaff(activeStaff, currentDate, req.getShiftType().getId(), period.getId());
                availableStaff = filterBySpecialty(availableStaff, req.getSpecialty().getId());

                int staffToAssign = Math.min(req.getRequiredStaffCount(), availableStaff.size());

                for (int i = 0; i < staffToAssign; i++) {
                    Staff staff = selectStaffByWorkload(availableStaff, period.getId(), req.getShiftType().getId());
                    if (staff == null) break;

                    Schedule schedule = createSchedule(period, staff, req.getShiftType(), currentDate, req);
                    if (schedule != null) {
                        createdSchedules.add(schedule);
                    }
                    availableStaff.remove(staff);
                }

                if (staffToAssign < req.getRequiredStaffCount()) {
                    conflicts.add(String.format("Ngày %s, ca %s: thiếu %d nhân sự (có %d)",
                            currentDate, req.getShiftType().getName(),
                            req.getRequiredStaffCount() - staffToAssign, staffToAssign));
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        int totalRequired = requirements.size();
        BigDecimal coverageRate = totalRequired > 0
                ? BigDecimal.valueOf((double) createdSchedules.size() / totalRequired * 100)
                : BigDecimal.ZERO;
        BigDecimal balanceScore = calculateBalanceScore(createdSchedules, activeStaff.size());

        saveMetrics(period, request.getAlgorithmType(), (int) executionTime, coverageRate, balanceScore, conflicts.size());

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

        return AutoScheduleResponse.builder()
                .success(true)
                .message(conflicts.isEmpty() ? "Xếp lịch tự động thành công" : "Xếp lịch hoàn thành với " + conflicts.size() + " cảnh báo")
                .periodId(period.getId())
                .algorithmType(request.getAlgorithmType())
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate.setScale(2, RoundingMode.HALF_UP))
                .balanceScore(balanceScore.setScale(2, RoundingMode.HALF_UP))
                .conflictCount(conflicts.size())
                .totalSchedulesCreated(createdSchedules.size())
                .schedules(scheduleSummaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private List<Staff> getAvailableStaff(List<Staff> allStaff, LocalDate date, String shiftTypeId, Integer periodId) {
        List<Staff> available = new ArrayList<>();

        for (Staff staff : allStaff) {
            if (isStaffAvailable(staff, date, shiftTypeId, periodId)) {
                available.add(staff);
            }
        }

        return available;
    }

    private boolean isStaffAvailable(Staff staff, LocalDate date, String shiftTypeId, Integer periodId) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(staff.getId(), date, date);
        boolean hasApprovedLeave = leaves.stream().anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) return false;

        var compensationDays = compensationDayRepository.findByStaffIdAndCompensationDate(staff.getId(), date);
        if (compensationDays.isPresent()) return false;

        List<Schedule> existingSchedules = scheduleRepository.findByStaffIdAndWorkDate(staff.getId(), date);
        for (Schedule s : existingSchedules) {
            if ("L01".equals(shiftTypeId) && "L02".equals(s.getShiftType().getId())) return false;
            if ("L02".equals(shiftTypeId) && "L01".equals(s.getShiftType().getId())) return false;
            if ("L03".equals(shiftTypeId) && "L04".equals(s.getShiftType().getId())) return false;
            if ("L04".equals(shiftTypeId) && "L03".equals(s.getShiftType().getId())) return false;
        }

        return true;
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

    private Schedule createSchedule(SchedulePeriod period, Staff staff, ShiftType shiftType,
                                   LocalDate workDate, ShiftRequirement requirement) {
        Optional<Schedule> existing = scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                period.getId(), staff.getId(), shiftType.getId(), workDate);
        if (existing.isPresent()) return null;

        Schedule schedule = Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(shiftType)
                .workDate(workDate)
                .requirement(requirement)
                .hasConflict(false)
                .build();

        return scheduleRepository.save(schedule);
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
}
