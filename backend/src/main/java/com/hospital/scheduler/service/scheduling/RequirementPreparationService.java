package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates, syncs and persists requirements for a scheduling period.
 * Wraps the requirements-side concerns of {@code runScheduling()} so that
 * {@code AutoSchedulingService} can stay focused on orchestration.
 */
@Slf4j
@Component
public class RequirementPreparationService {

    private final ShiftRequirementRepository requirementRepository;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final EntityManager entityManager;
    private final AlgorithmConfigService algorithmConfigService;

    public RequirementPreparationService(ShiftRequirementRepository requirementRepository,
                                       HolidayRepository holidayRepository,
                                       ShiftTypeRepository shiftTypeRepository,
                                       SpecialtyRepository specialtyRepository,
                                       EntityManager entityManager,
                                       AlgorithmConfigService algorithmConfigService) {
        this.requirementRepository = requirementRepository;
        this.holidayRepository = holidayRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.specialtyRepository = specialtyRepository;
        this.entityManager = entityManager;
        this.algorithmConfigService = algorithmConfigService;
    }

    /**
     * Prepare requirements for a scheduling run.
     *
     * @param period   the period being scheduled
     * @param save     when true, persist requirements and sync existing ones with current config
     * @param activeStaff  active staff list (used for pool sizing)
     * @return the prepared requirements (persisted if save=true)
     */
    public List<ShiftRequirement> prepareRequirements(SchedulePeriod period, boolean save,
                                                     List<Staff> activeStaff) {
        AutoGenConfig autoGenConfig = algorithmConfigService.getAutoGenConfig()
                .orElseThrow(() -> new com.hospital.scheduler.exception.BadRequestException(
                        "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán."));

        if (!autoGenConfig.enabled()) {
            throw new com.hospital.scheduler.exception.BadRequestException(
                    "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán.");
        }

        if (save) {
            syncExistingRequirementsWithConfig(period, autoGenConfig, activeStaff);
            List<ShiftRequirement> generated = generateRequirementsFromConfig(period, autoGenConfig, activeStaff);
            return persistRequirementsIfTransient(generated);
        } else {
            List<ShiftRequirement> existing = requirementRepository.findByPeriodId(period.getId());
            if (existing == null || existing.isEmpty()) {
                // First run against a fresh period — fall back to in-memory generation without persisting
                return generateRequirementsFromConfig(period, autoGenConfig, activeStaff);
            }
            return existing;
        }
    }

    /**
     * Re-sync persisted requirements with current auto-gen config.
     * Keeps id (FK safety) but updates requiredStaffCount.
     */
    public void syncExistingRequirementsWithConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> existing = requirementRepository.findByPeriodId(period.getId());
        if (existing == null || existing.isEmpty()) return;

        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int generalPoolSize = Math.max(1, activeStaff.size());
        boolean skipL03OnHoliday = !"PARTIAL".equalsIgnoreCase(config.holidayMode());

        boolean anyChanged = false;
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
                int specialtyPoolSize = config.l04CrossSpecialty()
                        ? generalPoolSize
                        : countActiveStaffBySpecialty(activeStaff, req.getSpecialty() != null ? req.getSpecialty().getId() : null);
                newTarget = resolveSoftDailyTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specialtyPoolSize);
            } else {
                continue;
            }
            if (req.getRequiredStaffCount() != newTarget) {
                req.setRequiredStaffCount(newTarget);
                anyChanged = true;
            }
        }
        if (anyChanged) {
            requirementRepository.saveAll(existing);
            entityManager.flush();
            log.info("Synced {} requirements with current config for period {}", existing.size(), period.getId());
        }
    }

    /**
     * Build new requirements from auto-gen config without persisting.
     */
    public List<ShiftRequirement> generateRequirementsFromConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> generated = new ArrayList<>();
        Set<String> removedShiftTypes = config.removedShiftTypes() == null
                ? Set.of()
                : config.removedShiftTypes().stream().map(String::toUpperCase).collect(Collectors.toSet());

        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAll().stream()
                .collect(Collectors.toMap(ShiftType::getId, s -> s));

        ShiftType l01 = shiftTypeMap.get("L01");
        ShiftType l02 = shiftTypeMap.get("L02");
        ShiftType l03 = shiftTypeMap.get("L03");
        ShiftType l04 = shiftTypeMap.get("L04");

        if (l01 == null || l02 == null || l03 == null || l04 == null) {
            throw new com.hospital.scheduler.exception.BadRequestException("Không tìm thấy shift types L01-L04 trong hệ thống");
        }

        int generalPoolSize = Math.max(1, activeStaff.size());
        List<Specialty> activeSpecialties = specialtyRepository.findByIsActiveTrue();
        LocalDate current = period.getStartDate();
        while (!current.isAfter(period.getEndDate())) {
            LocalDate date = current;
            boolean isHoliday = holidays.contains(date);
            boolean shouldGenerateFullDay = !isHoliday || "PARTIAL".equalsIgnoreCase(config.holidayMode());

            if (shouldGenerateFullDay && !removedShiftTypes.contains("L01")) {
                generated.add(buildAutoRequirement(period, l01, date, null,
                        resolveSoftDailyTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPoolSize),
                        "AUTO_SOFT_TARGET:L01:" + date));
            }
            if (shouldGenerateFullDay && !removedShiftTypes.contains("L02")) {
                generated.add(buildAutoRequirement(period, l02, date, null,
                        resolveSoftDailyTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPoolSize),
                        "AUTO_SOFT_TARGET:L02:" + date));
            }

            if (!removedShiftTypes.contains("L03")) {
                if ("PARTIAL".equalsIgnoreCase(config.holidayMode())) {
                    generated.add(buildAutoRequirement(period, l03, date, null,
                            resolveSoftDailyTarget(isHoliday ? 1 : config.l03MinPerDay(), config.l03MaxPerDay(), generalPoolSize),
                            "AUTO_SOFT_TARGET:L03:" + date));
                } else if (!isHoliday) {
                    generated.add(buildAutoRequirement(period, l03, date, null,
                            resolveSoftDailyTarget(config.l03MinPerDay(), config.l03MaxPerDay(), generalPoolSize),
                            "AUTO_SOFT_TARGET:L03:" + date));
                }
            }

            if (shouldGenerateFullDay && !removedShiftTypes.contains("L04")) {
                for (Specialty specialty : activeSpecialties) {
                    int specialtyPoolSize = config.l04CrossSpecialty()
                            ? generalPoolSize
                            : countActiveStaffBySpecialty(activeStaff, specialty.getId());
                    int target = resolveSoftDailyTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specialtyPoolSize);
                    generated.add(buildAutoRequirement(period, l04, date, specialty, target,
                            "AUTO_SOFT_TARGET:L04:" + date + ":" + specialty.getName()));
                }
            }

            current = current.plusDays(1);
        }

        Map<String, ShiftRequirement> uniqueReqs = new LinkedHashMap<>();
        for (ShiftRequirement r : generated) {
            String key = period.getId() + "_" + r.getWorkDate() + "_" + r.getShiftType().getId()
                    + "_" + (r.getSpecialty() != null ? r.getSpecialty().getId() : "null");
            uniqueReqs.putIfAbsent(key, r);
        }
        List<ShiftRequirement> deduplicated = new ArrayList<>(uniqueReqs.values());
        log.info("Generated {} soft-target requirements from auto config for period {}", deduplicated.size(), period.getId());
        return deduplicated;
    }

    private ShiftRequirement buildAutoRequirement(SchedulePeriod period, ShiftType shiftType, LocalDate workDate,
                                                  Specialty specialty, int targetStaffCount, String note) {
        return ShiftRequirement.builder()
                .period(period)
                .shiftType(shiftType)
                .workDate(workDate)
                .specialty(specialty)
                .requiredStaffCount(targetStaffCount)
                .note(note)
                .build();
    }

    private int resolveSoftDailyTarget(int preferredMin, int preferredMax, int eligiblePoolSize) {
        int target;
        if (preferredMax > 0) {
            target = Math.min(preferredMax, eligiblePoolSize);
            target = Math.max(target, preferredMin);
        } else {
            target = Math.max(preferredMin, 1);
        }
        return Math.min(target, Math.max(1, eligiblePoolSize));
    }

    private int countActiveStaffBySpecialty(List<Staff> activeStaff, Integer specialtyId) {
        long count = activeStaff.stream()
                .filter(s -> s.getSpecialty() != null && Objects.equals(s.getSpecialty().getId(), specialtyId))
                .count();
        return Math.max(1, (int) count);
    }

    private List<ShiftRequirement> persistRequirementsIfTransient(List<ShiftRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return requirements;

        List<ShiftRequirement> toSave = requirements.stream()
                .filter(r -> r != null && r.getId() == null)
                .collect(Collectors.toList());
        if (toSave.isEmpty()) return requirements;

        Map<String, ShiftRequirement> existing = new HashMap<>();
        for (ShiftRequirement req : requirementRepository.findByPeriodId(toSave.get(0).getPeriod().getId())) {
            String key = req.getWorkDate() + "|" + req.getShiftType().getId() + "|"
                    + (req.getSpecialty() != null ? req.getSpecialty().getId() : "null");
            existing.putIfAbsent(key, req);
        }

        List<ShiftRequirement> merged = new ArrayList<>(requirements.size());
        for (ShiftRequirement req : requirements) {
            if (req == null) { merged.add(null); continue; }
            if (req.getId() != null) { merged.add(req); continue; }
            String key = req.getWorkDate() + "|" + req.getShiftType().getId() + "|"
                    + (req.getSpecialty() != null ? req.getSpecialty().getId() : "null");
            ShiftRequirement already = existing.get(key);
            merged.add(already != null ? already : req);
        }

        List<ShiftRequirement> toInsert = merged.stream()
                .filter(r -> r != null && r.getId() == null)
                .collect(Collectors.toList());
        if (!toInsert.isEmpty()) {
            Map<String, ShiftRequirement> dedup = new LinkedHashMap<>();
            for (ShiftRequirement r : toInsert) {
                String key = r.getWorkDate() + "|" + r.getShiftType().getId() + "|"
                        + (r.getSpecialty() != null ? r.getSpecialty().getId() : "null");
                dedup.putIfAbsent(key, r);
            }
            List<ShiftRequirement> saved = requirementRepository.saveAll(new ArrayList<>(dedup.values()));
            for (int i = 0; i < merged.size(); i++) {
                ShiftRequirement cur = merged.get(i);
                if (cur != null && cur.getId() == null) {
                    for (ShiftRequirement s : saved) {
                        if (s.getWorkDate().equals(cur.getWorkDate())
                                && s.getShiftType().getId().equals(cur.getShiftType().getId())
                                && Objects.equals(
                                        s.getSpecialty() != null ? s.getSpecialty().getId() : null,
                                        cur.getSpecialty() != null ? cur.getSpecialty().getId() : null)) {
                            merged.set(i, s);
                            break;
                        }
                    }
                }
            }
        }
        return merged;
    }
}
