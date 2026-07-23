package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.service.scheduling.RequirementPreparationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Regenerates shift requirements for a period from current AutoGenConfig.
 * Deletes existing requirements and creates fresh ones based on latest config.
 *
 * Used to repair periods that were created with stale config (e.g., periods
 * where only 7 days of requirements exist when 30 days are expected).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftRequirementRegeneratorService {

    private final SchedulePeriodRepository periodRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final StaffRepository staffRepository;
    private final AlgorithmConfigService algorithmConfigService;
    private final RequirementPreparationService requirementPreparationService;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;

    public record RegenerateResult(int deletedCount, int newCount, int totalCount) {}

    @Transactional
    public RegenerateResult regenerate(Integer periodId) {
        var period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy kỳ lịch với ID: " + periodId));

        if (period.getStatus() != com.hospital.scheduler.entity.SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException(
                    "Chỉ có thể regenerate requirements khi kỳ lịch ở trạng thái DRAFT (hiện tại: " + period.getStatus() + ")");
        }

        AutoGenConfig config = algorithmConfigService.getAutoGenConfig()
                .orElseThrow(() -> new BadRequestException(
                        "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán."));

        if (!config.enabled()) {
            throw new BadRequestException(
                    "Cấu hình auto-gen chưa được bật.");
        }

        Integer actorId = currentActorId();

        // Delete existing
        List<ShiftRequirement> existing = requirementRepository.findByPeriodId(periodId);
        log.info("Regenerating requirements for period {}: deleting {} existing", periodId, existing.size());

        for (ShiftRequirement sr : existing) {
            auditHistoryService.logAction("shift_requirement", sr.getId(),
                    AuditHistory.ActionType.DELETE, sr, null, actorId);
        }
        requirementRepository.detachScheduleReferencesByPeriodNative(periodId);
        requirementRepository.deleteAllByPeriodIdNative(periodId);
        int deletedCount = existing.size();

        // Generate fresh from config
        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<ShiftRequirement> generated = requirementPreparationService
                .generateRequirementsFromConfig(period, config, activeStaff);

        // Persist
        List<ShiftRequirement> saved = new ArrayList<>();
        for (ShiftRequirement r : generated) {
            ShiftRequirement savedReq = requirementRepository.save(r);
            auditHistoryService.logAction("shift_requirement", savedReq.getId(),
                    AuditHistory.ActionType.INSERT, null, savedReq, actorId);
            saved.add(savedReq);
        }

        log.info("Regenerated requirements for period {}: {} deleted, {} new (total now: {})",
                periodId, deletedCount, saved.size(), saved.size());

        return new RegenerateResult(deletedCount, saved.size(), saved.size());
    }

    private Integer currentActorId() {
        try {
            var staff = authContextService.getCurrentStaff();
            return staff != null ? staff.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}