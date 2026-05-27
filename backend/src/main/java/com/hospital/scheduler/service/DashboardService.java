package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.SchedulePeriod;
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
    private final ShiftTypeRepository shiftTypeRepository;
    private final CompensationDayRepository compensationDayRepository;

    public DashboardResponse getDashboardSummary() {
        DashboardResponse.DashboardSummary summary = DashboardResponse.DashboardSummary.builder()
                .totalStaff(staffRepository.count())
                .activeStaff(staffRepository.findByIsActiveTrue().size())
                .totalSchedules(scheduleRepository.count())
                .totalPeriods(periodRepository.count())
                .pendingLeaveRequests((long) leaveRequestRepository.findPendingRequests().size())
                .pendingScheduleExchanges((long) exchangeRepository.findByStatus(ScheduleExchange.ExchangeStatus.PENDING).size())
                .build();

        return DashboardResponse.builder()
                .summary(summary)
                .build();
    }

    public DashboardResponse.ShiftStatistics getShiftStatistics() {
        List<Schedule> schedules = scheduleRepository.findAll();

        long L01Count = schedules.stream()
                .filter(s -> "L01".equals(s.getShiftType().getId()))
                .count();
        long L02Count = schedules.stream()
                .filter(s -> "L02".equals(s.getShiftType().getId()))
                .count();
        long L03Count = schedules.stream()
                .filter(s -> "L03".equals(s.getShiftType().getId()))
                .count();
        long L04Count = schedules.stream()
                .filter(s -> "L04".equals(s.getShiftType().getId()))
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
                    .filter(s -> "L01".equals(s.getShiftType().getId()))
                    .count();
            long L02Count = staffScheduleList.stream()
                    .filter(s -> "L02".equals(s.getShiftType().getId()))
                    .count();
            long L03Count = staffScheduleList.stream()
                    .filter(s -> "L03".equals(s.getShiftType().getId()))
                    .count();
            long L04Count = staffScheduleList.stream()
                    .filter(s -> "L04".equals(s.getShiftType().getId()))
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

    public List<DashboardResponse.PeriodSummary> getPeriodSummaries() {
        return periodRepository.findAll().stream()
                .map(period -> {
                    List<Schedule> schedules = scheduleRepository.findByPeriodId(period.getId());
                    Set<Integer> staffIds = schedules.stream()
                            .map(s -> s.getStaff().getId())
                            .collect(Collectors.toSet());

                    return DashboardResponse.PeriodSummary.builder()
                            .periodId(period.getId())
                            .periodName(period.getName())
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
}
