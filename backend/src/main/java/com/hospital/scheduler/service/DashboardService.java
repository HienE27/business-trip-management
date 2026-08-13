package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.dto.response.ScheduleAggregationResponse;
import com.hospital.scheduler.dto.response.StaffDailyCount;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final StaffRepository staffRepository;
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository exchangeRepository;

    /**
     * BUG-m4 fix: warm Caffeine cache at startup so the first user-facing
     * dashboard request doesn't pay the 7.6s cold-start cost (first DB
     * query + JPA cold JIT + Hibernate session init).
     *
     * Runs after Spring finishes wiring so Hibernate is ready; failures are
     * logged but never propagated — we don't want a slow first dashboard
     * query to crash the entire application.
     *
     * To disable in dev set {@code app.dashboard.warmup-enabled=false}.
     */
    @PostConstruct
    void warmupDashboardCache() {
        boolean enabled = Boolean.parseBoolean(
                System.getProperty("app.dashboard.warmup-enabled", "true"));
        if (!enabled) {
            log.info("Dashboard cache warmup disabled by app.dashboard.warmup-enabled=false");
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            // Populate the two most common cache keys: null = no period, 0 = all periods.
            getDashboardSummary(null);
            getDashboardSummary(0);
            log.info("Dashboard cache warmed in {}ms", System.currentTimeMillis() - t0);
        } catch (Exception ex) {
            log.warn("Dashboard cache warmup failed ({}ms) — first request will be slow",
                    System.currentTimeMillis() - t0, ex);
        }
    }

    @Cacheable(value = CacheConfig.DASHBOARD_STATS_CACHE, key = "'summary-' + #periodId")
    public DashboardResponse getDashboardSummary(Integer periodId) {
        // OPTIMIZATION: Load schedules ONCE and reuse for all statistics
        List<Schedule> periodSchedules = (periodId != null)
                ? scheduleRepository.findByPeriodId(periodId)
                : scheduleRepository.findAll();

        DashboardResponse.DashboardSummary summary = DashboardResponse.DashboardSummary.builder()
                .totalStaff(staffRepository.countByIsActiveTrue())
                .activeStaff(staffRepository.findByIsActiveTrue().size())
                .totalSchedules((long) periodSchedules.size())
                .totalPeriods(periodRepository.count())
                .pendingLeaveRequests((long) leaveRequestRepository.findPendingRequests().size())
                .pendingScheduleExchanges((long) exchangeRepository.findByStatus(ScheduleExchange.ExchangeStatus.PENDING).size())
                .build();

        // Reuse periodSchedules instead of querying again
        DashboardResponse.ShiftStatistics shiftStatistics = computeShiftStatistics(periodSchedules);
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
        return computeShiftStatistics(schedules);
    }

    /**
     * Compute shift statistics from already-loaded schedules (avoids duplicate DB query).
     */
    private DashboardResponse.ShiftStatistics computeShiftStatistics(List<Schedule> schedules) {
        long L01Count = 0, L02Count = 0, L03Count = 0, L04Count = 0;
        for (Schedule s : schedules) {
            String id = s.getShiftType().getId();
            if ("L01".equals(id)) L01Count++;
            else if ("L02".equals(id)) L02Count++;
            else if ("L03".equals(id)) L03Count++;
            else if ("L04".equals(id)) L04Count++;
        }
        return DashboardResponse.ShiftStatistics.builder()
                .L01Count(L01Count)
                .L02Count(L02Count)
                .L03Count(L03Count)
                .L04Count(L04Count)
                .build();
    }

    /**
     * OPTIMIZATION: Use COUNT queries instead of loading all records into memory.
     * Counts by status directly at DB level for better performance.
     */
    public DashboardResponse.LeaveRequestStatistics getLeaveRequestStatistics() {
        long total = leaveRequestRepository.count();
        long pending = leaveRequestRepository.countByStatus(LeaveRequest.LeaveStatus.PENDING);
        long approved = leaveRequestRepository.countByStatus(LeaveRequest.LeaveStatus.APPROVED);
        long rejected = leaveRequestRepository.countByStatus(LeaveRequest.LeaveStatus.REJECTED);

        return DashboardResponse.LeaveRequestStatistics.builder()
                .total(total)
                .pending(pending)
                .approved(approved)
                .rejected(rejected)
                .build();
    }

    public Map<String, Long> getExchangeRequestCounts() {
        long total = exchangeRepository.count();
        long pending = exchangeRepository.countByStatus(ScheduleExchange.ExchangeStatus.PENDING);
        long approved = exchangeRepository.countByStatus(ScheduleExchange.ExchangeStatus.APPROVED);
        long rejected = exchangeRepository.countByStatus(ScheduleExchange.ExchangeStatus.REJECTED);
        long cancelled = exchangeRepository.countByStatus(ScheduleExchange.ExchangeStatus.CANCELLED);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", total);
        counts.put("pending", pending);
        counts.put("approved", approved);
        counts.put("rejected", rejected);
        counts.put("cancelled", cancelled);
        return counts;
    }

    public List<DashboardResponse.StaffWorkloadStatistics> getStaffWorkloadByPeriod(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        return buildWorkloadStatistics(schedules, /*includeAllStaff=*/ false);
    }

    /**
     * Paginated variant of {@link #getStaffWorkloadByPeriod(Integer)}. Workload
     * statistics are computed in-memory from the period's schedule list — for the
     * current 20-staff dataset this is fine. If staff grows large, swap to a
     * DB-aggregated query. Default sort: total shifts DESC (heaviest load first).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DashboardResponse.StaffWorkloadStatistics> getStaffWorkloadByPeriodPage(
            Integer periodId, org.springframework.data.domain.Pageable pageable) {
        List<DashboardResponse.StaffWorkloadStatistics> all = getStaffWorkloadByPeriod(periodId);
        // Sort DESC by total schedule count so heaviest-loaded staff come first.
        all.sort(Comparator.comparingLong(DashboardResponse.StaffWorkloadStatistics::getScheduleCount).reversed());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<DashboardResponse.StaffWorkloadStatistics> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(slice, pageable, all.size());
    }

    /**
     * Period-wide workload summary used by Reports/Staff KPI cards. Aggregates
     * shift counts in a single pass so the KPI numbers agree with the page rows,
     * even when the user paginates.
     */
    public WorkloadSummary getStaffWorkloadSummary(Integer periodId) {
        List<DashboardResponse.StaffWorkloadStatistics> all = getStaffWorkloadByPeriod(periodId);
        long total = all.stream().mapToLong(DashboardResponse.StaffWorkloadStatistics::getScheduleCount).sum();
        long overCap = all.stream()
                .filter(w -> w.getMaxShiftsPerMonth() != null && w.getScheduleCount() > w.getMaxShiftsPerMonth())
                .count();
        long underCap = all.stream()
                .filter(w -> w.getMaxShiftsPerMonth() != null && w.getScheduleCount() < w.getMaxShiftsPerMonth())
                .count();
        long balanced = all.stream()
                .filter(w -> w.getMaxShiftsPerMonth() != null && w.getScheduleCount() == w.getMaxShiftsPerMonth())
                .count();
        long noCap = all.stream().filter(w -> w.getMaxShiftsPerMonth() == null).count();
        return new WorkloadSummary(all.size(), total, overCap, underCap, balanced, noCap);
    }

    public record WorkloadSummary(
            int activeStaff,
            long totalAssignments,
            long overCapStaff,
            long underCapStaff,
            long balancedStaff,
            long noCapStaff) {}

    private List<DashboardResponse.StaffWorkloadStatistics> buildWorkloadStatistics(
            List<Schedule> schedules, boolean includeAllStaff) {
        Map<Integer, List<Schedule>> staffSchedules = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        List<DashboardResponse.StaffWorkloadStatistics> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Schedule>> entry : staffSchedules.entrySet()) {
            result.add(buildWorkloadStatistic(entry.getValue()));
        }
        return result;
    }

    private DashboardResponse.StaffWorkloadStatistics buildWorkloadStatistic(List<Schedule> staffScheduleList) {
        Staff staff = staffScheduleList.get(0).getStaff();

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

        Integer cap = staff.getMaxShiftsPerMonth();
        return DashboardResponse.StaffWorkloadStatistics.builder()
                .staffId(staff.getId())
                .staffName(staff.getFullName())
                .scheduleCount(staffScheduleList.size())
                .L01Count(L01Count)
                .L02Count(L02Count)
                .L03Count(L03Count)
                .L04Count(L04Count)
                .maxShiftsPerMonth(cap)
                .underCap(cap != null && staffScheduleList.size() < cap)
                .build();
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
        // BUGFIX (was BE#20): the previous implementation called
        // scheduleRepository.findByPeriodId(period.getId()) for every period in
        // the DB — classic N+1: 1 query for periods + N queries for schedules.
        // For 12 periods that's 13 round-trips just to render the dashboard
        // list. Now we aggregate via a single grouped query and bucket the
        // result in memory, reducing the work to 2 round-trips total.
        java.util.Map<Integer, long[]> aggregateByPeriodId = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.aggregateByPeriod()) {
            Integer periodId = (Integer) row[0];
            long totalSchedules = ((Number) row[1]).longValue();
            long distinctStaff = ((Number) row[2]).longValue();
            aggregateByPeriodId.put(periodId, new long[]{ totalSchedules, distinctStaff });
        }

        return periodRepository.findAll().stream()
                .map(period -> {
                    long[] counts = aggregateByPeriodId.getOrDefault(period.getId(), new long[]{0, 0});
                    return DashboardResponse.PeriodSummary.builder()
                            .periodId(period.getId())
                            .periodName(period.getPeriodName())
                            .startDate(period.getStartDate())
                            .endDate(period.getEndDate())
                            .status(period.getStatus().name())
                            .scheduleCount((int) counts[0])
                            .staffCount((int) counts[1])
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * BUGFIX (was BE#7): the /reports/monthly page used to fetch the full
     * schedule list (page slice) and derive scheduleCount + staffCount from
     * it. For a period with thousands of schedules that returns only the
     * first page's worth. This endpoint serves the aggregate directly so the
     * KPIs always reflect the entire period.
     */
    public DashboardResponse.PeriodSummary getPeriodSummary(int periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new com.hospital.scheduler.exception.ResourceNotFoundException(
                        "Không tìm thấy kỳ lịch với id: " + periodId));

        long totalSchedules = 0;
        long distinctStaff = 0;
        for (Object[] row : scheduleRepository.aggregateByPeriod()) {
            if (((Number) row[0]).intValue() == periodId) {
                totalSchedules = ((Number) row[1]).longValue();
                distinctStaff = ((Number) row[2]).longValue();
                break;
            }
        }
        return DashboardResponse.PeriodSummary.builder()
                .periodId(period.getId())
                .periodName(period.getPeriodName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus().name())
                .scheduleCount((int) totalSchedules)
                .staffCount((int) distinctStaff)
                .build();
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
     * OPTIMIZATION: Filter at DB level using date range instead of loading all schedules.
     * Uses batch staff lookup to avoid N+1 for staff names.
     */
    public ScheduleAggregationResponse aggregateByDateRange(LocalDate startDate, LocalDate endDate, Integer staffId) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate và endDate là bắt buộc");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate phải trước hoặc bằng endDate");
        }
        // Filter at DB level instead of loading all schedules into memory
        List<Schedule> filtered = scheduleRepository.findByDateRange(startDate, endDate);
        if (staffId != null) {
            filtered = filtered.stream()
                    .filter(s -> staffId.equals(s.getStaff().getId()))
                    .toList();
        }

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
