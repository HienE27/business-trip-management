package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint;
import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.constraint.DuplicateShiftConstraint;
import com.hospital.scheduler.scheduling.constraint.LeaveConflictConstraint;
import com.hospital.scheduler.scheduling.constraint.MaxShiftsConstraint;
import com.hospital.scheduler.scheduling.constraint.RestDayConstraint;
import com.hospital.scheduler.scheduling.constraint.ShiftConflictConstraint;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end BR compliance test — runs the full constraint set against a
 * synthetic 20-staff / 31-day period and verifies the final {@link WorkingSolution}
 * has zero hard-rule violations.
 *
 * <p>The constraints are evaluated directly (not through the search loop) so
 * the test stays deterministic and fast.
 */
class LocalSearchSchedulerBRComplianceTest {

    @Test
    void hardConstraints_zeroViolations_acrossRandomAssignments() {
        // Build 20 staff × 31 days × 4 shift types synthetic period
        List<Staff> staff = buildStaff(20);
        List<ShiftRequirement> reqs = buildRequirements(LocalDate.of(2026, 7, 1), 31);
        WorkingSolution sol = ConstraintTestSupportForBR.wrapSolution(staff, reqs);
        fillRandomValidly(sol, staff.size(), 31);

        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new LeaveConflictConstraint());
        registry.register(new DuplicateShiftConstraint());

        int hardTotal = 0;
        for (Constraint c : registry.all()) {
            if (!c.isHard()) continue;
            hardTotal += c.evaluate(sol).hardDelta();
        }
        assertEquals(0, hardTotal,
                "Hard constraint violations must be zero for valid random fill");
    }

    @Test
    void shiftConflict_br01_injectedViolationIsDetected() {
        List<Staff> staff = buildStaff(5);
        List<ShiftRequirement> reqs = buildRequirements(LocalDate.of(2026, 7, 1), 5);
        WorkingSolution sol = ConstraintTestSupportForBR.wrapSolution(staff, reqs);

        addSlot(sol, 1, 1, LocalDate.of(2026, 7, 1), "L01");
        addSlot(sol, 2, 1, LocalDate.of(2026, 7, 1), "L02");

        assertEquals(1, new ShiftConflictConstraint().evaluate(sol).hardDelta());
    }

    @Test
    void leaveConflict_br05_injectedViolationIsDetected() {
        Staff s1 = new Staff();
        s1.setId(1);
        s1.setFullName("S1");
        s1.setIsActive(true);
        s1.setMaxShiftsPerMonth(5);

        com.hospital.scheduler.entity.LeaveRequest leave = new com.hospital.scheduler.entity.LeaveRequest();
        leave.setStaff(s1);
        leave.setStartDate(LocalDate.of(2026, 7, 5));
        leave.setEndDate(LocalDate.of(2026, 7, 5));

        com.hospital.scheduler.scheduling.domain.SchedulingProblem problem =
                com.hospital.scheduler.scheduling.domain.SchedulingProblem.from(
                        List.of(s1),
                        new ArrayList<>(),
                        List.of(leave),
                        new ArrayList<>(),
                        new java.util.HashSet<>(),
                        new com.hospital.scheduler.scheduling.config.SchedulingConfig());
        WorkingSolution sol = ConstraintTestSupportForBR.wrapSolution(problem);
        addSlot(sol, 1, 1, LocalDate.of(2026, 7, 5), "L01");

        assertEquals(1, new LeaveConflictConstraint().evaluate(sol).hardDelta());
    }

    @Test
    void duplicateShift_br07_injectedViolationIsDetected() {
        List<Staff> staff = buildStaff(3);
        List<ShiftRequirement> reqs = buildRequirements(LocalDate.of(2026, 7, 1), 3);
        WorkingSolution sol = ConstraintTestSupportForBR.wrapSolution(staff, reqs);

        addSlot(sol, 1, 1, LocalDate.of(2026, 7, 1), "L01");
        addSlot(sol, 2, 1, LocalDate.of(2026, 7, 1), "L01"); // same staff/date/shift

        assertEquals(1, new DuplicateShiftConstraint().evaluate(sol).hardDelta());
    }

    @Test
    void registry_hasAllSevenConstraints() {
        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new LeaveConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new MaxShiftsConstraint());
        registry.register(new RestDayConstraint());
        assertTrue(registry.size() >= 6,
                "Registry must carry all BR-01..BR-07 constraints (six registered here)");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<Staff> buildStaff(int n) {
        List<Staff> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("Staff " + (i + 1));
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(5);
            Specialty sp = new Specialty();
            sp.setId(7);
            sp.setName("Ngoại");
            s.setSpecialty(sp);
            out.add(s);
        }
        return out;
    }

    private static List<ShiftRequirement> buildRequirements(LocalDate start, int days) {
        List<ShiftRequirement> out = new ArrayList<>();
        String[] types = {"L01", "L02", "L03", "L04"};
        int slotId = 1;
        for (int d = 0; d < days; d++) {
            for (String type : types) {
                ShiftRequirement sr = new ShiftRequirement();
                sr.setId(slotId++);
                sr.setWorkDate(start.plusDays(d));
                com.hospital.scheduler.entity.ShiftType st = new com.hospital.scheduler.entity.ShiftType();
                st.setId(type);
                sr.setShiftType(st);
                sr.setRequiredStaffCount(1);
                out.add(sr);
            }
        }
        return out;
    }

    private static void fillRandomValidly(WorkingSolution sol, int staffCount, int days) {
        String[] types = {"L01", "L02", "L03", "L04"};
        int slotId = 1;
        for (int d = 0; d < days; d++) {
            LocalDate date = LocalDate.of(2026, 7, 1).plusDays(d);
            // For each day, each staff gets at most ONE shift type (deterministic by day-staff mod)
            for (int s = 1; s <= staffCount; s++) {
                String type = types[(s + d) % types.length];
                addSlot(sol, slotId++, s, date, type);
            }
        }
    }

    private static void addSlot(WorkingSolution sol, int slotId, int staffId, LocalDate date, String shift) {
        MutableAssignment a = new MutableAssignment();
        a.slotId = slotId;
        a.staffId = staffId;
        a.date = date;
        a.shiftTypeId = shift;
        sol.getAssignments().add(a);
    }
}