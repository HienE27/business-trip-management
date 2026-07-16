package com.hospital.scheduler.scheduling.telemetry;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that produces a heatmap for a scheduling period. Loads the staff /
 * requirements / leaves for the period and synthesizes a working solution
 * (round-robin assignment for unassigned slots) without running the full
 * search loop.
 */
@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final SchedulingConfig config;

    @Transactional(readOnly = true)
    public HeatmapBuilder.Heatmap buildForPeriod(Integer periodId,
                                                 HeatmapBuilder.Metric metric) {
        if (periodId == null) {
            throw new IllegalArgumentException("periodId must not be null");
        }
        List<ShiftRequirement> reqs =
                shiftRequirementRepository.findByPeriodId(periodId);
        if (reqs.isEmpty()) {
            return new HeatmapBuilder.Heatmap(metric, 0, LocalDate.now(),
                    LocalDate.now(), List.of(), 0.0);
        }
        LocalDate start = reqs.stream().map(ShiftRequirement::getWorkDate)
                .filter(d -> d != null).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = reqs.stream().map(ShiftRequirement::getWorkDate)
                .filter(d -> d != null).max(LocalDate::compareTo).orElse(start);

        List<Staff> activeStaff = staffRepository.findAll();
        List<LeaveRequest> leaves = leaveRequestRepository.findAll();
        Set<LocalDate> holidays = holidayRepository.findAll().stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        List<ShiftRequirementInfo> v10Reqs = reqs.stream()
                .map(ShiftRequirementInfo::from)
                .toList();

        SchedulingProblem problem = SchedulingProblem.withRequirements(
                activeStaff, v10Reqs, leaves, new HashSet<>(), holidays, config);

        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub.create(descriptor); // initialise hub (we don't reuse it for heatmap)

        WorkingSolution solution = WorkingSolution.fromProblem(config, descriptor);
        // Round-robin assign
        int next = 0;
        for (var r : v10Reqs) {
            List<Integer> eligible = problem.getEligibleStaff(r.id());
            if (eligible.isEmpty()) continue;
            int idx = eligible.get(next % eligible.size());
            solution.assign(r.id(), idx);
            next++;
        }

        HeatmapBuilder builder = new HeatmapBuilder();
        HeatmapBuilder.Heatmap raw = builder.build(solution, metric);
        // The descriptor builder derives start/end from assignments; align with period bounds
        int periodDays = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
        return new HeatmapBuilder.Heatmap(
                raw.metric(), Math.max(periodDays, raw.periodDays()),
                start, end, raw.rows(), raw.maxRaw());
    }
}
