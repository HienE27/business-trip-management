package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds workload chart data for the scheduling dashboard.
 * Used by M07-F09.
 */
@Service
public class WorkloadChartBuilder {

    private final SchedulePeriodRepository periodRepository;
    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;

    public WorkloadChartBuilder(SchedulePeriodRepository periodRepository,
                              ScheduleRepository scheduleRepository,
                              StaffRepository staffRepository) {
        this.periodRepository = periodRepository;
        this.scheduleRepository = scheduleRepository;
        this.staffRepository = staffRepository;
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        return getWorkloadChartData(periodId, null);
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId, String shiftTypeId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> staffWorkloadData = buildStaffWorkloadData(activeStaff, schedules, shiftTypeId);

        // Sort by total shifts descending
        staffWorkloadData.sort((a, b) -> {
            int t1 = ((Number) a.get("totalShifts")).intValue();
            int t2 = ((Number) b.get("totalShifts")).intValue();
            return Integer.compare(t2, t1);
        });

        // Calculate averages
        double avgWorkload = calculateAverageWorkload(activeStaff, staffWorkloadData);
        long maxWorkload = calculateMaxWorkload(staffWorkloadData);
        long minWorkload = calculateMinWorkload(staffWorkloadData);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalSchedules", schedules.size());
        result.put("totalStaff", shiftTypeId != null && !shiftTypeId.isBlank()
                ? staffWorkloadData.size() : activeStaff.size());
        result.put("shiftTypeId", shiftTypeId);
        result.put("averageWorkload", avgWorkload);
        result.put("minWorkload", minWorkload);
        result.put("maxWorkload", maxWorkload);
        result.put("staffWorkloadData", staffWorkloadData);

        return result;
    }

    private List<Map<String, Object>> buildStaffWorkloadData(List<Staff> activeStaff,
                                                             List<Schedule> schedules,
                                                             String shiftTypeId) {
        List<Map<String, Object>> data = new ArrayList<>();

        for (Staff staff : activeStaff) {
            List<Schedule> staffSchedules = schedules.stream()
                    .filter(s -> s.getStaff().getId().equals(staff.getId()))
                    .collect(Collectors.toList());

            long L01Count = staffSchedules.stream()
                    .filter(s -> "L01".equals(s.getShiftType().getId())).count();
            long L02Count = staffSchedules.stream()
                    .filter(s -> "L02".equals(s.getShiftType().getId())).count();
            long L03Count = staffSchedules.stream()
                    .filter(s -> "L03".equals(s.getShiftType().getId())).count();
            long L04Count = staffSchedules.stream()
                    .filter(s -> "L04".equals(s.getShiftType().getId())).count();

            double workloadPct = calculateWorkloadPercentage(staff, staffSchedules, schedules);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("staffId", staff.getId());
            item.put("staffName", staff.getFullName());
            item.put("specialty", staff.getSpecialty() != null ? staff.getSpecialty().getName() : null);
            item.put("totalShifts", staffSchedules.size());
            item.put("L01", L01Count);
            item.put("L02", L02Count);
            item.put("L03", L03Count);
            item.put("L04", L04Count);
            item.put("workloadPercentage", workloadPct);

            data.add(item);
        }

        // Filter to staff with at least one shift when filtering by type
        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            data = data.stream()
                    .filter(m -> ((Number) m.get("totalShifts")).longValue() > 0)
                    .collect(Collectors.toList());
        }

        return data;
    }

    private double calculateWorkloadPercentage(Staff staff, List<Schedule> staffSchedules, List<Schedule> allSchedules) {
        Integer maxShifts = staff.getMaxShiftsPerMonth();
        if (maxShifts != null && maxShifts > 0) {
            return Math.round((double) staffSchedules.size() / maxShifts * 10000.0) / 100.0;
        } else if (!allSchedules.isEmpty()) {
            return Math.round((double) staffSchedules.size() / allSchedules.size() * 10000.0) / 100.0;
        }
        return 0.0;
    }

    private double calculateAverageWorkload(List<Staff> activeStaff, List<Map<String, Object>> staffWorkloadData) {
        if (activeStaff.isEmpty()) return 0.0;
        // BUGFIX (BUG#1): use totalShifts (shift count) instead of workloadPercentage
        double totalShifts = staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("totalShifts")).doubleValue())
                .sum();
        return Math.round(totalShifts / activeStaff.size() * 100.0) / 100.0;
    }

    private long calculateMaxWorkload(List<Map<String, Object>> staffWorkloadData) {
        return (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("totalShifts")).doubleValue())
                .max().orElse(0.0));
    }

    private long calculateMinWorkload(List<Map<String, Object>> staffWorkloadData) {
        return (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("totalShifts")).doubleValue())
                .min().orElse(0.0));
    }
}
