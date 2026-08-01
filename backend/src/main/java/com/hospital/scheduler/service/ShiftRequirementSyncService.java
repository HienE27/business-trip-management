package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sync persisted ShiftRequirement rows with current AutoGenConfig.
 *
 * Runs in a NEW transaction (REQUIRES_NEW) so it commits and releases row locks
 * BEFORE the long-running scheduler transaction begins. Without this isolation,
 * every preview/apply transaction holds row locks on shift_requirement for the
 * full duration of the GA/CSP run (often minutes), causing concurrent requests
 * on the same period to fail with "Lock wait timeout exceeded".
 *
 * Side note: do NOT call this from preview paths. Preview should be a read-only
 * "what-if"; only the apply path should mutate shift_requirement.required_staff_count.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftRequirementSyncService {

    private final ShiftRequirementRepository requirementRepository;
    private final HolidayRepository holidayRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int syncRequirementsForPeriod(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> existing = requirementRepository.findByPeriodId(period.getId());
        if (existing == null || existing.isEmpty()) {
            return 0;
        }

        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int generalPoolSize = Math.max(1, activeStaff.size());
        boolean skipL03OnHoliday = !"PARTIAL".equalsIgnoreCase(config.holidayMode());

        int changedCount = 0;
        for (ShiftRequirement req : existing) {
            if (req.getWorkDate() == null || req.getShiftType() == null) continue;
            boolean isHoliday = holidays.contains(req.getWorkDate());
            int newTarget;
            String typeId = req.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                newTarget = resolveSoftDailyTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L02.equals(typeId)) {
                newTarget = resolveSoftDailyTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L03.equals(typeId)) {
                int min = (isHoliday && skipL03OnHoliday) ? 0 : config.l03MinPerDay();
                newTarget = resolveSoftDailyTarget(min, config.l03MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)) {
                // L04 luôn strict-specialty (không cross): pool = staff đúng chuyên khoa.
                int specialtyPoolSize = countActiveStaffBySpecialty(activeStaff,
                        req.getSpecialty() != null ? req.getSpecialty().getId() : null);
                newTarget = resolveSoftDailyTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specialtyPoolSize);
            } else {
                continue;
            }
            if (req.getRequiredStaffCount() != newTarget) {
                req.setRequiredStaffCount(newTarget);
                changedCount++;
            }
        }

        if (changedCount > 0) {
            requirementRepository.saveAll(existing);
            log.info("Synced {}/{} shift_requirement rows for period {} (committed in REQUIRES_NEW txn)",
                    changedCount, existing.size(), period.getId());
        }
        return changedCount;
    }

    /**
     * Mirror of AutoSchedulingService.resolveSoftDailyTarget — kept here so this service
     * is self-contained. max > 0 caps to that value; max = 0 means "no upper limit"
     * so we use max(min, 1) and finally cap by the eligible pool.
     */
    static int resolveSoftDailyTarget(int preferredMin, int preferredMax, int eligiblePoolSize) {
        int target;
        if (preferredMax > 0) {
            target = Math.min(preferredMax, eligiblePoolSize);  // Start from max, cap at pool
            target = Math.max(target, preferredMin);            // Ensure at least min
        } else {
            target = Math.max(preferredMin, 1);                  // max=0 means unlimited, use min
        }
        return Math.min(target, Math.max(1, eligiblePoolSize));
    }

    private int countActiveStaffBySpecialty(List<Staff> activeStaff, Integer specialtyId) {
        if (specialtyId == null) return Math.max(1, activeStaff.size());
        long count = activeStaff.stream()
                .filter(s -> s.getSpecialty() != null && java.util.Objects.equals(s.getSpecialty().getId(), specialtyId))
                .count();
        return Math.max(1, (int) count);
    }
}