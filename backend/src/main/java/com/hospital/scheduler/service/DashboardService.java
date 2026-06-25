package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.dto.response.ScheduleAggregationResponse;
import com.hospital.scheduler.dto.response.StaffDailyCount;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final StaffRepository staffRepository;
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository exchangeRepository;

    public DashboardResponse getDashboardSummary(Integer periodId) {
        // Get schedules filtered by period (or all if no period specified)
        List<Schedule> periodSchedules = (periodId != null)
                ? scheduleRepository.findByPeriodId(periodId)
                : scheduleRepository.findAll();

        DashboardResponse.DashboardSummary summary = DashboardResponse.DashboardSummary.builder()
                .totalStaff(staffRepository.count())
                .activeStaff(staffRepository.findByIsActiveTrue().size())
                .totalSchedules((long) periodSchedules.size())
                .totalPeriods(periodRepository.count())
                .pendingLeaveRequests((long) leaveRequestRepository.findPendingRequests().size())
                .pendingScheduleExchanges((long) exchangeRepository.findByStatus(ScheduleExchange.ExchangeStatus.PENDING).size())
                .build();

        DashboardResponse.ShiftStatistics shiftStatistics = getShiftStatistics(periodId);
        DashboardResponse.LeaveRequestStatistics leaveRequestStatistics = getLeaveRequestStatistics();

        return DashboardResponse.builder()
                .summary(summary)
                .shiftStatistics(shiftStatistics)
                .leaveRequestStatistics(leaveRequestStatistics)
                .build();
    }

    public DashboardResponse.ShiftStatistics getShiftStatistics(Integer periodId) {
        List<Schedule> schedules = (periodId != null)
                ? scheduleRepository.findByPeriodId(periodId)
                : scheduleRepository.findAll();

        long L01Count = schedules.stream()
                .filter(s -> ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId()))
                .count();
        long L02Count = schedules.stream()
                .filter(s -> ConflictDetectionService.SHIFT_TYPE_L02.equals(s.getShiftType().getId()))
                .count();
        long L03Count = schedules.stream()
                .filter(s -> ConflictDetectionService.SHIFT_TYPE_L03.equals(s.getShiftType().getId()))
                .count();
        long L04Count = schedules.stream()
                .filter(s -> ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId()))
                .count();

        return DashboardResponse.ShiftStatistics.builder()
                .L01Count(L01Count)
                .L02Count(L02Count)
                .L03Count(L03Count)
                .L04Count(L04Count)
                .build();
    }

    public DashboardResponse.LeaveRequestStatistics getLeaveRequestStatistics() {
        List<LeaveRequest> all = leaveRequestRepository.findAll();

        return DashboardResponse.LeaveRequestStatistics.builder()
                .total(all.size())
                .pending(all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.PENDING).count())
                .approved(all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED).count())
                .rejected(all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.REJECTED).count())
                .build();
    }

    public List<DashboardResponse.StaffWorkloadStatistics> getStaffWorkloadByPeriod(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<Integer, List<Schedule>> staffSchedules = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        List<DashboardResponse.StaffWorkloadStatistics> result = new ArrayList<>();

        for (Map.Entry<Integer, List<Schedule>> entry : staffSchedules.entrySet()) {
            Staff staff = entry.getValue().get(0).getStaff();
            List<Schedule> staffScheduleList = entry.getValue();

            long L01Count = staffScheduleList.stream()
                    .filter(s -> ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId()))
                    .count();
            long L02Count = staffScheduleList.stream()
                    .filter(s -> ConflictDetectionService.SHIFT_TYPE_L02.equals(s.getShiftType().getId()))
                    .count();
            long L03Count = staffScheduleList.stream()
                    .filter(s -> ConflictDetectionService.SHIFT_TYPE_L03.equals(s.getShiftType().getId()))
                    .count();
            long L04Count = staffScheduleList.stream()
                    .filter(s -> ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId()))
                    .count();

            result.add(DashboardResponse.StaffWorkloadStatistics.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getFullName())
                    .scheduleCount(staffScheduleList.size())
                    .L01Count(L01Count)
                    .L02Count(L02Count)
                    .L03Count(L03Count)
                    .L04Count(L04Count)
                    .build());
        }

        return result;
    }

    /**
     * Thống kê chi tiết theo loại ca (L03/L04) theo tuần hoặc tháng.
     * Phục vụ M04-F05 và M05-F05.
     */
    public DashboardResponse.ShiftTypeDetailStatistics getShiftTypeDetailStatistics(
            Integer periodId, String shiftTypeId, String groupBy) {

        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        String finalShiftTypeId = (shiftTypeId != null) ? shiftTypeId : "L03";

        List<Schedule> filtered = schedules.stream()
                .filter(s -> finalShiftTypeId.equals(s.getShiftType().getId()))
                .toList();

        // Group by week or month
        Map<String, Long> byGroup = filtered.stream()
                .collect(Collectors.groupingBy(
                        s -> {
                            LocalDate d = s.getWorkDate();
                            if ("week".equalsIgnoreCase(groupBy)) {
                                int week = d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
                                return "W" + String.format("%02d", week);
                            } else {
                                return "Tháng " + d.getMonthValue();
                            }
                        },
                        Collectors.counting()
                ));

        // Group by staff
        Map<Integer, List<Schedule>> byStaffMap = filtered.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        List<DashboardResponse.ShiftTypeDetailStatistics.StaffShiftDetail> byStaff = byStaffMap.entrySet().stream()
                .map(entry -> {
                    Staff staff = entry.getValue().get(0).getStaff();
                    return DashboardResponse.ShiftTypeDetailStatistics.StaffShiftDetail.builder()
                            .staffId(staff.getId())
                            .staffName(staff.getFullName())
                            .totalDays(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(DashboardResponse.ShiftTypeDetailStatistics.StaffShiftDetail::getTotalDays).reversed())
                .toList();

        return DashboardResponse.ShiftTypeDetailStatistics.builder()
                .shiftTypeId(finalShiftTypeId)
                .shiftTypeName("L03".equals(finalShiftTypeId) ? "Phòng khám dịch vụ" : "Phòng khám chuyên gia")
                .totalDays(filtered.size())
                .byGroup(byGroup)
                .byStaff(byStaff)
                .build();
    }

    public List<DashboardResponse.PeriodSummary> getPeriodSummaries() {
        return periodRepository.findAll().stream()
                .map(period -> {
                    List<Schedule> schedules = scheduleRepository.findByPeriodId(period.getId());
                    Set<Integer> staffIds = schedules.stream()
                            .map(s -> s.getStaff().getId())
                            .collect(Collectors.toSet());

                    return DashboardResponse.PeriodSummary.builder()
                            .periodId(period.getId())
                            .periodName(period.getPeriodName())
                            .startDate(period.getStartDate())
                            .endDate(period.getEndDate())
                            .status(period.getStatus().name())
                            .scheduleCount(schedules.size())
                            .staffCount(staffIds.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getScheduleHeatmapData(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<LocalDate, Map<String, Long>> heatmap = new LinkedHashMap<>();

        for (Schedule schedule : schedules) {
            LocalDate date = schedule.getWorkDate();
            String shiftTypeId = schedule.getShiftType().getId();

            heatmap.computeIfAbsent(date, k -> new HashMap<>())
                    .merge(shiftTypeId, 1L, Long::sum);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("heatmap", heatmap);
        result.put("totalSchedules", schedules.size());
        result.put("totalStaff", schedules.stream().map(s -> s.getStaff().getId()).distinct().count());

        return result;
    }

    /**
     * Aggregate schedule counts for an arbitrary date range, optionally filtered
     * by staff. Used by dashboard week and month views that aren't bound to a
     * {@code SchedulePeriod}.
     */
    public ScheduleAggregationResponse aggregateByDateRange(LocalDate startDate, LocalDate endDate, Integer staffId) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate và endDate là bắt buộc");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate phải trước hoặc bằng endDate");
        }
        List<Schedule> all = scheduleRepository.findAll();
        List<Schedule> filtered = all.stream()
                .filter(s -> !s.getWorkDate().isBefore(startDate) && !s.getWorkDate().isAfter(endDate))
                .filter(s -> staffId == null || staffId.equals(s.getStaff().getId()))
                .toList();

        Map<LocalDate, Map<String, Long>> dailyCounts = new LinkedHashMap<>();
        for (Schedule schedule : filtered) {
            LocalDate date = schedule.getWorkDate();
            dailyCounts.computeIfAbsent(date, k -> new LinkedHashMap<>())
                    .merge(schedule.getShiftType().getId(), 1L, Long::sum);
        }

        Map<String, Long> shiftTypeTotals = new HashMap<>();
        for (Schedule schedule : filtered) {
            shiftTypeTotals.merge(schedule.getShiftType().getId(), 1L, Long::sum);
        }

        Map<Integer, Long> perStaffMap = filtered.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        // OPTIMIZATION: batch load all staff names in ONE query instead of N individual findById
        List<Integer> staffIds = new java.util.ArrayList<>(perStaffMap.keySet());
        Map<Integer, Staff> staffMap = staffIds.isEmpty() ? java.util.Collections.emptyMap()
                : staffRepository.findAllById(staffIds).stream()
                        .collect(Collectors.toMap(Staff::getId, s -> s));

        List<StaffDailyCount> perStaff = perStaffMap.entrySet().stream()
                .map(e -> {
                    Staff staff = staffMap.get(e.getKey());
                    return StaffDailyCount.builder()
                            .staffId(e.getKey())
                            .staffFullName(staff != null ? staff.getFullName() : null)
                            .scheduleCount(e.getValue())
                            .build();
                })
                .sorted(Comparator.comparing(StaffDailyCount::getStaffFullName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return ScheduleAggregationResponse.builder()
                .rangeStart(startDate)
                .rangeEnd(endDate)
                .daysInRange((int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1)
                .totalSchedules(filtered.size())
                .dailyCounts(dailyCounts)
                .shiftTypeTotals(shiftTypeTotals)
                .perStaff(perStaff)
                .build();
    }
}
