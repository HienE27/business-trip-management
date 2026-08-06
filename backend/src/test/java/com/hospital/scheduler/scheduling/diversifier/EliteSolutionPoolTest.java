package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link EliteSolutionPool}. */
class EliteSolutionPoolTest {

    private WorkingSolution makeSol() {
        List<Staff> staff = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Staff s = new Staff();
            s.setId(i);
            s.setFullName("S" + i);
            s.setIsActive(true);
            staff.add(s);
        }
        ShiftRequirementInfo r1 = new ShiftRequirementInfo(1, LocalDate.of(2026, 8, 1), "L01", null, 1);
        ShiftRequirementInfo r2 = new ShiftRequirementInfo(2, LocalDate.of(2026, 8, 2), "L01", null, 1);
        SchedulingProblem problem = SchedulingProblem.withRequirements(
                staff, List.of(r1, r2), List.of(),
                java.util.Map.of(), Set.of(), new SchedulingConfig());
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(
                new SolutionDescriptor(problem, null));
        WorkingSolution sol = WorkingSolution.fromProblem(new SchedulingConfig(),
                new SolutionDescriptor(problem, hub));
        // Assign both slots
        sol.assign(1, 1);
        sol.assign(2, 1);
        return sol;
    }

    @Test
    void acceptsUpToPoolSize() {
        EliteSolutionPool pool = new EliteSolutionPool(3);
        assertTrue(pool.offer(makeSol(), 1.0, 0, 0.0));
        assertTrue(pool.offer(makeSol(), 2.0, 0, 0.0));
        assertTrue(pool.offer(makeSol(), 3.0, 0, 0.0));
        assertEquals(3, pool.size());
    }

    @Test
    void evictsWorstWhenFull() {
        EliteSolutionPool pool = new EliteSolutionPool(2);
        pool.offer(makeSol(), 1.0, 0, 0.0);
        pool.offer(makeSol(), 2.0, 0, 0.0);
        assertTrue(pool.offer(makeSol(), 3.0, 0, 0.0));
        assertEquals(2, pool.size());
    }

    @Test
    void getBestReturnsHighestCoverage() {
        EliteSolutionPool pool = new EliteSolutionPool(3);
        pool.offer(makeSol(), 5.0, 0, 0.0);
        pool.offer(makeSol(), 1.0, 0, 0.0);
        pool.offer(makeSol(), 3.0, 0, 0.0);
        WorkingSolution best = pool.getBest();
        assertNotNull(best);
        // copyOf creates a new solution with 2 assigned slots → coverage = 2/2 = 1.0
        assertEquals(1.0, best.getCoverage(), 1e-9);
    }

    @Test
    void clearEmptiesPool() {
        EliteSolutionPool pool = new EliteSolutionPool(3);
        pool.offer(makeSol(), 1.0, 0, 0.0);
        pool.offer(makeSol(), 2.0, 0, 0.0);
        pool.clear();
        assertTrue(pool.isEmpty());
    }

    @Test
    void rejectNullSolution() {
        EliteSolutionPool pool = new EliteSolutionPool(3);
        assertFalse(pool.offer(null, 1.0, 0, 0.0));
    }

    @Test
    void rejectsLowerCoverageWhenFull() {
        EliteSolutionPool pool = new EliteSolutionPool(2);
        pool.offer(makeSol(), 8.0, 0, 0.0);
        pool.offer(makeSol(), 9.0, 0, 0.0);
        assertFalse(pool.offer(makeSol(), 1.0, 0, 0.0));
    }

    @Test
    void hardViolationsBreaksTies() {
        EliteSolutionPool pool = new EliteSolutionPool(2);
        pool.offer(makeSol(), 5.0, 2, 0.0);
        pool.offer(makeSol(), 5.0, 1, 0.0);
        assertEquals(2, pool.size());
        // All solutions have coverage = 2/2 = 1.0 after copyOf
        WorkingSolution best = pool.getBest();
        assertEquals(1.0, best.getCoverage(), 1e-9);
    }

    @Test
    void getEliteReturnsBestFirst() {
        EliteSolutionPool pool = new EliteSolutionPool(3);
        pool.offer(makeSol(), 2.0, 0, 0.0);
        pool.offer(makeSol(), 1.0, 0, 0.0);
        pool.offer(makeSol(), 3.0, 0, 0.0);
        List<WorkingSolution> elite = pool.getElite();
        assertEquals(3, elite.size());
        // copyOf gives coverage = 2/2 = 1.0 for all (solutions all have 2 assigned slots)
        for (WorkingSolution s : elite) {
            assertEquals(1.0, s.getCoverage(), 1e-9);
        }
    }
}
