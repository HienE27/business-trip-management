package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for auto-generating and syncing shift requirements
 * from algorithm configuration. Extracted from the monolithic AutoSchedulingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementAutoGenService {

    private final ShiftRequirementRepository requirementRepository;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final EntityManager entityManager;

    /**
     * Sync existing requirements with current config so changes to min/max per day
     * take effect on the next scheduling run.
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
     * Generate requirements from algorithm configuration for a period.
     * Creates soft-target requirements for L01-L04 shift types.
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
            throw new BadRequestException("Không tìm thấy shift types L01-L04 trong hệ thống");
        }

        int generalPoolSize = Math.max(1, activeStaff.size());
        int daysInPeriod = (int) ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        int periodWeeks = Math.max(1, daysInPeriod / 7);

        // Pre-analyze staff count per specialty for fair L04 distribution
        Map<Integer, Integer> staffPerSpecialty = new HashMap<>();
        for (Staff s : activeStaff) {
            if (s.getSpecialty() != null) {
                staffPerSpecialty.merge(s.getSpecialty().getId(), 1, Integer::sum);
            }
        }

        List<Specialty> activeSpecialties = specialtyRepository.findByIsActiveTrue();

        // === [DEBUG L04-INVESTIGATION] temporary logging ===
        log.info("[L04-DBG] ===== generateRequirementsFromConfig START =====");
        log.info("[L04-DBG] periodId={} dateRange={}..{} daysInPeriod={} periodWeeks={}",
                period.getId(), period.getStartDate(), period.getEndDate(), daysInPeriod, periodWeeks);
        log.info("[L04-DBG] activeStaff.size={} generalPoolSize={} holidayMode={} removedShiftTypes={}",
                activeStaff.size(), generalPoolSize, config.holidayMode(), removedShiftTypes);
        log.info("[L04-DBG] CONFIG L01: min/day={} max/day={}", config.l01MinPerDay(), config.l01MaxPerDay());
        log.info("[L04-DBG] CONFIG L02: min/day={} max/day={}", config.l02MinPerDay(), config.l02MaxPerDay());
        log.info("[L04-DBG] CONFIG L03: min/day={} max/day={}", config.l03MinPerDay(), config.l03MaxPerDay());
        log.info("[L04-DBG] CONFIG L04: min/day={} max/day={} max/week={} crossSpecialty={} crossRatio={} balanceStrategy={}",
                config.l04MinPerDay(), config.l04MaxPerDay(), config.l04MaxPerWeek(),
                config.l04CrossSpecialty(), config.l04CrossSpecialtyRatio(), config.l04BalanceStrategy());
        log.info("[L04-DBG] l04AllowedSpecialties={} (NOTE: not applied in this generator — generator iterates findByIsActiveTrue)",
                config.l04AllowedSpecialties());
        log.info("[L04-DBG] activeSpecialties count={} (findByIsActiveTrue):",
                activeSpecialties.size());
        for (Specialty sp : activeSpecialties) {
            int cnt = staffPerSpecialty.getOrDefault(sp.getId(), 0);
            log.info("[L04-DBG]   - specialty id={} name='{}' activeStaffCount={}", sp.getId(), sp.getName(), cnt);
        }
        // === [END DEBUG BLOCK 1] ===

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
                    int staffInSpec = staffPerSpecialty.getOrDefault(specialty.getId(), 0);

                    // L04 per person per period from config (no hard ceiling of 10).
                    // l04MaxPerWeek=0 = unlimited → physical bound = daysInPeriod.
                    int maxL04PerPerson = config.l04MaxPerWeek() > 0
                            ? Math.min(config.l04MaxPerWeek() * periodWeeks, daysInPeriod)
                            : daysInPeriod;

                    // Total L04 needed for this specialty based on its own staff count.
                    // When cross-specialty is enabled, the algorithm can fill remaining
                    // slots from the general pool — but we generate requirements based
                    // on the specialty's own capacity to avoid overloading solo specialties.
                    int totalL04Needed = Math.max(1, staffInSpec * maxL04PerPerson);

                    // Smart interval-based distribution: spread requirements across period
                    // so small specialties don't get 1-per-day = 31 requirements.
                    // When cross-specialty on and specialty has < 3 staff, cap at 50% of days
                    // (general pool fills the rest).
                    double effectiveDays = daysInPeriod;
                    if (config.l04CrossSpecialty() && staffInSpec < 3) {
                        effectiveDays = Math.ceil(daysInPeriod * 0.5);
                    }
                    int interval = Math.max(1, (int) Math.ceil(effectiveDays / Math.max(1, totalL04Needed)));
                    int dayOfPeriod = (int) ChronoUnit.DAYS.between(period.getStartDate(), date);
                    boolean generateToday = (dayOfPeriod % interval == 0) && dayOfPeriod < effectiveDays;

                    // === [DEBUG L04-INVESTIGATION] per (specialty × day) ===
                    if (generateToday || dayOfPeriod == 0) {
                        log.info("[L04-DBG] spec='{}' date={} dayOfPeriod={} staffInSpec={} maxL04PerPerson={} totalL04Needed={} effectiveDays={} interval={} generateToday={}",
                                specialty.getName(), date, dayOfPeriod, staffInSpec, maxL04PerPerson,
                                totalL04Needed, effectiveDays, interval, generateToday);
                    }
                    // === [END DEBUG BLOCK 2] ===

	                    if (generateToday) {
	                        // When maxPerDay=0 (unlimited), derive per-day cap from total need ÷ generation days
	                        int perDayCap = config.l04MaxPerDay() > 0
	                                ? config.l04MaxPerDay()
	                                : Math.max(1, (int) Math.ceil((double) totalL04Needed / effectiveDays));
	                        int target = Math.max(config.l04MinPerDay(), Math.min(perDayCap, totalL04Needed));
                        generated.add(buildAutoRequirement(period, l04, date, specialty, target,
                                "AUTO_SOFT_TARGET:L04:" + date + ":" + specialty.getName()));
	                }
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

        // === [DEBUG L04-INVESTIGATION] summary of generated requirements ===
        Map<String, Integer> countByShiftType = new LinkedHashMap<>();
        Map<String, Integer> countByShiftTypeSpecialty = new LinkedHashMap<>();
        Map<String, Integer> sumTargetBySpecialty = new LinkedHashMap<>();
        for (ShiftRequirement r : deduplicated) {
            String stId = r.getShiftType().getId();
            countByShiftType.merge(stId, 1, Integer::sum);
            String specName = r.getSpecialty() != null ? r.getSpecialty().getName() : "null";
            String key = stId + "|" + specName;
            countByShiftTypeSpecialty.merge(key, 1, Integer::sum);
            if ("L04".equals(stId)) {
                sumTargetBySpecialty.merge(specName, r.getRequiredStaffCount(), Integer::sum);
            }
        }
        log.info("[L04-DBG] ===== SUMMARY generateRequirementsFromConfig END =====");
        log.info("[L04-DBG] total deduplicated requirements = {} (before dedup: {})", deduplicated.size(), generated.size());
        log.info("[L04-DBG] count by shiftType: {}", countByShiftType);
        log.info("[L04-DBG] count by (shiftType, specialty):");
        for (Map.Entry<String, Integer> e : countByShiftTypeSpecialty.entrySet()) {
            log.info("[L04-DBG]   - {}: {}", e.getKey(), e.getValue());
        }
        log.info("[L04-DBG] L04 sum of requiredStaffCount by specialty:");
        for (Map.Entry<String, Integer> e : sumTargetBySpecialty.entrySet()) {
            log.info("[L04-DBG]   - {}: totalTarget={}", e.getKey(), e.getValue());
        }
        // dump first few L04 requirements (sorted by date) for visual verification
        List<ShiftRequirement> l04Sample = deduplicated.stream()
                .filter(r -> "L04".equals(r.getShiftType().getId()))
                .sorted(java.util.Comparator.comparing(ShiftRequirement::getWorkDate))
                .limit(15)
                .collect(Collectors.toList());
        log.info("[L04-DBG] sample L04 requirements (first 15 sorted by date):");
        for (ShiftRequirement r : l04Sample) {
            log.info("[L04-DBG]   - date={} spec='{}' target={}", r.getWorkDate(),
                    r.getSpecialty() != null ? r.getSpecialty().getName() : "null", r.getRequiredStaffCount());
        }
        log.info("[L04-DBG] ===== END DEBUG BLOCK =====");
        // === [END DEBUG BLOCK 3] ===

        log.info("Generated {} soft-target requirements from auto config for period {}", deduplicated.size(), period.getId());
        return deduplicated;
    }

    /**
     * Build a single ShiftRequirement entity from the given parameters.
     */
    public ShiftRequirement buildAutoRequirement(
            SchedulePeriod period,
            ShiftType shiftType,
            LocalDate workDate,
            Specialty specialty,
            int targetStaffCount,
            String note) {
        return ShiftRequirement.builder()
                .period(period)
                .shiftType(shiftType)
                .workDate(workDate)
                .specialty(specialty)
                .requiredStaffCount(targetStaffCount)
                .note(note)
                .build();
    }

    /**
     * Resolve daily staff target from min/max config.
     * <p>
     * Logic: start from preferredMax (upper bound), clamp to min if needed, cap at pool.
     * Examples:
     *   min=3, max=4, pool=20 -> 4 (within [min,max], cap at pool)
     *   min=3, max=4, pool=2  -> 2 (below min, use pool)
     *   min=5, max=10, pool=20 -> 10 (within [min,max], cap at pool)
     *   min=5, max=10, pool=7  -> 7 (below min, use pool)
     *   min=5, max=0, pool=20  -> 5 (max=0 means unlimited, use min)
     */
    public int resolveSoftDailyTarget(int preferredMin, int preferredMax, int eligiblePoolSize) {
        int target;
        if (preferredMax > 0) {
            target = Math.min(preferredMax, eligiblePoolSize);  // Start from max, cap at pool
            target = Math.max(target, preferredMin);            // Ensure at least min
        } else {
            target = Math.max(preferredMin, 1);                  // max=0 means unlimited, use min
        }
        return Math.min(target, Math.max(1, eligiblePoolSize));
    }

    /**
     * Count active staff by specialty.
     */
    public int countActiveStaffBySpecialty(List<Staff> activeStaff, Integer specialtyId) {
        long count = activeStaff.stream()
                .filter(s -> s.getSpecialty() != null && Objects.equals(s.getSpecialty().getId(), specialtyId))
                .count();
        return Math.max(1, (int) count);
    }

    /**
     * Persist transient (unsaved) requirements to the database.
     * Merges with existing requirements to avoid duplicates.
     */
    public List<ShiftRequirement> persistRequirementsIfTransient(List<ShiftRequirement> requirements) {
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
