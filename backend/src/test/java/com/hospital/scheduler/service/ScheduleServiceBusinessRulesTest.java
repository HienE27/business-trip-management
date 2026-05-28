package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.StaffRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScheduleServiceBusinessRulesTest {

    @Test
    void calculatesCompensationDateForEachDutyDayRule() throws Exception {
        ScheduleService scheduleService = new ScheduleService(
                mock(ScheduleRepository.class),
                mock(SchedulePeriodRepository.class),
                mock(StaffRepository.class),
                mock(ShiftTypeRepository.class),
                mock(ShiftRequirementRepository.class),
                mock(CompensationDayRepository.class),
                mock(LeaveRequestRepository.class),
                mock(ConflictDetectionService.class),
                new AuditHistoryService(
                        mock(AuditHistoryRepository.class),
                        mock(StaffRepository.class),
                        new ObjectMapper()
                )
        );
        Method method = ScheduleService.class.getDeclaredMethod("calculateCompensationDate", LocalDate.class);
        method.setAccessible(true);

        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 2)))
                .isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 3)))
                .isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 4)))
                .isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 5)))
                .isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 6)))
                .isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(method.invoke(scheduleService, LocalDate.of(2026, 6, 7)))
                .isEqualTo(LocalDate.of(2026, 6, 8));
    }
}
