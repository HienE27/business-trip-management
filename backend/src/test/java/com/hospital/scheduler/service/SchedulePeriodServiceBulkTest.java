package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.BulkPeriodResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchedulePeriodService bulk operations — H3/H4")
class SchedulePeriodServiceBulkTest {

    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks
    private SchedulePeriodService periodService;

    private SchedulePeriod draftPeriod;
    private SchedulePeriod publishedPeriod;
    private SchedulePeriod archivedPeriod;
    private Staff publisher;

    @BeforeEach
    void setUp() {
        draftPeriod = SchedulePeriod.builder()
                .id(1)
                .periodName("Kỳ Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        publishedPeriod = SchedulePeriod.builder()
                .id(2)
                .periodName("Kỳ Tháng 5/2026")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 31))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .build();

        archivedPeriod = SchedulePeriod.builder()
                .id(3)
                .periodName("Kỳ Tháng 4/2026")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 4, 30))
                .status(SchedulePeriod.PeriodStatus.ARCHIVED)
                .build();

        publisher = Staff.builder().id(10).username("admin").build();
    }

    // -------------------------------------------------------------------------
    // H3: bulkPublish — pre-validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("bulkPublish pre-validation (H3)")
    class BulkPublishPreValidation {

        @Test
        @DisplayName("Should return failure with DRAFT check message for non-DRAFT period")
        void bulkPublish_nonDraftPeriod_returnsDetailedFailure() {
            // Given: period 2 is PUBLISHED, period 1 is DRAFT
            when(periodRepository.findAllByIdIn(List.of(1, 2)))
                    .thenReturn(List.of(draftPeriod, publishedPeriod));

            BulkPeriodResponse response = periodService.bulkPublish(List.of(1, 2), 10);

            // Both should be in results
            assertThat(response.getResults()).hasSize(2);

            // Period 1 (DRAFT) — try to publish (may fail on conflict check but gets a result)
            var draftResult = response.getResults().stream()
                    .filter(r -> r.getId().equals(1)).findFirst().orElseThrow();
            // Period 2 (PUBLISHED) — should fail with clear message
            var publishedResult = response.getResults().stream()
                    .filter(r -> r.getId().equals(2)).findFirst().orElseThrow();

            assertThat(publishedResult.isSuccess()).isFalse();
            assertThat(publishedResult.getMessage()).contains("PUBLISHED");
        }

        @Test
        @DisplayName("Should return failure for non-existent period ID")
        void bulkPublish_nonExistent_returnsNotFound() {
            when(periodRepository.findAllByIdIn(List.of(999)))
                    .thenReturn(Collections.emptyList());

            BulkPeriodResponse response = periodService.bulkPublish(List.of(999), 10);

            assertThat(response.getResults()).hasSize(1);
            assertThat(response.getResults().get(0).isSuccess()).isFalse();
            assertThat(response.getResults().get(0).getMessage())
                    .contains("Không tìm thấy kỳ lịch");
        }

        @Test
        @DisplayName("Should return failure for ARCHIVED period")
        void bulkPublish_archived_returnsDetailedFailure() {
            when(periodRepository.findAllByIdIn(List.of(3)))
                    .thenReturn(List.of(archivedPeriod));

            BulkPeriodResponse response = periodService.bulkPublish(List.of(3), 10);

            assertThat(response.getResults()).hasSize(1);
            var result = response.getResults().get(0);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("ARCHIVED");
        }
    }

    // -------------------------------------------------------------------------
    // H4: bulkArchive — pre-validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("bulkArchive pre-validation (H4)")
    class BulkArchivePreValidation {

        @Test
        @DisplayName("Should return failure for non-PUBLISHED period")
        void bulkArchive_nonPublished_returnsDetailedFailure() {
            when(periodRepository.findAllByIdIn(List.of(1, 3)))
                    .thenReturn(List.of(draftPeriod, archivedPeriod));

            BulkPeriodResponse response = periodService.bulkArchive(List.of(1, 3));

            assertThat(response.getResults()).hasSize(2);

            var draftResult = response.getResults().stream()
                    .filter(r -> r.getId().equals(1)).findFirst().orElseThrow();
            var archivedResult = response.getResults().stream()
                    .filter(r -> r.getId().equals(3)).findFirst().orElseThrow();

            assertThat(draftResult.isSuccess()).isFalse();
            assertThat(draftResult.getMessage()).contains("PUBLISHED");

            assertThat(archivedResult.isSuccess()).isFalse();
            assertThat(archivedResult.getMessage()).contains("ARCHIVED");
        }

        @Test
        @DisplayName("Should return failure for non-existent period ID")
        void bulkArchive_nonExistent_returnsNotFound() {
            when(periodRepository.findAllByIdIn(List.of(999)))
                    .thenReturn(Collections.emptyList());

            BulkPeriodResponse response = periodService.bulkArchive(List.of(999));

            assertThat(response.getResults()).hasSize(1);
            assertThat(response.getResults().get(0).isSuccess()).isFalse();
            assertThat(response.getResults().get(0).getMessage())
                    .contains("Không tìm thấy kỳ lịch");
        }
    }
}
