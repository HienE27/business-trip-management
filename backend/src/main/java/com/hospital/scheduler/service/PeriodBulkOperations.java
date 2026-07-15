package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.BulkPeriodResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Shared helper for bulk publish / archive operations on
 * {@link SchedulePeriod}. Both bulk flows share the same skeleton:
 *
 * <ol>
 *   <li>Batch-fetch all referenced periods in one query.</li>
 *   <li>Pre-validate each (not-found / wrong state) — skip with error result.</li>
 *   <li>Call the per-period action and wrap success/failure into a {@link BulkPeriodResponse}.</li>
 *   <li>Evict the dashboard cache once at the end.</li>
 * </ol>
 *
 * <p>Extracted from {@link SchedulePeriodService} so the bulk logic stays
 * in one place (was duplicated between {@code bulkPublish} and
 * {@code bulkArchive} pre-P7).</p>
 */
@Component
@RequiredArgsConstructor
public class PeriodBulkOperations {

    private final SchedulePeriodRepository periodRepository;
    private final CacheEvictor cacheEvictor;

    /**
     * Run a bulk operation over a list of period IDs.
     *
     * @param periodIds     the IDs to process (preserves caller ordering in the result)
     * @param requiredState the state the period must be in to be eligible
     * @param action        the per-period action — receives (periodId, extraArg) and
     *                      returns the {@link SchedulePeriodResponse} payload on success.
     *                      Should throw {@link BadRequestException} for state validation
     *                      errors and any RuntimeException for unexpected failures.
     * @param extraArg      optional 2nd argument passed to {@code action}; for example
     *                      publishPeriod needs the publishing user ID while archive does not.
     */
    public <A> BulkPeriodResponse runBulk(
            List<Integer> periodIds,
            SchedulePeriod.PeriodStatus requiredState,
            BiFunction<Integer, A, SchedulePeriodResponse> action,
            A extraArg) {

        List<SchedulePeriod> periods = periodRepository.findAllByIdIn(periodIds);
        Map<Integer, SchedulePeriod> periodMap = periods.stream()
                .collect(Collectors.toMap(SchedulePeriod::getId, p -> p));

        List<BulkPeriodResponse.PeriodResult> results = periodIds.stream()
                .map(id -> {
                    SchedulePeriod period = periodMap.get(id);
                    if (period == null) {
                        return BulkPeriodResponse.PeriodResult.builder()
                                .id(id)
                                .success(false)
                                .message("Không tìm thấy kỳ lịch với ID: " + id)
                                .processedAt(LocalDateTime.now())
                                .build();
                    }
                    if (period.getStatus() != requiredState) {
                        return BulkPeriodResponse.PeriodResult.builder()
                                .id(id)
                                .periodName(period.getPeriodName())
                                .success(false)
                                .message("Kỳ lịch '" + period.getPeriodName()
                                        + "' không ở trạng thái " + requiredState
                                        + " (hiện tại: " + period.getStatus() + ")")
                                .processedAt(LocalDateTime.now())
                                .build();
                    }
                    return runSingle(id, action, extraArg, period.getPeriodName());
                })
                .toList();
        cacheEvictor.evictDashboard();
        return BulkPeriodResponse.of(results);
    }

    private <A> BulkPeriodResponse.PeriodResult runSingle(
            Integer id,
            BiFunction<Integer, A, SchedulePeriodResponse> action,
            A extraArg,
            String fallbackName) {
        try {
            SchedulePeriodResponse result = action.apply(id, extraArg);
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .periodName(result.getPeriodName())
                    .success(true)
                    .message("Thao tác thành công")
                    .data(result)
                    .processedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .periodName(fallbackName)
                    .success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Lỗi không xác định")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
    }
}
