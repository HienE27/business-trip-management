package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.BulkPeriodResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodBulkOperations — shared bulk helper (P7)")
class PeriodBulkOperationsTest {

    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private CacheEvictor cacheEvictor;

    private PeriodBulkOperations helper;

    private SchedulePeriod draftPeriod;
    private SchedulePeriod publishedPeriod;

    @BeforeEach
    void setUp() {
        helper = new PeriodBulkOperations(periodRepository, cacheEvictor);
        draftPeriod = SchedulePeriod.builder()
                .id(1).periodName("Kỳ DRAFT")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();
        publishedPeriod = SchedulePeriod.builder()
                .id(2).periodName("Kỳ PUBLISHED")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 31))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .build();
    }

    @Test
    @DisplayName("Not-found IDs produce a failure result, action is not invoked")
    void runBulk_unknownId_yieldsFailureAndSkipsAction() {
        when(periodRepository.findAllByIdIn(List.of(999))).thenReturn(Collections.emptyList());

        BulkPeriodResponse res = helper.runBulk(List.of(999),
                SchedulePeriod.PeriodStatus.DRAFT,
                (id, arg) -> { throw new AssertionError("Action must NOT be called for missing IDs"); },
                (Void) null);

        assertThat(res.getResults()).hasSize(1);
        assertThat(res.getResults().get(0).isSuccess()).isFalse();
        assertThat(res.getResults().get(0).getMessage()).contains("Không tìm thấy");
        verify(cacheEvictor).evictDashboard();
    }

    @Test
    @DisplayName("Wrong-state periods get a detailed failure with the required state name")
    void runBulk_wrongState_yieldsFailureWithRequiredState() {
        when(periodRepository.findAllByIdIn(List.of(2))).thenReturn(List.of(publishedPeriod));

        BulkPeriodResponse res = helper.runBulk(List.of(2),
                SchedulePeriod.PeriodStatus.DRAFT,
                (id, arg) -> { throw new AssertionError("Action must NOT be called for wrong-state"); },
                (Void) null);

        assertThat(res.getResults()).hasSize(1);
        assertThat(res.getResults().get(0).isSuccess()).isFalse();
        assertThat(res.getResults().get(0).getMessage())
                .contains("DRAFT")
                .contains("PUBLISHED");
    }

    @Test
    @DisplayName("Successful action returns success result with the data")
    void runBulk_actionSuccess_returnsSuccessResult() {
        when(periodRepository.findAllByIdIn(List.of(1))).thenReturn(List.of(draftPeriod));

        BulkPeriodResponse res = helper.runBulk(List.of(1),
                SchedulePeriod.PeriodStatus.DRAFT,
                (id, arg) -> SchedulePeriodResponse.builder()
                        .id(id).periodName("Renamed").status("PUBLISHED").build(),
                (Void) null);

        assertThat(res.getResults()).hasSize(1);
        assertThat(res.getResults().get(0).isSuccess()).isTrue();
        assertThat(res.getResults().get(0).getData().getPeriodName()).isEqualTo("Renamed");
    }

    @Test
    @DisplayName("BadRequestException from action is surfaced as a failure with the message")
    void runBulk_actionThrows_returnsFailureWithMessage() {
        when(periodRepository.findAllByIdIn(List.of(1))).thenReturn(List.of(draftPeriod));

        BulkPeriodResponse res = helper.runBulk(List.of(1),
                SchedulePeriod.PeriodStatus.DRAFT,
                (id, arg) -> { throw new BadRequestException("kỳ có xung đột: BS A ngày 2026-06-15"); },
                (Void) null);

        assertThat(res.getResults()).hasSize(1);
        assertThat(res.getResults().get(0).isSuccess()).isFalse();
        assertThat(res.getResults().get(0).getMessage()).contains("BS A");
    }

    @Test
    @DisplayName("Mixed batch — one valid, one invalid — both surface in order")
    void runBulk_mixedBatch_preservesInputOrder() {
        when(periodRepository.findAllByIdIn(List.of(1, 999))).thenReturn(List.of(draftPeriod));

        BulkPeriodResponse res = helper.runBulk(List.of(1, 999),
                SchedulePeriod.PeriodStatus.DRAFT,
                (id, arg) -> SchedulePeriodResponse.builder().id(id).periodName("ok").build(),
                (Void) null);

        assertThat(res.getResults()).hasSize(2);
        // Order is preserved from input list — period 1 first, then 999
        assertThat(res.getResults().get(0).getId()).isEqualTo(1);
        assertThat(res.getResults().get(0).isSuccess()).isTrue();
        assertThat(res.getResults().get(1).getId()).isEqualTo(999);
        assertThat(res.getResults().get(1).isSuccess()).isFalse();
    }
}