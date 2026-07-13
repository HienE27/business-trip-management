package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ShiftRequirementRequest;
import com.hospital.scheduler.dto.response.ShiftRequirementResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * M07-F01 "Cấu hình tham số đầu vào": quản lý yêu cầu nhân sự theo
 * (periodId, workDate, shiftTypeId, optional specialtyId).
 *
 * Upsert logic: nếu đã có row trùng (periodId, workDate, shiftTypeId, specialtyId)
 * thì update requiredStaffCount + note; nếu chưa có thì tạo mới.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftRequirementService {

    private final ShiftRequirementRepository shiftRequirementRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final HolidayRepository holidayRepository;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;

    public List<ShiftRequirementResponse> getByPeriod(Integer periodId) {
        validatePeriodExists(periodId);
        return shiftRequirementRepository.findByPeriodId(periodId).stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getByPeriodAndDate(Integer periodId, LocalDate workDate) {
        validatePeriodExists(periodId);
        return shiftRequirementRepository.findByPeriodIdAndWorkDate(periodId, workDate).stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Bulk upsert: xử lý từng request — nếu đã có row trùng (periodId, workDate,
     * shiftTypeId, specialtyId) thì cập nhật; nếu chưa có thì tạo mới. Trả về
     * danh sách row sau khi xử lý.
     */
    @Transactional
    public List<ShiftRequirementResponse> upsert(Integer periodId, List<ShiftRequirementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Danh sách yêu cầu không được rỗng");
        }
        var period = validatePeriodExists(periodId);
        Integer actorId = currentActorId();

        List<ShiftRequirement> saved = new ArrayList<>();
        for (ShiftRequirementRequest req : requests) {
            validateNotHoliday(req.getWorkDate());
            Specialty specialty = resolveSpecialty(req.getSpecialtyId());
            ShiftType shiftType = shiftTypeRepository.findById(req.getShiftTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy loại ca với ID: " + req.getShiftTypeId()));

            Optional<ShiftRequirement> existing = specialty == null
                    ? shiftRequirementRepository.findByPeriodIdAndWorkDateAndShiftTypeId(
                            periodId, req.getWorkDate(), req.getShiftTypeId())
                            .filter(sr -> sr.getSpecialty() == null)
                    : Optional.empty();

            ShiftRequirement entity;
            boolean created;
            if (existing.isPresent()) {
                entity = existing.get();
                ShiftRequirement before = cloneForAudit(entity);
                entity.setRequiredStaffCount(req.getRequiredStaffCount());
                entity.setNote(req.getNote());
                created = false;
                auditHistoryService.logAction("shift_requirement", entity.getId(),
                        AuditHistory.ActionType.UPDATE, before, entity, actorId);
            } else {
                entity = ShiftRequirement.builder()
                        .period(period)
                        .workDate(req.getWorkDate())
                        .shiftType(shiftType)
                        .specialty(specialty)
                        .requiredStaffCount(req.getRequiredStaffCount())
                        .note(req.getNote())
                        .build();
                created = true;
            }
            ShiftRequirement persisted = shiftRequirementRepository.save(entity);
            if (created) {
                auditHistoryService.logAction("shift_requirement", persisted.getId(),
                        AuditHistory.ActionType.INSERT, null, persisted, actorId);
            }
            saved.add(persisted);
        }
        return saved.stream().map(ShiftRequirementResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public ShiftRequirementResponse update(Integer id, ShiftRequirementRequest req) {
        ShiftRequirement entity = shiftRequirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu ca với ID: " + id));
        ShiftRequirement before = cloneForAudit(entity);

        validateNotHoliday(req.getWorkDate());
        ShiftType shiftType = shiftTypeRepository.findById(req.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy loại ca với ID: " + req.getShiftTypeId()));
        Specialty specialty = resolveSpecialty(req.getSpecialtyId());

        entity.setWorkDate(req.getWorkDate());
        entity.setShiftType(shiftType);
        entity.setSpecialty(specialty);
        entity.setRequiredStaffCount(req.getRequiredStaffCount());
        entity.setNote(req.getNote());

        ShiftRequirement saved = shiftRequirementRepository.save(entity);
        auditHistoryService.logAction("shift_requirement", saved.getId(),
                AuditHistory.ActionType.UPDATE, before, saved, currentActorId());
        return ShiftRequirementResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(Integer id) {
        ShiftRequirement entity = shiftRequirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu ca với ID: " + id));
        ShiftRequirement before = cloneForAudit(entity);
        // Detach schedule references BEFORE delete — otherwise Hibernate flushes
        // a stale FK from Schedule.requirement → ShiftRequirement and throws
        // TransientPropertyValueException on cascade.
        shiftRequirementRepository.detachScheduleReferencesNative(id);
        shiftRequirementRepository.delete(entity);
        shiftRequirementRepository.flush();
        auditHistoryService.logAction("shift_requirement", id,
                AuditHistory.ActionType.DELETE, before, null, currentActorId());
    }

    /**
     * Bulk delete toàn bộ yêu cầu của một period. Dùng native query
     * deleteAllByPeriodIdNative để bypass JPA cache và tránh stale data khi
     * còn schedule đang tham chiếu.
     */
    @Transactional
    public int deleteAllByPeriod(Integer periodId) {
        validatePeriodExists(periodId);
        List<ShiftRequirement> existing = shiftRequirementRepository.findByPeriodId(periodId);
        Integer actorId = currentActorId();
        for (ShiftRequirement sr : existing) {
            auditHistoryService.logAction("shift_requirement", sr.getId(),
                    AuditHistory.ActionType.DELETE, sr, null, actorId);
        }
        // Detach schedule references for the whole period before bulk delete
        shiftRequirementRepository.detachScheduleReferencesByPeriodNative(periodId);
        shiftRequirementRepository.deleteAllByPeriodIdNative(periodId);
        return existing.size();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private com.hospital.scheduler.entity.SchedulePeriod validatePeriodExists(Integer periodId) {
        return periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy kỳ lịch với ID: " + periodId));
    }

    private Specialty resolveSpecialty(Integer specialtyId) {
        if (specialtyId == null) return null;
        return specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chuyên khoa với ID: " + specialtyId));
    }

    /**
     * BUG-m6 fix: reject shift requirements that fall on a configured holiday.
     * Schedule generation skips holidays anyway, so silently accepting the
     * requirement creates the illusion of coverage that never materialises.
     */
    private void validateNotHoliday(LocalDate workDate) {
        if (workDate == null) return;
        if (holidayRepository.existsByHolidayDateAndIsActiveTrue(workDate)) {
            throw new BadRequestException(
                    "Không thể tạo yêu cầu ca vào ngày lễ: " + workDate);
        }
    }

    private Integer currentActorId() {
        return authContextService.getCurrentStaff() != null
                ? authContextService.getCurrentStaff().getId()
                : null;
    }

    private ShiftRequirement cloneForAudit(ShiftRequirement src) {
        // Shallow snapshot — đủ để audit hiển thị trước/sau vì các ManyToOne đã có ID
        return ShiftRequirement.builder()
                .id(src.getId())
                .period(src.getPeriod())
                .workDate(src.getWorkDate())
                .shiftType(src.getShiftType())
                .specialty(src.getSpecialty())
                .requiredStaffCount(src.getRequiredStaffCount())
                .note(src.getNote())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .build();
    }
}