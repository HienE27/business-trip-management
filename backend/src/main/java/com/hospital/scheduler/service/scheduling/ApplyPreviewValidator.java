package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validate every preview item before any write happens. Returns the
 * resolved drafts plus the list of human-readable error messages so
 * the caller can decide whether to abort (atomic) or continue (legacy
 * partial save, currently not used by the apply path).
 *
 * <p>Design rules:
 * <ul>
 *   <li>Pure: no writes, no transaction side effects, no JPA flush.</li>
 *   <li>Deterministic: same input → same result, no random ordering.</li>
 *   <li>Single responsibility: does not build the response, save
 *       schedules, create compensation days, or send notifications —
 *       the {@link com.hospital.scheduler.service.AutoSchedulingService}
 *       caller does that after validation succeeds.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ApplyPreviewValidator {

    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final HolidayRepository holidayRepository;
    private final ScheduleRepository scheduleRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final CompensationDateCalculator compensationDateCalculator;

    /** A successfully resolved item that is ready to be saved. */
    public record ApplyDraft(
            Schedule schedule,
            ShiftRequirement requirement,
            Staff staff,
            ShiftType shiftType,
            LocalDate workDate,
            boolean requiresCompensationDay
    ) {}

    /** Validation outcome: drafts paired with the list of error messages. */
    public record ValidationResult(List<ApplyDraft> drafts, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /**
     * Validate every {@link PreviewScheduleItem} in the request. Items
     * marked for removal are skipped silently — that matches the existing
     * semantics of the apply path.
     *
     * @param period                       the period being applied to
     * @param request                      the apply-preview payload
     * @param siblingAssignments           in-memory map {@code staffId_date → set<shiftTypeId>};
     *                                     populated as drafts are accepted so that
     *                                     sibling-conflict detection (L01↔L02, L03↔L04)
     *                                     works without re-reading the in-progress saves
     * @param existingCompensationDayKeys  compensation-day keys already persisted for
     *                                     this period so conflict detection sees them
     * @param overwriteExisting            whether the apply will overwrite existing
     *                                     schedules; DB-level duplicate and conflict
     *                                     checks are skipped when {@code true} because
     *                                     the old rows will be deleted right after
     *                                     validation succeeds
     */
    public ValidationResult validate(
            SchedulePeriod period,
            AutoScheduleApplyPreviewRequestDTO request,
            Map<String, Set<String>> siblingAssignments,
            Set<String> existingCompensationDayKeys,
            boolean overwriteExisting) {

        List<String> errors = new ArrayList<>();
        List<ApplyDraft> drafts = new ArrayList<>();

        if (request.getSchedules() == null || request.getSchedules().isEmpty()) {
            errors.add("Danh sách lịch xem trước không được để trống");
            return new ValidationResult(drafts, errors);
        }

        // Index requirements once so the per-item resolver runs in O(1).
        List<ShiftRequirement> periodRequirements = requirementRepository.findByPeriodId(period.getId());
        Map<Integer, ShiftRequirement> byId = new HashMap<>();
        for (ShiftRequirement r : periodRequirements) {
            if (r.getId() != null) byId.put(r.getId(), r);
        }
        Map<String, List<ShiftRequirement>> byDateShift = new HashMap<>();
        for (ShiftRequirement r : periodRequirements) {
            if (r.getWorkDate() == null || r.getShiftType() == null || r.getShiftType().getId() == null) continue;
            String key = r.getWorkDate().toString() + "|" + r.getShiftType().getId();
            byDateShift.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        Set<String> removedScheduleKeys = request.getRemovedSchedules() == null
                ? Set.of()
                : request.getRemovedSchedules().stream()
                        .map(item -> scheduleKey(item.getStaffId(), item.getWorkDate(), item.getShiftTypeId()))
                        .collect(Collectors.toSet());

        for (int idx = 0; idx < request.getSchedules().size(); idx++) {
            PreviewScheduleItem item = request.getSchedules().get(idx);
            String label = "[index=" + idx + " staff=" + item.getStaffId()
                    + " workDate=" + item.getWorkDate()
                    + " shift=" + item.getShiftTypeId() + "]";

            String removedKey = scheduleKey(item.getStaffId(), item.getWorkDate(), item.getShiftTypeId());
            if (removedScheduleKeys.contains(removedKey)) {
                // skip removed item silently — same as existing behavior
                continue;
            }

            Staff staff = staffRepository.findById(item.getStaffId()).orElse(null);
            if (staff == null) {
                errors.add(label + " không tìm thấy nhân sự với ID: " + item.getStaffId());
                continue;
            }

            ShiftRequirement requirement;
            if (item.getRequirementId() != null) {
                requirement = byId.get(item.getRequirementId());
                if (requirement == null) {
                    errors.add(label + " requirementId không hợp lệ: " + item.getRequirementId());
                    continue;
                }
                String reqDate = requirement.getWorkDate().toString();
                String reqShift = requirement.getShiftType().getId();
                if (!reqDate.equals(item.getWorkDate()) || !reqShift.equals(item.getShiftTypeId())) {
                    errors.add(label + " requirementId " + item.getRequirementId()
                            + " không khớp (workDate=" + reqDate
                            + ", shiftTypeId=" + reqShift
                            + ") so với (workDate=" + item.getWorkDate()
                            + ", shiftTypeId=" + item.getShiftTypeId() + ")");
                    continue;
                }
            } else {
                String key = item.getWorkDate() + "|" + item.getShiftTypeId();
                List<ShiftRequirement> candidates = byDateShift.getOrDefault(key, List.of());
                if (candidates.isEmpty()) {
                    errors.add(label + " không tìm thấy requirement cho (workDate="
                            + item.getWorkDate() + ", shiftTypeId=" + item.getShiftTypeId() + ")");
                    continue;
                }
                if (candidates.size() > 1) {
                    ShiftRequirement matched = disambiguateByStaffSpecialty(candidates, staff);
                    if (matched == null) {
                        errors.add(label + " có nhiều requirement cho (workDate="
                                + item.getWorkDate() + ", shiftTypeId=" + item.getShiftTypeId()
                                + ") — client phải gửi requirementId (ứng viên: "
                                + candidates.stream().map(r -> String.valueOf(r.getId()))
                                        .collect(Collectors.joining(", ")) + ")");
                        continue;
                    }
                    requirement = matched;
                } else {
                    requirement = candidates.get(0);
                }
            }

            ShiftType shiftType = requirement.getShiftType();
            if (shiftType == null) {
                errors.add(label + " requirement " + requirement.getId() + " thiếu ShiftType");
                continue;
            }

            LocalDate workDate;
            try {
                workDate = LocalDate.parse(item.getWorkDate());
            } catch (Exception e) {
                errors.add(label + " workDate không hợp lệ: " + item.getWorkDate());
                continue;
            }

            if (holidayRepository.existsByHolidayDateAndIsActiveTrue(workDate)) {
                errors.add(label + " không thể phân công vào ngày lễ: " + workDate);
                continue;
            }

            String siblingKey = staff.getId() + "_" + workDate;
            Set<String> siblingShifts = siblingAssignments.computeIfAbsent(siblingKey, k -> new HashSet<>());

            // duplicate: same staff + date + shift within the same payload
            if (siblingShifts.contains(shiftType.getId())) {
                errors.add(label + " bị trùng với item khác trong cùng payload");
                continue;
            }
            // conflict L01↔L02 / L03↔L04 within the same payload
            boolean conflictInLoop = false;
            for (String other : siblingShifts) {
                if (conflicts(shiftType.getId(), other)) {
                    errors.add(label + " xung đột với " + other + " trong cùng payload");
                    conflictInLoop = true;
                    break;
                }
            }
            siblingShifts.add(shiftType.getId());
            if (conflictInLoop) {
                continue;
            }

            // DB-level checks: skip when overwrite=true because the old rows will be deleted
            // before saving the drafts — DB duplicates and conflicts would resolve themselves.
            if (!overwriteExisting) {
                if (conflictDetectionService.hasAnyConflict(
                        staff.getId(), workDate, shiftType.getId(), null, false, false)) {
                    errors.add(label + " đã có xung đột với dữ liệu đã lưu");
                    continue;
                }
                if (scheduleRepository.existsByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                        period.getId(), staff.getId(), shiftType.getId(), workDate)) {
                    errors.add(label + " đã tồn tại lịch với cùng staff/date/shift");
                    continue;
                }
            }

            Schedule schedule = Schedule.builder()
                    .period(period)
                    .staff(staff)
                    .shiftType(shiftType)
                    .workDate(workDate)
                    .requirement(requirement)
                    .hasConflict(false)
                    .build();

            drafts.add(new ApplyDraft(
                    schedule,
                    requirement,
                    staff,
                    shiftType,
                    workDate,
                    ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftType.getId())));
        }

        return new ValidationResult(drafts, errors);
    }

    /**
     * Mirror the legacy disambiguation in {@code AutoSchedulingService}:
     * prefer a requirement whose specialty matches the staff, fall back to
     * the catch-all (specialty IS NULL) row, otherwise return {@code null}
     * so the caller can throw an explicit error.
     */
    private ShiftRequirement disambiguateByStaffSpecialty(List<ShiftRequirement> candidates, Staff staff) {
        Integer staffSpecId = staff.getSpecialty() != null ? staff.getSpecialty().getId() : null;
        if (staffSpecId != null) {
            ShiftRequirement matched = null;
            ShiftRequirement catchAll = null;
            int matchCount = 0;
            for (ShiftRequirement c : candidates) {
                if (c.getSpecialty() == null) {
                    catchAll = c;
                } else if (staffSpecId.equals(c.getSpecialty().getId())) {
                    matched = c;
                    matchCount++;
                }
            }
            if (matchCount == 1) return matched;
            if (matchCount == 0) return catchAll;
            return null;
        }
        for (ShiftRequirement c : candidates) {
            if (c.getSpecialty() == null) return c;
        }
        return null;
    }

    private static boolean conflicts(String a, String b) {
        if (a == null || b == null) return false;
        return (ConflictDetectionService.SHIFT_TYPE_L01.equals(a)
                && ConflictDetectionService.SHIFT_TYPE_L02.equals(b))
                || (ConflictDetectionService.SHIFT_TYPE_L02.equals(a)
                && ConflictDetectionService.SHIFT_TYPE_L01.equals(b))
                || (ConflictDetectionService.SHIFT_TYPE_L03.equals(a)
                && ConflictDetectionService.SHIFT_TYPE_L04.equals(b))
                || (ConflictDetectionService.SHIFT_TYPE_L04.equals(a)
                && ConflictDetectionService.SHIFT_TYPE_L03.equals(b));
    }

    private static String scheduleKey(Integer staffId, String workDate, String shiftTypeId) {
        return staffId + "|" + workDate + "|" + shiftTypeId;
    }
}
