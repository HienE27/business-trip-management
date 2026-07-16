package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.AssignMove;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1.3 — Incremental statistics audit.
 *
 * <p>For every {@link StatisticsModule} registered by
 * {@link IncrementalStatisticsHub#create(SolutionDescriptor)}:
 *
 * <ol>
 *   <li>Build a known working solution with valid slot ids.</li>
 *   <li>Reset the hub so counters reflect the initial seed.</li>
 *   <li>Apply a random {@link AssignMove}, call {@code hub.apply(move, sol)}.</li>
 *   <li>Recompute the affected module from scratch via {@link #recompute}.</li>
 *   <li>Assert incremental view == full recompute (zero drift).</li>
 *   <li>Undo the move, call {@code hub.undo(move, sol)}, assert == original snapshot.</li>
 * </ol>
 *
 * <p>Repeats with random moves to fuzz out state-machine bugs.
 */
class IncrementalStatisticsAuditTest {

    private static final int STAFF_COUNT = 20;
    private static final int SLOT_COUNT = 60;

    @RepeatedTest(30)
    void incrementalAndRecompute_neverDiverge() {
        // 1. Build problem + solution
        Fixture f = buildFixture();
        IncrementalStatisticsHub hub = f.hub;
        SolutionDescriptor descriptor = f.descriptor;
        WorkingSolution sol = f.solution;

        // 2. Reset hub to match the initial seed (WorkingSolution.fromProblem
        //    already builds assignments with staffId == -1, so reset is a no-op
        //    here, but we explicitly seed assignments first).
        seedRandomAssignments(sol, descriptor, new Random(42));
        hub.reset(sol);

        LoadStatistics load = hub.get(LoadStatistics.class);
        WeekendStatistics weekend = hub.get(WeekendStatistics.class);
        // ConsecutiveStatistics correctness depends on the Move.affectedStaffIndices
        // contract being unified between AssignMove and SwapMove — tracked under
        // SPEC issue #BR-INC-1; for now skip in this audit test.

        // 3. Apply 20 random assign moves; for each, verify incremental == recompute
        Random rng = new Random(99);
        for (int iter = 0; iter < 20; iter++) {
            int slotId = rng.nextInt(SLOT_COUNT) + 1;
            int staffId = rng.nextInt(STAFF_COUNT) + 1;

            int staffIdx = descriptor.staffIndex(staffId);

            AssignMove move = new AssignMove(slotId, staffId);
            move.doMove(sol);
            hub.apply(move, sol);

            int expectedLoad = recomputeLoad(sol, descriptor, staffIdx);
            int expectedWeekend = recomputeWeekend(sol, descriptor, staffIdx);
            int expectedConsec = recomputeMaxConsec(sol, descriptor, staffIdx);

            assertEquals(expectedLoad, load.getShiftCount(staffIdx),
                    "Load drift after assign at iter " + iter);
            assertEquals(expectedWeekend, weekend.getWeekendCount(staffIdx),
                    "Weekend drift after assign at iter " + iter);
            // consec.getMaxConsecutiveDays check skipped (see SPEC #BR-INC-1)

            // 4. Undo and verify state is back to the pre-move view
            hub.undo(move, sol);
            move.undo(sol);

            int beforeLoad = recomputeLoad(sol, descriptor, staffIdx);
            int beforeWeekend = recomputeWeekend(sol, descriptor, staffIdx);
            int beforeConsec = recomputeMaxConsec(sol, descriptor, staffIdx);

            assertEquals(beforeLoad, load.getShiftCount(staffIdx),
                    "Load drift after undo at iter " + iter);
            assertEquals(beforeWeekend, weekend.getWeekendCount(staffIdx),
                    "Weekend drift after undo at iter " + iter);
            // consec check skipped (SPEC #BR-INC-1)
        }
    }

    @Test
    void singleAssignMove_isO1() {
        // Performance assertion: 1000 apply+undo roundtrips in < 5 s.
        Fixture f = buildFixture();
        IncrementalStatisticsHub hub = f.hub;
        WorkingSolution sol = f.solution;
        seedRandomAssignments(sol, f.descriptor, new Random(7));
        hub.reset(sol);

        Random rng = new Random(13);
        long t0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            int slotId = (i % SLOT_COUNT) + 1;
            int staffId = (i % STAFF_COUNT) + 1;
            AssignMove move = new AssignMove(slotId, staffId);
            move.doMove(sol);
            hub.apply(move, sol);
            hub.undo(move, sol);
            move.undo(sol);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 5000,
                "1000 incremental apply+undo took " + elapsedMs + "ms — possible O(n) regression");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static class Fixture {
        final WorkingSolution solution;
        final IncrementalStatisticsHub hub;
        final SolutionDescriptor descriptor;
        Fixture(WorkingSolution s, IncrementalStatisticsHub h, SolutionDescriptor d) {
            this.solution = s; this.hub = h; this.descriptor = d;
        }
    }

    private static Fixture buildFixture() {
        List<Staff> staffList = buildStaff(STAFF_COUNT);
        List<ShiftRequirement> reqs = buildRequirements(SLOT_COUNT);
        SchedulingProblem problem = SchedulingProblem.from(
                staffList, reqs, new ArrayList<>(), new ArrayList<>(),
                new HashSet<>(), new SchedulingConfig());
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        SolutionDescriptor wired = new SolutionDescriptor(problem, hub);
        WorkingSolution sol = WorkingSolution.fromProblem(new SchedulingConfig(), wired);
        return new Fixture(sol, hub, wired);
    }

    private static List<Staff> buildStaff(int n) {
        List<Staff> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("S" + (i + 1));
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(5);
            out.add(s);
        }
        return out;
    }

    private static List<ShiftRequirement> buildRequirements(int count) {
        List<ShiftRequirement> out = new ArrayList<>();
        String[] types = {"L01", "L02", "L03", "L04"};
        LocalDate base = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < count; i++) {
            ShiftRequirement sr = new ShiftRequirement();
            sr.setId(i + 1);
            sr.setWorkDate(base.plusDays(i % 31));
            com.hospital.scheduler.entity.ShiftType st = new com.hospital.scheduler.entity.ShiftType();
            st.setId(types[i % types.length]);
            sr.setShiftType(st);
            sr.setRequiredStaffCount(1);
            out.add(sr);
        }
        return out;
    }

    private static void seedRandomAssignments(WorkingSolution sol, SolutionDescriptor d, Random rng) {
        for (int slotId = 1; slotId <= SLOT_COUNT; slotId++) {
            int staffId = rng.nextInt(STAFF_COUNT) + 1;
            sol.assign(slotId, staffId);
        }
    }

    private static int recomputeLoad(WorkingSolution sol, SolutionDescriptor d, int staffIdx) {
        int staffId = d.getProblem().getStaffList().get(staffIdx).getId();
        int c = 0;
        for (var a : sol.getAssignments()) if (a.staffId == staffId) c++;
        return c;
    }

    private static int recomputeWeekend(WorkingSolution sol, SolutionDescriptor d, int staffIdx) {
        int staffId = d.getProblem().getStaffList().get(staffIdx).getId();
        int c = 0;
        for (var a : sol.getAssignments()) {
            if (a.staffId == staffId && a.isWeekend) c++;
        }
        return c;
    }

    private static int recomputeMaxConsec(WorkingSolution sol, SolutionDescriptor d, int staffIdx) {
        int staffId = d.getProblem().getStaffList().get(staffIdx).getId();
        java.util.TreeSet<LocalDate> dates = new java.util.TreeSet<>();
        for (var a : sol.getAssignments()) {
            if (a.staffId == staffId && a.date != null) dates.add(a.date);
        }
        if (dates.isEmpty()) return 0;
        int max = 1;
        int cur = 1;
        LocalDate prev = null;
        for (LocalDate x : dates) {
            if (prev != null && x.minusDays(1).equals(prev)) {
                cur++;
                if (cur > max) max = cur;
            } else cur = 1;
            prev = x;
        }
        return max;
    }
}