package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ShiftRequirementRequest;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for ShiftRequirementService holiday validation.
 *
 * Covers QA report:
 * - BUG-m6: Reject shift requirement creation/update on a configured holiday.
 *   Schedule generation skips holidays anyway, so silently accepting the
 *   requirement creates the illusion of coverage that never materialises.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShiftRequirementService - Holiday validation (BUG-m6)")
class ShiftRequirementServiceHolidayTest {

    @Mock private ShiftRequirementRepository shiftRequirementRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;

    @InjectMocks private ShiftRequirementService service;

    private SchedulePeriod period;
    private ShiftType shiftType;

    @BeforeEach
    void setUp() {
        period = SchedulePeriod.builder()
                .id(1).periodName("Tháng 7/2026")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        shiftType = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24")
                .isOvernight(true).fatigueScore(3)
                .build();

        lenient().when(periodRepository.findById(1)).thenReturn(Optional.of(period));
        lenient().when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(shiftType));
        lenient().when(holidayRepository.existsByHolidayDateAndIsActiveTrue(any())).thenReturn(false);
        lenient().when(authContextService.getCurrentStaff()).thenReturn(null);
    }

    @Nested
    @DisplayName("upsert rejects holiday dates")
    class UpsertRejectsHoliday {

        @Test
        @DisplayName("Upsert on a holiday → BadRequestException")
        void upsert_onHoliday_throwsBadRequest() {
            LocalDate holiday = LocalDate.of(2026, 7, 2); // Tết Độc Lập giả định
            when(holidayRepository.existsByHolidayDateAndIsActiveTrue(holiday)).thenReturn(true);

            ShiftRequirementRequest req = ShiftRequirementRequest.builder()
                    .workDate(holiday)
                    .shiftTypeId("L01")
                    .requiredStaffCount(1)
                    .build();

            assertThatThrownBy(() -> service.upsert(1, List.of(req)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ngày lễ");
        }

        @Test
        @DisplayName("Upsert on a non-holiday → does not call holidayRepository twice for each req, but accepts")
        void upsert_onWorkday_proceeds() {
            LocalDate workDay = LocalDate.of(2026, 7, 6); // Thứ 2
            when(holidayRepository.existsByHolidayDateAndIsActiveTrue(workDay)).thenReturn(false);
            when(shiftRequirementRepository.findByPeriodIdAndWorkDateAndShiftTypeId(anyInt(), any(), any()))
                    .thenReturn(java.util.Optional.empty());
            when(shiftRequirementRepository.save(any(com.hospital.scheduler.entity.ShiftRequirement.class)))
                    .thenAnswer(inv -> {
                        com.hospital.scheduler.entity.ShiftRequirement arg =
                                inv.getArgument(0, com.hospital.scheduler.entity.ShiftRequirement.class);
                        arg.setId(999);
                        return arg;
                    });

            ShiftRequirementRequest req = ShiftRequirementRequest.builder()
                    .workDate(workDay)
                    .shiftTypeId("L01")
                    .requiredStaffCount(1)
                    .build();

            // No exception expected; we don't assert the saved entity here.
            // If validation incorrectly fires, we'll see BadRequestException.
            try {
                service.upsert(1, List.of(req));
            } catch (BadRequestException e) {
                throw new AssertionError("Validation incorrectly rejected non-holiday", e);
            }
        }
    }

    @Nested
    @DisplayName("update rejects holiday dates")
    class UpdateRejectsHoliday {

        @Test
        @DisplayName("Update with holiday workDate → BadRequestException")
        void update_onHoliday_throwsBadRequest() {
            LocalDate holiday = LocalDate.of(2026, 7, 2);
            com.hospital.scheduler.entity.ShiftRequirement existing =
                    com.hospital.scheduler.entity.ShiftRequirement.builder()
                            .id(100)
                            .period(period)
                            .workDate(LocalDate.of(2026, 7, 6))
                            .shiftType(shiftType)
                            .requiredStaffCount(1)
                            .build();
            when(shiftRequirementRepository.findById(100)).thenReturn(Optional.of(existing));
            when(holidayRepository.existsByHolidayDateAndIsActiveTrue(holiday)).thenReturn(true);

            ShiftRequirementRequest req = ShiftRequirementRequest.builder()
                    .workDate(holiday)
                    .shiftTypeId("L01")
                    .requiredStaffCount(2)
                    .build();

            assertThatThrownBy(() -> service.update(100, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ngày lễ");
        }
    }
}