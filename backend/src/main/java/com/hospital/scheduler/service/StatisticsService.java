package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.StaffShiftStatistics;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final ScheduleRepository scheduleRepository;

    /**
     * Get staff shift statistics for a given period.
     *
     * @param periodId the schedule period ID
     * @param shiftTypeId optional shift type filter (L01, L02, L03, L04)
     * @return list of staff shift statistics sorted by total shifts descending
     */
    public List<StaffShiftStatistics> getStaffShiftStatistics(Integer periodId, String shiftTypeId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .collect(Collectors.toList());
        }

        Map<Integer, List<Schedule>> byStaff = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        int totalShiftsAll = schedules.size();

        return byStaff.entrySet().stream()
                .map(entry -> {
                    Staff staff = entry.getValue().get(0).getStaff();
                    List<Schedule> staffSchedules = entry.getValue();

                    int L01 = countShift(staffSchedules, "L01");
                    int L02 = countShift(staffSchedules, "L02");
                    int L03 = countShift(staffSchedules, "L03");
                    int L04 = countShift(staffSchedules, "L04");
                    int total = staffSchedules.size();

                    BigDecimal hours = calculateHours(L01, L02, L03, L04);
                    BigDecimal percentage = totalShiftsAll > 0
                            ? BigDecimal.valueOf(total * 100.0 / totalShiftsAll)
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return StaffShiftStatistics.builder()
                            .staffId(staff.getId())
                            .staffName(staff.getFullName())
                            .staffCode(staff.getStaffCode())
                            .specialtyName(staff.getSpecialty() != null ? staff.getSpecialty().getName() : null)
                            .totalShifts(total)
                            .L01Count(L01)
                            .L02Count(L02)
                            .L03Count(L03)
                            .L04Count(L04)
                            .totalHours(hours)
                            .workloadPercentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparingInt(StaffShiftStatistics::getTotalShifts).reversed())
                .collect(Collectors.toList());
    }

    private int countShift(List<Schedule> schedules, String shiftTypeId) {
        return (int) schedules.stream()
                .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                .count();
    }

    private BigDecimal calculateHours(int L01, int L02, int L03, int L04) {
        double hours = L01 * 24.0 + L02 * 8.0 + L03 * 4.0 + L04 * 4.0;
        return BigDecimal.valueOf(hours).setScale(1, RoundingMode.HALF_UP);
    }
}
