package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Suggests replacement staff for a given schedule.
 * Used by M07-F08.
 */
@Service
public class ReplacementSuggestionService {

    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final ConflictDetectionService conflictDetectionService;

    /**
     * Typed result for {@link #suggestTopCandidates} — includes workload so callers
     * can sort and limit without re-querying.
     */
    public record CandidateWithWorkload(
            Integer staffId,
            String fullName,
            String specialty,
            long currentShiftCount
    ) {}

    public ReplacementSuggestionService(ScheduleRepository scheduleRepository,
                                      StaffRepository staffRepository,
                                      ConflictDetectionService conflictDetectionService) {
        this.scheduleRepository = scheduleRepository;
        this.staffRepository = staffRepository;
        this.conflictDetectionService = conflictDetectionService;
    }

    /**
     * Lightweight top-N candidate lookup for auto-scheduling and leave-approval.
     * Delegates to {@link ConflictDetectionService#findReplacements} (batch query)
     * and enriches results with per-candidate workload from
     * {@link ScheduleRepository#countByStaffIdAndPeriodId}.
     *
     * @param schedule         original schedule being replaced
     * @param limit            max candidates to return (&le; 0 returns empty list)
     * @param excludedStaffIds staff IDs to skip (pass {@link Set#of()} if none)
     * @return list sorted ascending by current workload, capped at {@code limit}
     */
    public List<CandidateWithWorkload> suggestTopCandidates(
            Schedule schedule, int limit, Set<Integer> excludedStaffIds) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        if (limit <= 0) {
            return List.of();
        }

        Integer periodId = schedule.getPeriod() != null ? schedule.getPeriod().getId() : null;
        Integer originalStaffId = schedule.getStaff() != null ? schedule.getStaff().getId() : null;
        LocalDate workDate = schedule.getWorkDate();
        String shiftTypeId = schedule.getShiftType() != null ? schedule.getShiftType().getId() : null;

        List<Staff> candidates = conflictDetectionService.findReplacements(
                periodId, workDate, shiftTypeId, originalStaffId,
                limit, excludedStaffIds, true);

        List<CandidateWithWorkload> result = new ArrayList<>();
        for (Staff s : candidates) {
            long workload = periodId != null
                    ? scheduleRepository.countByStaffIdAndPeriodId(s.getId(), periodId)
                    : 0L;
            result.add(new CandidateWithWorkload(
                    s.getId(),
                    s.getFullName(),
                    s.getSpecialty() != null ? s.getSpecialty().getName() : null,
                    workload));
        }

        result.sort(Comparator.comparingLong(CandidateWithWorkload::currentShiftCount));
        return result.size() <= limit ? result : result.subList(0, limit);
    }

    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds) {
        return suggestReplacements(scheduleId, excludedStaffIds, false);
    }

    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds, boolean skipCompensationDay) {
        Schedule original = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + scheduleId));

        List<Staff> allStaff = staffRepository.findByIsActiveTrue();
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Staff candidate : allStaff) {
            if (candidate.getId().equals(original.getStaff().getId())) continue;
            if (excludedStaffIds != null && excludedStaffIds.contains(candidate.getId())) continue;

            // Specialty match required
            if (original.getStaff().getSpecialty() != null) {
                if (candidate.getSpecialty() == null ||
                        !candidate.getSpecialty().getId().equals(original.getStaff().getSpecialty().getId())) {
                    continue;
                }
            }

            // Conflict check (skip compensation day so staff on day off can still be surfaced)
            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    candidate.getId(), original.getWorkDate(), original.getShiftType().getId(),
                    scheduleId, skipCompensationDay);

            long currentWorkload = scheduleRepository.countByStaffIdAndPeriodId(
                    candidate.getId(), original.getPeriod().getId());

            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("staffId", candidate.getId());
            suggestion.put("staffName", candidate.getFullName());
            suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
            suggestion.put("currentWorkload", currentWorkload);
            suggestion.put("conflicts", conflicts);
            suggestion.put("isAvailable", conflicts.isEmpty());
            suggestion.put("reason", conflicts.isEmpty() ? "Không có xung đột" : String.join(", ", conflicts));
            suggestions.add(suggestion);
        }

        // Sort: available first
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
}
