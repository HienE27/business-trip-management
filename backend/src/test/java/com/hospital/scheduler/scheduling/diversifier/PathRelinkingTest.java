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

/** Tests for {@link PathRelinking}. */
class PathRelinkingTest {

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
        return sol;
    }

    private WorkingSolution sourceSol() {
        WorkingSolution s = makeSol();
        s.assign(1, 1);
        s.assign(2, 2);
        return s;
    }

    private WorkingSolution targetSol() {
        WorkingSolution s = makeSol();
        s.assign(1, 2);
        s.assign(2, 1);
        return s;
    }

    private double matchTargetScore(WorkingSolution s, WorkingSolution target) {
        int score = 0;
        for (var a : s.getAssignments()) {
            if (a.staffId > 0 && a.staffId == target.getAssignedStaff(a.slotId)) {
                score++;
            }
        }
        return score;
    }

    @Test
    void returnsNullWhenBothNull() {
        PathRelinking pr = new PathRelinking(10);
        assertNull(pr.relink(null, null, sol -> 0));
    }

    @Test
    void returnsTargetWhenSourceNull() {
        PathRelinking pr = new PathRelinking(10);
        WorkingSolution target = targetSol();
        assertSame(target, pr.relink(null, target, sol -> 0));
    }

    @Test
    void returnsSourceWhenTargetNull() {
        PathRelinking pr = new PathRelinking(10);
        WorkingSolution source = sourceSol();
        assertSame(source, pr.relink(source, null, sol -> 0));
    }

    @Test
    void returnsSourceWhenIdentical() {
        PathRelinking pr = new PathRelinking(10);
        WorkingSolution sol = sourceSol();
        assertSame(sol, pr.relink(sol, sol, x -> 0));
    }

    @Test
    void relinksTowardTarget() {
        PathRelinking pr = new PathRelinking(5);
        WorkingSolution source = sourceSol();
        WorkingSolution target = targetSol();
        WorkingSolution result = pr.relink(source, target, sol -> matchTargetScore(sol, target));
        assertNotNull(result);
        // Both slots should match target after relinking
        assertEquals(2, result.getAssignedStaff(1));
        assertEquals(1, result.getAssignedStaff(2));
    }

    @Test
    void maxStepsCapsTrajectory() {
        PathRelinking pr = new PathRelinking(1);
        WorkingSolution source = sourceSol();
        WorkingSolution target = targetSol();
        WorkingSolution result = pr.relink(source, target, sol -> matchTargetScore(sol, target));
        assertNotNull(result);
        // With maxSteps=1, only 1 slot matches
        int matches = 0;
        if (result.getAssignedStaff(1) == 2) matches++;
        if (result.getAssignedStaff(2) == 1) matches++;
        assertEquals(1, matches, "only 1 move applied due to maxSteps=1");
    }
}
