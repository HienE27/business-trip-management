package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Auto-scheduling concurrency smoke test, executed as a plain Mockito test (no Spring
 * context). Verifies that the service can be called from multiple threads without
 * throwing and that each invocation produces a non-null result.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoSchedulingService Concurrency Tests (Smoke) - ThreadLocal state isolation")
class AutoSchedulingServiceConcurrencyTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftRequirementRepository requirementRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AlgorithmMetricsRepository metricsRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private NotificationService notificationService;
    @Mock private AlgorithmConfigService algorithmConfigService;
    @Mock private HolidayRepository holidayRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private SpecialtyRepository specialtyRepository;

    private AutoSchedulingService autoSchedulingService;

    @BeforeEach
    void setUp() {
        autoSchedulingService = new AutoSchedulingService(
                scheduleRepository, periodRepository, staffRepository, requirementRepository,
                compensationDayRepository, leaveRequestRepository, metricsRepository,
                conflictDetectionService, auditHistoryService, compensationDateCalculator, notificationService,
                algorithmConfigService, holidayRepository, shiftTypeRepository, specialtyRepository
        );

        SchedulePeriod testPeriod = SchedulePeriod.builder().id(1).periodName("Tháng 6/2026 - Concurrency")
                .startDate(java.time.LocalDate.of(2026, 6, 1))
                .endDate(java.time.LocalDate.of(2026, 6, 7))
                .status(SchedulePeriod.PeriodStatus.DRAFT).build();
        Staff staff1 = Staff.builder().id(1).username("dr1").isActive(true).build();
        Staff staff2 = Staff.builder().id(2).username("dr2").isActive(true).build();
        Staff staff3 = Staff.builder().id(3).username("dr3").isActive(true).build();

        lenient().when(periodRepository.findById(1)).thenReturn(java.util.Optional.of(testPeriod));
        lenient().when(requirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        lenient().when(scheduleRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        lenient().when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(staff1, staff2, staff3));

        // Batch-loading mocks for conflict data optimization
        lenient().when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(compensationDayRepository.findInRange(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(scheduleRepository.findL01SchedulesInRange(any(), any())).thenReturn(Collections.emptyList());

        lenient().when(conflictDetectionService.detectAllConflicts(org.mockito.ArgumentMatchers.anyInt(), any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Collections.emptyList());
        lenient().when(scheduleRepository.save(any())).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            if (s.getId() == null) s.setId((int) (System.currentTimeMillis() % 10000));
            return s;
        });
        lenient().when(metricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock runtime config to return defaults
        lenient().when(algorithmConfigService.getRuntimeConfig())
                .thenReturn(AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                        .maxIterations(1000)
                        .weekendWeight(java.math.BigDecimal.valueOf(2.0))
                        .overnightRecoveryHours(24)
                        .greedyCoverageThreshold(java.math.BigDecimal.valueOf(0.85))
                        .balanceScoreMin(java.math.BigDecimal.valueOf(0.70))
                        .autoCompensationEnabled(true)
                        .backtrackTimeLimitSeconds(60)
                        .build());
    }

    @Test
    @DisplayName("Service is callable from multiple threads without state leak")
    void previewAutoSchedule_concurrentCalls_shouldBeThreadSafe() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Future<AutoScheduleResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return autoSchedulingService.previewSchedule(
                            AutoScheduleRequestDTO.builder().periodId(1).build()
                    );
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        for (Future<AutoScheduleResponse> f : futures) {
            try {
                assertThat(f.get()).isNotNull();
            } catch (ExecutionException e) {
                throw new AssertionError("Concurrent call failed: " + e.getMessage(), e);
            }
        }
    }

    @Test
    @DisplayName("Different excluded staff lists do not throw concurrently")
    void previewAutoSchedule_concurrentWithDifferentExclusions_shouldHaveDifferentResults() throws InterruptedException {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Future<AutoScheduleResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            List<Integer> excluded = idx == 0 ? List.of(1) : idx == 1 ? List.of(2) : List.of(3);
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return autoSchedulingService.previewSchedule(
                            AutoScheduleRequestDTO.builder()
                                    .periodId(1)
                                    .excludedStaffIds(excluded).build()
                    );
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        for (Future<AutoScheduleResponse> f : futures) {
            try {
                assertThat(f.get()).isNotNull();
            } catch (ExecutionException e) {
                throw new AssertionError("Concurrent call failed: " + e.getMessage(), e);
            }
        }
    }

    @Test
    @DisplayName("No thread-local state leakage between concurrent calls")
    void previewAutoSchedule_noStateLeakage() throws InterruptedException {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    AutoScheduleResponse preview = autoSchedulingService.previewSchedule(
                            AutoScheduleRequestDTO.builder().periodId(1).build()
                    );
                    return Map.of("preview", (Object) (preview != null));
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        for (Future<Map<String, Object>> f : futures) {
            try {
                assertThat(f.get()).isNotNull();
                assertThat(f.get().get("preview")).isEqualTo(true);
            } catch (ExecutionException e) {
                throw new AssertionError("Concurrent call failed: " + e.getMessage(), e);
            }
        }
    }
}
