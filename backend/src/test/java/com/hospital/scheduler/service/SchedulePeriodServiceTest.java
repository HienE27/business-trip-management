package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.StaffRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchedulePeriodService Tests - Quản lý kỳ lịch")
class SchedulePeriodServiceTest {

    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private ShiftRequirementRepository shiftRequirementRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private AuthContextService authContextService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private CacheEvictor cacheEvictor;

    @InjectMocks
    private SchedulePeriodService periodService;

    private Staff adminStaff;
    private SchedulePeriod draftPeriod;
    private SchedulePeriod publishedPeriod;

    @BeforeEach
    void setUp() {
        adminStaff = Staff.builder()
                .id(1).username("admin").fullName("Admin User").isActive(true).build();

        draftPeriod = SchedulePeriod.builder()
                .id(1)
                .periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .generatedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        publishedPeriod = SchedulePeriod.builder()
                .id(2)
                .periodName("Tháng 5/2026")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 31))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== getAllPeriods ====================
    @Nested
    @DisplayName("getAllPeriods - Lấy tất cả kỳ lịch")
    class GetAllPeriods {

        @Test
        @DisplayName("Có dữ liệu -> trả về danh sách")
        void hasData_shouldReturnList() {
            when(periodRepository.findAll()).thenReturn(List.of(draftPeriod, publishedPeriod));

            List<SchedulePeriodResponse> result = periodService.getAllPeriods();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Không có dữ liệu -> trả về danh sách rỗng")
        void noData_shouldReturnEmptyList() {
            when(periodRepository.findAll()).thenReturn(Collections.emptyList());

            List<SchedulePeriodResponse> result = periodService.getAllPeriods();

            assertThat(result).isEmpty();
        }
    }

    // ==================== getPeriodsByStatus ====================
    @Nested
    @DisplayName("getPeriodsByStatus - Lấy theo trạng thái")
    class GetByStatus {

        @Test
        @DisplayName("Lọc DRAFT -> chỉ trả về DRAFT")
        void filterDraft_shouldReturnOnlyDraft() {
            when(periodRepository.findByStatusOrderByStartDateDesc(SchedulePeriod.PeriodStatus.DRAFT))
                    .thenReturn(List.of(draftPeriod));

            List<SchedulePeriodResponse> result =
                    periodService.getPeriodsByStatus(SchedulePeriod.PeriodStatus.DRAFT);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Lọc PUBLISHED -> chỉ trả về PUBLISHED")
        void filterPublished_shouldReturnOnlyPublished() {
            when(periodRepository.findByStatusOrderByStartDateDesc(SchedulePeriod.PeriodStatus.PUBLISHED))
                    .thenReturn(List.of(publishedPeriod));

            List<SchedulePeriodResponse> result =
                    periodService.getPeriodsByStatus(SchedulePeriod.PeriodStatus.PUBLISHED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("PUBLISHED");
        }
    }

    // ==================== getPeriodById ====================
    @Nested
    @DisplayName("getPeriodById - Lấy theo ID")
    class GetById {

        @Test
        @DisplayName("Tồn tại -> trả về response")
        void exists_shouldReturnResponse() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));

            SchedulePeriodResponse result = periodService.getPeriodById(1);

            assertThat(result.getId()).isEqualTo(1);
            assertThat(result.getPeriodName()).isEqualTo("Tháng 6/2026");
        }

        @Test
        @DisplayName("Không tồn tại -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(periodRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> periodService.getPeriodById(999))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy kỳ lịch với ID: 999");
        }
    }

    // ==================== createPeriod ====================
    @Nested
    @DisplayName("createPeriod - Tạo kỳ lịch mới")
    class CreatePeriod {

        @Test
        @DisplayName("Hợp lệ -> tạo thành công với status DRAFT")
        void validRequest_shouldCreateWithDraftStatus() {
            SchedulePeriodRequest request = SchedulePeriodRequest.builder()
                    .periodName("Tháng 7/2026")
                    .startDate(LocalDate.of(2026, 7, 1))
                    .endDate(LocalDate.of(2026, 7, 31))
                    .build();
            when(periodRepository.save(any(SchedulePeriod.class)))
                    .thenAnswer(inv -> {
                        SchedulePeriod p = inv.getArgument(0);
                        p.setId(10);
                        p.setCreatedAt(LocalDateTime.now());
                        p.setUpdatedAt(LocalDateTime.now());
                        return p;
                    });

            SchedulePeriodResponse result = periodService.createPeriod(request, 1);

            assertThat(result.getId()).isEqualTo(10);
            assertThat(result.getStatus()).isEqualTo("DRAFT");
            assertThat(result.getPeriodName()).isEqualTo("Tháng 7/2026");
            verify(auditHistoryService).logAction(
                    eq("schedule_period"), eq(10), eq(AuditHistory.ActionType.INSERT), isNull(), any(), isNull());
        }

        @Test
        @DisplayName("startDate > endDate -> throw BadRequestException")
        void startDateAfterEndDate_shouldThrow() {
            SchedulePeriodRequest request = SchedulePeriodRequest.builder()
                    .periodName("Tháng 7/2026")
                    .startDate(LocalDate.of(2026, 7, 31))
                    .endDate(LocalDate.of(2026, 7, 1))
                    .build();

            assertThatThrownBy(() -> periodService.createPeriod(request, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ngày bắt đầu phải trước ngày kết thúc");
        }
    }

    // ==================== updatePeriod ====================
    @Nested
    @DisplayName("updatePeriod - Cập nhật kỳ lịch")
    class UpdatePeriod {

        @Test
        @DisplayName("DRAFT -> cập nhật thành công")
        void draft_shouldUpdate() {
            SchedulePeriodRequest request = SchedulePeriodRequest.builder()
                    .periodName("Tháng 6/2026 - Updated")
                    .startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 30))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(periodRepository.save(any(SchedulePeriod.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SchedulePeriodResponse result = periodService.updatePeriod(1, request);

            assertThat(result.getPeriodName()).isEqualTo("Tháng 6/2026 - Updated");
            verify(auditHistoryService).logAction(
                    eq("schedule_period"), eq(1), eq(AuditHistory.ActionType.UPDATE), any(), any(), isNull());
        }

        @Test
        @DisplayName("PUBLISHED -> throw BadRequestException")
        void published_shouldThrow() {
            SchedulePeriodRequest request = SchedulePeriodRequest.builder()
                    .periodName("Tháng 5/2026 Updated")
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 5, 31))
                    .build();
            when(periodRepository.findById(2)).thenReturn(Optional.of(publishedPeriod));

            assertThatThrownBy(() -> periodService.updatePeriod(2, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("startDate > endDate -> throw BadRequestException")
        void startDateAfterEndDate_shouldThrow() {
            SchedulePeriodRequest request = SchedulePeriodRequest.builder()
                    .periodName("Tháng 6/2026")
                    .startDate(LocalDate.of(2026, 6, 30))
                    .endDate(LocalDate.of(2026, 6, 1))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));

            assertThatThrownBy(() -> periodService.updatePeriod(1, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ngày bắt đầu phải trước ngày kết thúc");
        }
    }

    // ==================== publishPeriod ====================
    @Nested
    @DisplayName("publishPeriod - Công bố kỳ lịch")
    class PublishPeriod {

        @Test
        @DisplayName("DRAFT, không conflict -> PUBLISHED + gửi thông báo cho staff")
        void draftWithoutConflicts_shouldPublish() {
            when(periodRepository.findByIdWithLock(1)).thenReturn(Optional.of(draftPeriod));
            when(conflictDetectionService.checkPeriodConflicts(1))
                    .thenReturn(ConflictCheckResponse.builder().hasConflicts(false).conflicts(List.of()).build());
            when(periodRepository.save(any(SchedulePeriod.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(scheduleRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
            when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(adminStaff));

            SchedulePeriodResponse result = periodService.publishPeriod(1, 1);

            assertThat(result.getStatus()).isEqualTo("PUBLISHED");
            // The publish flow now sends notifications via batch path, not per-staff.
            verify(notificationService).createNotificationBatch(any());
            verify(emailService).sendSchedulePublishedEmail(
                    eq(List.of(adminStaff)), anyString(), any(), any(), anyList(), anyList());
        }

        @Test
        @DisplayName("PUBLISHED -> throw BadRequestException")
        void alreadyPublished_shouldThrow() {
            when(periodRepository.findByIdWithLock(2)).thenReturn(Optional.of(publishedPeriod));

            assertThatThrownBy(() -> periodService.publishPeriod(2, 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("Có xung đột -> throw BadRequestException")
        void hasConflicts_shouldThrow() {
            when(periodRepository.findByIdWithLock(1)).thenReturn(Optional.of(draftPeriod));
            when(conflictDetectionService.checkPeriodConflicts(1))
                    .thenReturn(ConflictCheckResponse.builder().hasConflicts(true)
                            .conflicts(List.of(ConflictCheckResponse.ConflictDetail.builder()
                                    .staffName("BS. Test").workDate(LocalDate.of(2026, 1, 15))
                                    .conflictReasons(List.of("Trùng với lịch thông tầm")).build()))
                            .build());

            assertThatThrownBy(() -> periodService.publishPeriod(1, 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("có xung đột");
        }
    }

    // ==================== archivePeriod ====================
    @Nested
    @DisplayName("archivePeriod - Lưu trữ kỳ lịch")
    class ArchivePeriod {

        @Test
        @DisplayName("PUBLISHED -> ARCHIVED")
        void published_shouldArchive() {
            when(periodRepository.findById(2)).thenReturn(Optional.of(publishedPeriod));
            when(periodRepository.save(any(SchedulePeriod.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SchedulePeriodResponse result = periodService.archivePeriod(2);

            assertThat(result.getStatus()).isEqualTo("ARCHIVED");
        }

        @Test
        @DisplayName("DRAFT -> throw BadRequestException")
        void draft_shouldThrow() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));

            assertThatThrownBy(() -> periodService.archivePeriod(1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PUBLISHED");
        }
    }

    // ==================== deletePeriod ====================
    @Nested
    @DisplayName("deletePeriod - Xóa kỳ lịch")
    class DeletePeriod {

        @Test
        @DisplayName("DRAFT -> xóa thành công")
        void draft_shouldDelete() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(shiftRequirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
            doNothing().when(periodRepository).delete(draftPeriod);

            periodService.deletePeriod(1);

            verify(periodRepository).delete(draftPeriod);
            verify(auditHistoryService).logAction(
                    eq("schedule_period"), eq(1), eq(AuditHistory.ActionType.DELETE), any(), isNull(), isNull());
        }

        @Test
        @DisplayName("PUBLISHED -> throw BadRequestException")
        void published_shouldThrow() {
            when(periodRepository.findById(2)).thenReturn(Optional.of(publishedPeriod));

            assertThatThrownBy(() -> periodService.deletePeriod(2))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }
    }
}
