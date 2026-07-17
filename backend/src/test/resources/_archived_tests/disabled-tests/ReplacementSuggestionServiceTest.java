package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReplacementSuggestionService}.
 *
 * <p>Covers the shared delegation path used by both the auto-scheduling
 * wizard (M07-F08) and the leave-approval flow
 * (LeaveRequestService.findReplacementsForLeave). Tests here are the
 * regression net for SERVICE_AUDIT.md P1 — any behaviour change to
 * {@code suggestTopCandidates} should surface here first.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReplacementSuggestionService - Shared replacement-suggestion path")
@Disabled("Archived: the folder name 'disabled-tests' does not match a valid Java package, " +
        "so Surefire cannot load the class without the rename below. Re-enable when " +
        "the M07-F08 replacement-suggestion path is wired back into the runtime scheduler.")
class ReplacementSuggestionServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @InjectMocks private ReplacementSuggestionService service;

    private Schedule scheduleOnMonday() {
        SchedulePeriod period = new SchedulePeriod();
        period.setId(10);
        SchedulePeriod periodRef = period;
        ShiftType type = new ShiftType();
        type.setId("L01");
        type.setName("Trực 24/24");
        Specialty spec = new Specialty();
        spec.setId(1);
        spec.setName("Nội khoa");
        Staff original = new Staff();
        original.setId(100);
        original.setFullName("Nguyễn Văn A");
        original.setSpecialty(spec);
        Schedule s = new Schedule();
        s.setId(555);
        s.setPeriod(periodRef);
        s.setWorkDate(LocalDate.of(2026, 7, 6));  // Monday
        s.setShiftType(type);
        s.setStaff(original);
        return s;
    }

    private Staff staffWithSpecialty(int id, String name, int specialtyId) {
        Specialty sp = new Specialty();
        sp.setId(specialtyId);
        sp.setName("Spec-" + specialtyId);
        Staff staff = new Staff();
        staff.setId(id);
        staff.setFullName(name);
        staff.setSpecialty(sp);
        return staff;
    }

    @Test
    @DisplayName("suggestTopCandidates - delegates to ConflictDetectionService.findReplacements with skipCompensationDay=true")
    void suggestTopCandidates_delegatesToBatchConflictDetection() {
        Schedule schedule = scheduleOnMonday();

        Staff alice = staffWithSpecialty(1, "Alice", 1);
        Staff bob = staffWithSpecialty(2, "Bob", 1);
        when(conflictDetectionService.findReplacements(eq(10), eq(LocalDate.of(2026, 7, 6)),
                eq("L01"), eq(100), eq(2), any(), eq(true)))
                .thenReturn(List.of(alice, bob));
        when(scheduleRepository.countByStaffIdAndPeriodId(1, 10)).thenReturn(4L);
        when(scheduleRepository.countByStaffIdAndPeriodId(2, 10)).thenReturn(7L);

        List<ReplacementSuggestionService.CandidateWithWorkload> result =
                service.suggestTopCandidates(schedule, 2, Set.of(100));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fullName()).isEqualTo("Alice");
        assertThat(result.get(0).currentShiftCount()).isEqualTo(4);
        assertThat(result.get(1).currentShiftCount()).isEqualTo(7);

        // Verify skipCompensationDay=true is passed (comp day only blocks auto-scheduling, not human reassignment)
        verify(conflictDetectionService, times(1))
                .findReplacements(eq(10), eq(LocalDate.of(2026, 7, 6)),
                        eq("L01"), eq(100), eq(2), any(), eq(true));
    }

    @Test
    @DisplayName("suggestTopCandidates - returns empty list when limit <= 0")
    void suggestTopCandidates_zeroLimitReturnsEmptyWithoutCallingRepo() {
        Schedule schedule = scheduleOnMonday();

        List<ReplacementSuggestionService.CandidateWithWorkload> result =
                service.suggestTopCandidates(schedule, 0, Set.of());

        assertThat(result).isEmpty();
        verify(conflictDetectionService, never()).findReplacements(any(), any(), any(), any(), anyInt(), any(), anyBoolean());
        verify(staffRepository, never()).findByIsActiveTrue();
    }

    @Test
    @DisplayName("suggestTopCandidates - periodId null means workload defaults to 0")
    void suggestTopCandidates_nullPeriodIdResultsZeroWorkload() {
        Schedule schedule = scheduleOnMonday();
        schedule.setPeriod(null);  // detached entity — no period

        Staff carol = staffWithSpecialty(3, "Carol", 1);
        when(conflictDetectionService.findReplacements(isNull(), eq(LocalDate.of(2026, 7, 6)),
                eq("L01"), eq(100), eq(1), any(), eq(true)))
                .thenReturn(List.of(carol));

        List<ReplacementSuggestionService.CandidateWithWorkload> result =
                service.suggestTopCandidates(schedule, 1, Set.of(100));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentShiftCount()).isZero();
    }

    @Test
    @DisplayName("suggestTopCandidates - rejected null schedule")
    void suggestTopCandidates_nullScheduleThrows() {
        assertThatThrownBy(() -> service.suggestTopCandidates(null, 3, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schedule");
    }

    @Test
    @DisplayName("suggestReplacements (legacy Map API) - throws when scheduleId not found")
    void suggestReplacements_unknownScheduleIdThrows() {
        when(scheduleRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggestReplacements(999, Set.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("suggestReplacements (legacy Map API) - returns sorted list with availableCount and totalCandidates")
    void suggestReplacements_classifiesAvailableVsConflict() {
        Schedule schedule = scheduleOnMonday();
        when(scheduleRepository.findById(555)).thenReturn(Optional.of(schedule));

        Staff available1 = staffWithSpecialty(1, "FreeStaff1", 1);
        Staff available2 = staffWithSpecialty(2, "FreeStaff2", 1);
        Staff busy = staffWithSpecialty(3, "BusyStaff", 1);
        when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(available1, available2, busy));

        when(conflictDetectionService.detectAllConflicts(eq(1), any(), any(), eq(555), eq(false)))
                .thenReturn(List.of());  // no conflicts
        when(conflictDetectionService.detectAllConflicts(eq(2), any(), any(), eq(555), eq(false)))
                .thenReturn(List.of());  // no conflicts
        when(conflictDetectionService.detectAllConflicts(eq(3), any(), any(), eq(555), eq(false)))
                .thenReturn(List.of("Đã có L01"));  // has conflict

        when(scheduleRepository.countByStaffIdAndPeriodId(any(), eq(10))).thenReturn(2L);

        Map<String, Object> result = service.suggestReplacements(555, Set.of(100));

        assertThat(result.get("originalScheduleId")).isEqualTo(555);
        assertThat(result.get("totalCandidates")).isEqualTo(3);
        assertThat(result.get("availableCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.get(0).get("isAvailable")).isEqualTo(true);
        assertThat(suggestions.get(0).get("staffId")).isEqualTo(1);
        assertThat(suggestions.get(1).get("isAvailable")).isEqualTo(true);
        assertThat(suggestions.get(2).get("isAvailable")).isEqualTo(false);
        assertThat(suggestions.get(2).get("reason")).asString().contains("L01");
        // Original staff should never appear in the candidates list
        assertThat(suggestions).noneMatch(m -> Objects.equals(m.get("staffId"), 100));
    }
}
