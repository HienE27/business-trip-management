package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulated Annealing refinement for schedule fairness + fatigue.
 *
 * <p>Operates on an in-memory map representation ({@code staffId → date → shiftType})
 * to avoid mutating JPA entities during search. Returns a list of {@link Change}
 * that the caller applies to the real Schedule entities.
 *
 * <p>This is the "SA refinement" half of Hybrid (Enhanced Greedy + SA) from the
 * benchmark. The Enhanced Greedy half is implemented as the fatigue-awareness
 * tier in {@code AutoSchedulingService#runGreedy}.
 */
@Slf4j
@Component
public class SimulatedAnnealingOptimizer {

    // --- SA parameters (matching benchmark) ---
    private static final int MAX_ITER = 2000;
    private static final double INIT_T = 100.0;
    private static final double ALPHA = 0.995;
    private static final double MIN_T = 1.0;

    /** Score weights (matching benchmark Hybrid config). */
    private static final double W_FAIRNESS = 0.20;
    private static final double W_FATIGUE = 0.15;
    private static final double W_COVERAGE = 0.50;
    private static final double W_COMPENSATION = 0.15;

    /** A single shift change: which Schedule, what new shift type. */
    public record Change(Schedule schedule, ShiftType newShiftType) {}

    /**
     * Run SA refinement on a schedule. Returns a list of suggested changes;
     * the caller is responsible for applying them and persisting.
     *
     * @param schedules    existing schedule (JPA entities, not modified)
     * @param activeStaff  all active staff in the period
     * @param requirements all shift requirements for the period
     * @param shiftTypes   available shift types (L01…L04)
     * @param leaveIndex   staff leave days (staffId → set of dates)
     * @param compDays     compensation day set (staffId+date)
     * @return list of changes to apply, or empty list if no improvement found
     */
    public List<Change> optimize(
            List<Schedule> schedules,
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            List<ShiftType> shiftTypes,
            Map<Integer, Set<LocalDate>> leaveIndex,
            Set<String> compDays) {

        if (schedules == null || schedules.size() < 3) return List.of();

        long startTime = System.currentTimeMillis();

        // Build type lookup: id → ShiftType
        Map<String, ShiftType> typeMap = shiftTypes.stream()
                .collect(Collectors.toMap(ShiftType::getId, t -> t, (a, b) -> a));

        // Build mutable in-memory state: staffId → (date → shiftType)
        Map<Integer, Map<LocalDate, String>> state = new HashMap<>();
        for (Schedule s : schedules) {
            if (s.getStaff() == null || s.getShiftType() == null) continue;
            state.computeIfAbsent(s.getStaff().getId(), k -> new HashMap<>())
                    .put(s.getWorkDate(), s.getShiftType().getId());
        }

        // Collect all staff IDs, dates, and requirement fill counts
        Set<Integer> staffIds = activeStaff.stream().map(Staff::getId).collect(Collectors.toSet());
        Set<LocalDate> allDates = schedules.stream().map(Schedule::getWorkDate).collect(Collectors.toSet());
        Map<String, Integer> dateTypeDemand = buildDemand(requirements);

        // Current state scoring
        double currentScore = scoreState(state, staffIds, allDates, dateTypeDemand, leaveIndex, compDays);
        double bestScore = currentScore;
        Map<Integer, Map<LocalDate, String>> bestState = deepCopy(state);

        double T = INIT_T;
        int accepted = 0, improved = 0;

        for (int iter = 0; iter < MAX_ITER && T > MIN_T; iter++) {
            // Pick a random assignment to mutate
            List<Map.Entry<Integer, Map.Entry<LocalDate, String>>> entries = flattenState(state);
            if (entries.isEmpty()) break;

            var pick = entries.get(new Random().nextInt(entries.size()));
            int sid = pick.getKey();
            LocalDate date = pick.getValue().getKey();
            String oldType = pick.getValue().getValue();

            // Keep L01 fixed (compensation day side-effects)
            if ("L01".equals(oldType)) continue;

            String newType = pickRandomType(oldType);
            if (newType == null) continue;

            // Constraint check
            if (!isValidMove(sid, date, oldType, newType, state, leaveIndex, compDays)) continue;

            // Apply
            state.get(sid).put(date, newType);
            double newScore = scoreState(state, staffIds, allDates, dateTypeDemand, leaveIndex, compDays);
            double delta = newScore - currentScore;

            if (delta > 0 || Math.exp(delta / T) > new Random().nextDouble()) {
                currentScore = newScore;
                accepted++;
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestState = deepCopy(state);
                    improved++;
                }
            } else {
                state.get(sid).put(date, oldType); // revert
            }

            T *= ALPHA;
        }

        // Build change list from best state
        List<Change> changes = buildChanges(schedules, bestState, typeMap);

        log.info("SA optimizer: accepted={}, improved={}, score={}→{}, changes={}, time={}ms",
                accepted, improved,
                String.format("%.2f", bestScore), String.format("%.2f", currentScore),
                changes.size(), System.currentTimeMillis() - startTime);

        return changes;
    }

    // ─────────────────────────────────────────────────────────────
    // Scoring
    // ─────────────────────────────────────────────────────────────

    private double scoreState(
            Map<Integer, Map<LocalDate, String>> state,
            Set<Integer> staffIds,
            Set<LocalDate> allDates,
            Map<String, Integer> dateTypeDemand,
            Map<Integer, Set<LocalDate>> leaveIndex,
            Set<String> compDays) {

        // Coverage — how many assigned slots out of demand
        int totalDemand = dateTypeDemand.values().stream().mapToInt(Integer::intValue).sum();
        int assigned = 0;
        for (var staffEntry : state.entrySet()) {
            assigned += staffEntry.getValue().values().stream()
                    .filter(t -> !"OFF".equals(t))
                    .count();
        }
        double coverage = totalDemand > 0 ? 100.0 * Math.min(assigned, totalDemand) / totalDemand : 0;

        // Fairness — inverse of stddev of total shift counts
        List<Integer> workloads = staffIds.stream()
                .map(id -> (int) state.getOrDefault(id, Map.of()).values().stream()
                        .filter(t -> !"OFF".equals(t)).count())
                .toList();
        double avg = workloads.stream().mapToInt(Integer::intValue).average().orElse(1);
        double variance = workloads.stream()
                .mapToDouble(w -> Math.pow(w - avg, 2))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);
        double fairness = avg > 0 ? Math.max(0, 100.0 * (1.0 - stddev / avg)) : 0;

        // Fatigue — penalty for consecutive days
        double fatiguePenalty = 0;
        for (var staffEntry : state.entrySet()) {
            List<LocalDate> workDates = staffEntry.getValue().entrySet().stream()
                    .filter(e -> !"OFF".equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            for (int i = 1; i < workDates.size(); i++) {
                long gap = ChronoUnit.DAYS.between(workDates.get(i - 1), workDates.get(i));
                if (gap <= 1) fatiguePenalty += 20;
            }
        }
        double fatigue = Math.max(0, 100.0 - fatiguePenalty / Math.max(1, staffIds.size()));

        // Compensation — count violations (staff assigned on their comp day)
        long compViolations = 0;
        for (var staffEntry : state.entrySet()) {
            for (var dateEntry : staffEntry.getValue().entrySet()) {
                if ("OFF".equals(dateEntry.getValue())) continue;
                String key = staffEntry.getKey() + "_" + dateEntry.getKey();
                if (compDays.contains(key)) compViolations++;
            }
        }
        double totalCompDays = Math.max(1, compDays.size());
        double compensation = Math.max(0, 100.0 * (1.0 - compViolations / totalCompDays));

        return W_COVERAGE * coverage
             + W_FAIRNESS * fairness
             + W_FATIGUE * fatigue
             + W_COMPENSATION * compensation;
    }

    // ─────────────────────────────────────────────────────────────
    // Move generation & validation
    // ─────────────────────────────────────────────────────────────

    private String pickRandomType(String exclude) {
        List<String> types = new ArrayList<>(List.of("L01", "L02", "L03", "L04"));
        types.remove(exclude);
        return types.isEmpty() ? null : types.get(new Random().nextInt(types.size()));
    }

    private boolean isValidMove(
            int sid, LocalDate date, String oldType, String newType,
            Map<Integer, Map<LocalDate, String>> state,
            Map<Integer, Set<LocalDate>> leaveIndex,
            Set<String> compDays) {

        // No leave conflict
        if (leaveIndex.getOrDefault(sid, Set.of()).contains(date)) return false;

        // No compensation day conflict
        if (compDays.contains(sid + "_" + date)) return false;

        // No same-day shift type conflict (L01↔L02, L03↔L04)
        Map<LocalDate, String> staffDays = state.getOrDefault(sid, Map.of());
        String existingSameDay = staffDays.get(date);
        if (existingSameDay != null && !existingSameDay.equals(oldType)) {
            if (isConflict(existingSameDay, newType)) return false;
        }

        // L01: check 3-day gap
        if ("L01".equals(newType)) {
            for (var entry : staffDays.entrySet()) {
                LocalDate d = entry.getKey();
                if ("L01".equals(entry.getValue()) && !d.equals(date)) {
                    long gap = Math.abs(ChronoUnit.DAYS.between(d, date));
                    if (gap < 3) return false;
                }
            }
        }

        return true;
    }

    /** L01↔L02 conflict, L03↔L04 conflict */
    private boolean isConflict(String a, String b) {
        return ("L01".equals(a) && "L02".equals(b))
            || ("L02".equals(a) && "L01".equals(b))
            || ("L03".equals(a) && "L04".equals(b))
            || ("L04".equals(a) && "L03".equals(b));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Map<String, Integer> buildDemand(List<ShiftRequirement> requirements) {
        if (requirements == null) return Map.of();
        Map<String, Integer> demand = new HashMap<>();
        for (ShiftRequirement r : requirements) {
            String key = r.getWorkDate() + "|" + r.getShiftType().getId();
            demand.merge(key, r.getRequiredStaffCount(), Integer::sum);
        }
        return demand;
    }

    private List<Map.Entry<Integer, Map.Entry<LocalDate, String>>> flattenState(
            Map<Integer, Map<LocalDate, String>> state) {
        List<Map.Entry<Integer, Map.Entry<LocalDate, String>>> result = new ArrayList<>();
        for (var staffEntry : state.entrySet()) {
            for (var dateEntry : staffEntry.getValue().entrySet()) {
                if ("OFF".equals(dateEntry.getValue())) continue;
                result.add(Map.entry(staffEntry.getKey(), Map.entry(dateEntry.getKey(), dateEntry.getValue())));
            }
        }
        return result;
    }

    private Map<Integer, Map<LocalDate, String>> deepCopy(Map<Integer, Map<LocalDate, String>> original) {
        Map<Integer, Map<LocalDate, String>> copy = new HashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    private List<Change> buildChanges(
            List<Schedule> schedules,
            Map<Integer, Map<LocalDate, String>> bestState,
            Map<String, ShiftType> typeMap) {

        List<Change> changes = new ArrayList<>();
        for (Schedule s : schedules) {
            if (s.getStaff() == null || s.getShiftType() == null) continue;
            String currentType = s.getShiftType().getId();
            String bestType = bestState
                    .getOrDefault(s.getStaff().getId(), Map.of())
                    .get(s.getWorkDate());
            if (bestType != null && !bestType.equals(currentType)) {
                ShiftType newType = typeMap.get(bestType);
                if (newType != null) {
                    changes.add(new Change(s, newType));
                }
            }
        }
        return changes;
    }
}
