package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.scheduling.config.ConfigDefaults;
import com.hospital.scheduler.scheduling.config.ConfigService;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUGFIX (M08-EXPAND-V10): verifies the V10 search expands each requirement
 * into {@code requiredCount} slots (1 slot = 1 staff) and that the greedy
 * initial solution never creates hard violations (L01↔L02 same day, duplicate).
 */
class LocalSearchSchedulerExpandTest {

    private SchedulingConfig smallConfig() {
        SchedulingConfig c = new SchedulingConfig();
        c.getSearch().setMaxIterations(20);
        c.getSearch().setMaxNoImprove(10);
        return c;
    }

    private LocalSearchScheduler buildScheduler(SchedulingConfig config) {
        HolidayRepository holidays = mock(HolidayRepository.class);
        when(holidays.findAll()).thenReturn(List.of());
        CompensationDateCalculator compCalc = mock(CompensationDateCalculator.class);
        AlgorithmConfigService algoCfg = mock(AlgorithmConfigService.class);
        when(algoCfg.getAutoGenConfig()).thenReturn(Optional.empty());
        ConfigService configService = mock(ConfigService.class);
        when(configService.load()).thenReturn(ConfigDefaults.withDefaults());
        return new LocalSearchScheduler(config, holidays, compCalc, algoCfg, configService);
    }

    private Staff staff(int id) {
        Staff s = new Staff();
        s.setId(id);
        s.setFullName("NV" + id);
        s.setIsActive(true);
        return s;
    }

    @Test
    void expand_requirementNeeds2Staff_produces2Schedules() {
        LocalDate day = LocalDate.of(2026, 8, 3);
        LocalSearchScheduler scheduler = buildScheduler(smallConfig());

        // One L01 requirement demanding 2 staff, pool of 2 free staff.
        List<ShiftRequirementInfo> reqs = List.of(
                new ShiftRequirementInfo("L01", day, 2));
        SchedulingResult result = scheduler.solve(
                List.of(staff(1), staff(2)), day, day, reqs,
                Set.of(), List.of(), Set.of());

        // Expand: 1 requirement x requiredCount=2 → 2 slots → both staff assigned.
        assertEquals(2, result.getScheduleCount(),
                "requirement needing 2 staff must expand into 2 schedules");
        assertTrue(result.isValid(), "no hard violations expected");
    }

    @Test
    void greedy_avoidsL01L02SameDayConflict_staysHardFree() {
        LocalDate day = LocalDate.of(2026, 8, 3);
        LocalSearchScheduler scheduler = buildScheduler(smallConfig());

        // Single staff pool + L01 and L02 on the SAME day: a naive greedy would
        // assign both to the same staff and create a BR-01 hard violation.
        List<ShiftRequirementInfo> reqs = List.of(
                new ShiftRequirementInfo("L01", day, 1),
                new ShiftRequirementInfo("L02", day, 1));
        SchedulingResult result = scheduler.solve(
                List.of(staff(1)), day, day, reqs,
                Set.of(), List.of(), Set.of());

        assertTrue(result.isValid(),
                "L01+L02 same day on one staff must not produce hard violations");
        assertEquals(1, result.getScheduleCount(),
                "only the first (L01) slot is assignable; L02 must stay unassigned");
    }
}
