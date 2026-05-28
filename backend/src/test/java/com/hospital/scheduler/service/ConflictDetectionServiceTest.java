package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleConflictRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private CompensationDayRepository compensationDayRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleConflictRepository scheduleConflictRepository;

    @InjectMocks
    private ConflictDetectionService conflictDetectionService;

    @Test
    void detectsL01AndL02SameDayConflict() {
        LocalDate workDate = LocalDate.of(2026, 5, 29);
        Schedule existingDuty = Schedule.builder()
                .id(10)
                .shiftType(ShiftType.builder().id("L01").name("Lịch trực 24/24").build())
                .build();

        when(leaveRequestRepository.findByStaffIdAndDateRange(1, workDate, workDate)).thenReturn(List.of());
        when(compensationDayRepository.findByStaffIdAndCompensationDate(1, workDate)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStaffIdAndWorkDate(1, workDate)).thenReturn(List.of(existingDuty));

        List<String> conflicts = conflictDetectionService.detectAllConflicts(1, workDate, "L02", null);

        assertThat(conflicts).containsExactly("Trùng với lịch trực 24/24 (L01) trong ngày này");
    }

    @Test
    void detectsL03AndL04SameDayConflict() {
        LocalDate workDate = LocalDate.of(2026, 5, 30);
        Schedule existingServiceClinic = Schedule.builder()
                .id(11)
                .shiftType(ShiftType.builder().id("L03").name("Lịch phòng khám dịch vụ").build())
                .build();

        when(leaveRequestRepository.findByStaffIdAndDateRange(2, workDate, workDate)).thenReturn(List.of());
        when(compensationDayRepository.findByStaffIdAndCompensationDate(2, workDate)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStaffIdAndWorkDate(2, workDate)).thenReturn(List.of(existingServiceClinic));

        List<String> conflicts = conflictDetectionService.detectAllConflicts(2, workDate, "L04", null);

        assertThat(conflicts).containsExactly("Trùng với lịch phòng khám dịch vụ (L03) trong ngày này");
    }

    @Test
    void blocksSchedulingOnApprovedLeaveAndCompensationDay() {
        LocalDate workDate = LocalDate.of(2026, 6, 2);
        LeaveRequest approvedLeave = LeaveRequest.builder()
                .status(LeaveRequest.LeaveStatus.APPROVED)
                .build();

        when(leaveRequestRepository.findByStaffIdAndDateRange(3, workDate, workDate)).thenReturn(List.of(approvedLeave));
        when(compensationDayRepository.findByStaffIdAndCompensationDate(3, workDate))
                .thenReturn(Optional.of(CompensationDay.builder().compensationDate(workDate).build()));
        when(scheduleRepository.findByStaffIdAndWorkDate(3, workDate)).thenReturn(List.of());

        List<String> conflicts = conflictDetectionService.detectAllConflicts(3, workDate, "L03", null);

        assertThat(conflicts)
                .contains("Nhân sự có ngày nghỉ phép được duyệt trong ngày này")
                .contains("Ngày này là ngày nghỉ bù của nhân sự");
    }

    @Test
    void validateAndThrowRaisesConflictExceptionWhenAnyRuleFails() {
        LocalDate workDate = LocalDate.of(2026, 5, 31);
        Schedule existingExpertClinic = Schedule.builder()
                .id(12)
                .shiftType(ShiftType.builder().id("L04").name("Lịch phòng khám chuyên gia").build())
                .build();

        when(leaveRequestRepository.findByStaffIdAndDateRange(4, workDate, workDate)).thenReturn(List.of());
        when(compensationDayRepository.findByStaffIdAndCompensationDate(4, workDate)).thenReturn(Optional.empty());
        when(scheduleRepository.findByStaffIdAndWorkDate(4, workDate)).thenReturn(List.of(existingExpertClinic));

        assertThatThrownBy(() -> conflictDetectionService.validateAndThrow(4, workDate, "L03", null))
                .hasMessageContaining("Phát hiện xung đột")
                .hasMessageContaining("Trùng với lịch phòng khám chuyên gia");
    }
}
