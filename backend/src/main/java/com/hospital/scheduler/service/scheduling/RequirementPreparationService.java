package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;

    public RequirementPreparationService(ShiftRequirementRepository requirementRepository,
                                       HolidayRepository holidayRepository,
                                       ShiftTypeRepository shiftTypeRepository,
                                       SpecialtyRepository specialtyRepository,
                                       EntityManager entityManager,
                                       AlgorithmConfigService algorithmConfigService,
                                       LeaveRequestRepository leaveRequestRepository,
                                       CompensationDayRepository compensationDayRepository,
                                       ScheduleRepository scheduleRepository) {
        this.requirementRepository = requirementRepository;
        this.holidayRepository = holidayRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.specialtyRepository = specialtyRepository;
        this.entityManager = entityManager;
        this.algorithmConfigService = algorithmConfigService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.compensationDayRepository = compensationDayRepository;
        this.scheduleRepository = scheduleRepository;
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
        return prepareRequirements(period, save, activeStaff, true);
    }

    /**
     * Prepare requirements for a scheduling run.
     *
     * @param period   the period being scheduled
     * @param save     when true, persist requirements and sync existing ones with current config
     * @param activeStaff  active staff list (used for pool sizing)
     * @param includeL04FromConfig  when false, skip generating L04 rows from config. Used by the
     *                              apply-preview path: the adaptive L04 set was already persisted
     *                              at preview time (syncAdaptiveL04Requirements) and the apply
     *                              payload pins those rows by requirementId. Re-generating config
     *                              L04 here would insert a SECOND, different L04 set (buildL04OpenSchedule
     *                              reads busy state from DB, adaptive reads phase-A in-memory
     *                              assignments) → persisted capacity inflates (77 adaptive + 13 config)
     *                              → applied coverage (92.9%) diverges from preview (100%).
     * @return the prepared requirements (persisted if save=true)
     */
    public List<ShiftRequirement> prepareRequirements(SchedulePeriod period, boolean save,
                                                     List<Staff> activeStaff, boolean includeL04FromConfig) {
        AutoGenConfig autoGenConfig = algorithmConfigService.getAutoGenConfig()
                .orElseThrow(() -> new com.hospital.scheduler.exception.BadRequestException(
                        "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán."));

        if (!autoGenConfig.enabled()) {
            throw new com.hospital.scheduler.exception.BadRequestException(
                    "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán.");
        }

        // Always generate fresh from current config so config changes (removedShiftTypes,
        // min/max per day) take effect on preview, not only on save. The in-memory
        // generated list reflects the current config; persistence happens only when save=true.
        List<ShiftRequirement> generated = generateRequirementsFromConfig(period, autoGenConfig, activeStaff, includeL04FromConfig);
        if (save) {
            // Sync deletes stale rows (e.g. removed shift types) then persist the fresh set.
            syncExistingRequirementsWithConfig(period, autoGenConfig, activeStaff);
            return persistRequirementsIfTransient(generated);
        }
        return generated;
    }

    /**
     * Re-sync persisted requirements with current auto-gen config.
     * Keeps id (FK safety) but updates requiredStaffCount.
     */
	    public void syncExistingRequirementsWithConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
	        Set<String> removedShiftTypes = config.removedShiftTypes() == null
	                ? Set.of()
	                : config.removedShiftTypes().stream().map(String::toUpperCase).collect(Collectors.toSet());

	        // Xóa requirement cũ cho shift type bị bỏ qua (removedShiftTypes)
	        if (!removedShiftTypes.isEmpty()) {
	            requirementRepository.detachScheduleReferencesByPeriodNative(period.getId());
	            int deleted = requirementRepository.deleteByPeriodIdAndShiftTypeIds(
	                    period.getId(), removedShiftTypes);
	            if (deleted > 0) {
	                entityManager.flush();
	                entityManager.clear();
	                log.info("Deleted {} requirements for removed shift types {} from period {}",
	                        deleted, removedShiftTypes, period.getId());
	            }
	        }

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
                // L04 luôn strict-specialty (không cross): pool = staff đúng chuyên khoa.
                int specialtyPoolSize = countActiveStaffBySpecialty(activeStaff,
                        req.getSpecialty() != null ? req.getSpecialty().getId() : null);
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
        return generateRequirementsFromConfig(period, config, activeStaff, true);
    }

    /**
     * Build new requirements from auto-gen config without persisting.
     *
     * @param includeL04FromConfig when false, L04 rows are skipped entirely (see
     *                             {@link #prepareRequirements(SchedulePeriod, boolean, List, boolean)}).
     */
    public List<ShiftRequirement> generateRequirementsFromConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff, boolean includeL04FromConfig) {
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
        // Lịch mở PK chuyên gia theo ngày bác sĩ đúng khoa rảnh (thay thế
        // cross-specialty): mỗi tuần chọn ngày có nhân lực rảnh nhất.
        Map<LocalDate, Map<Integer, Integer>> l04FreeByDate = buildL04OpenSchedule(period, activeStaff, activeSpecialties);
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

            if (shouldGenerateFullDay && !removedShiftTypes.contains("L04") && includeL04FromConfig) {
                Map<Integer, Integer> freeBySpec = l04FreeByDate.get(date);
                if (freeBySpec != null) {
                    for (Specialty specialty : activeSpecialties) {
                        int freeCount = freeBySpec.getOrDefault(specialty.getId(), 0);
                        if (freeCount <= 0) continue; // hôm đó không ai đúng khoa rảnh → PK đóng
                        int desired = resolveSoftDailyTarget(
                                config.l04MinPerDay(), config.l04MaxPerDay(),
                                countActiveStaffBySpecialty(activeStaff, specialty.getId()));
                        // Số slot không được vượt số bác sĩ đúng khoa rảnh hôm đó
                        // → không bao giờ phải cross-specialty, slot luôn xếp đủ.
                        int target = Math.min(desired, freeCount);
                        if (target <= 0) continue;
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
        // Both 0 → no requirement for this shift type
        if (preferredMax == 0 && preferredMin == 0) {
            return 0;
        }
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
        return (int) count;
    }

    /**
     * Lịch mở PK chuyên gia theo nhân lực rảnh (thay thế cross-specialty):
     * mỗi tuần (T2-T7, không CN), mỗi chuyên khoa mở PK vào {@code openDays}
     * ngày có nhiều bác sĩ đúng khoa rảnh nhất. Bác sĩ "bận" = nghỉ phép
     * APPROVED, nghỉ bù, hoặc đã có L01/L02/L03 cùng ngày trong DB.
     * Ngày nào không còn ai đúng khoa rảnh → PK đóng (không sinh requirement)
     * → không bao giờ cần cross-specialty, slot luôn xếp đủ.
     *
     * @return date → (specialtyId → số bác sĩ đúng khoa rảnh hôm đó)
     */
    private Map<LocalDate, Map<Integer, Integer>> buildL04OpenSchedule(
            SchedulePeriod period, List<Staff> activeStaff, List<Specialty> activeSpecialties) {
        Map<LocalDate, Map<Integer, Integer>> result = new HashMap<>();
        if (period == null || period.getStartDate() == null || activeSpecialties.isEmpty()) {
            return result;
        }
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        // Nhân sự bận hôm đó: nghỉ phép APPROVED
        Set<String> busyKeys = new HashSet<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(start, end)) {
            if (lr.getStaff() == null || lr.getStartDate() == null) continue;
            for (LocalDate d = lr.getStartDate(); !d.isAfter(lr.getEndDate()); d = d.plusDays(1)) {
                if (!d.isBefore(start) && !d.isAfter(end)) busyKeys.add(lr.getStaff().getId() + "_" + d);
            }
        }
        // Nghỉ bù
        for (CompensationDay cd : compensationDayRepository.findInRange(start.minusDays(1), end.plusDays(1))) {
            if (cd.getStaff() == null || cd.getCompensationDate() == null) continue;
            if (!cd.getCompensationDate().isBefore(start) && !cd.getCompensationDate().isAfter(end)) {
                busyKeys.add(cd.getStaff().getId() + "_" + cd.getCompensationDate());
            }
        }
        // L01/L02/L03 đã ghi DB từ lần apply trước — cùng ngày không làm L04
        for (Schedule s : scheduleRepository.findByPeriodId(period.getId())) {
            if (s.getStaff() == null || s.getWorkDate() == null || s.getShiftType() == null) continue;
            String t = s.getShiftType().getId();
            if (!"L01".equals(t) && !"L02".equals(t) && !"L03".equals(t)) continue;
            busyKeys.add(s.getStaff().getId() + "_" + s.getWorkDate());
        }

        // Bác sĩ active theo chuyên khoa
        Map<Integer, List<Staff>> staffBySpec = new HashMap<>();
        for (Staff s : activeStaff) {
            if (s.getSpecialty() == null) continue;
            staffBySpec.computeIfAbsent(s.getSpecialty().getId(), k -> new ArrayList<>()).add(s);
        }

        // Gom ngày T2-T7 theo tuần (CN đóng cửa)
        Map<LocalDate, List<LocalDate>> weekDays = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weekDays.computeIfAbsent(monday, k -> new ArrayList<>()).add(d);
        }

        for (Map.Entry<LocalDate, List<LocalDate>> week : weekDays.entrySet()) {
            for (Specialty spec : activeSpecialties) {
                List<Staff> specStaff = staffBySpec.getOrDefault(spec.getId(), List.of());
                int strictPool = specStaff.size();
                // Khoa không có bác sĩ active (vd Răng) → không thể mở PK
                if (strictPool == 0) continue;
                // Số ngày mở/tuần theo số bác sĩ: 1 bs → 1 ngày, ≥6 bs → cả tuần
                int openDays = Math.min(6, Math.max(1, strictPool));
                // Chọn openDays ngày có nhiều bác sĩ rảnh nhất (ưu tiên thứ sớm cho lịch ổn định)
                List<LocalDate> chosen = week.getValue().stream()
                        .sorted(Comparator
                                .comparingInt((LocalDate d) -> countFree(d, specStaff, busyKeys)).reversed()
                                .thenComparingInt((LocalDate d) -> d.getDayOfWeek().getValue()))
                        .limit(openDays)
                        .collect(Collectors.toList());
                for (LocalDate d : chosen) {
                    int free = countFree(d, specStaff, busyKeys);
                    if (free <= 0) continue; // hôm đó không ai rảnh → PK đóng
                    result.computeIfAbsent(d, k -> new HashMap<>()).put(spec.getId(), free);
                }
            }
        }
        return result;
    }

    private int countFree(LocalDate d, List<Staff> specStaff, Set<String> busyKeys) {
        int free = 0;
        for (Staff s : specStaff) {
            if (!busyKeys.contains(s.getId() + "_" + d)) free++;
        }
        return free;
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
