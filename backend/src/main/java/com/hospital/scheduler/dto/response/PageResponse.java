package com.hospital.scheduler.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Canonical pagination envelope used by all list endpoints in PR-11-03.
 *
 * <p>Shape is stable so the frontend can write one paged list renderer and
 * reuse it across screens. Field naming follows common REST conventions:
 * <ul>
 *   <li>{@code items} — page contents (alias of {@code content} from
 *       {@link Page}; renamed for friendlier JSON).</li>
 *   <li>{@code page} — 0-based index of the current page.</li>
 *   <li>{@code size} — page size actually applied (after caps).</li>
 *   <li>{@code totalItems} — total matching rows across all pages.</li>
 *   <li>{@code totalPages} — derived from totalItems / size.</li>
 *   <li>{@code hasNext} / {@code hasPrev} — navigation flags.</li>
 *   <li>{@code sort} — active sort, e.g. {@code "nameVi,ASC"}; null if
 *       unsorted. <b>Direction casing is part of the v1 contract — do not
 *       lowercase without a version bump.</b></li>
 * </ul>
 */
@Schema(description = "Canonical paged response envelope. Contract frozen at v1.")
public record PageResponse<T>(
        @Schema(description = "Các dòng trong trang hiện tại") List<T> items,
        @Schema(description = "Số trang (0-based)", example = "0") int page,
        @Schema(description = "Số dòng / trang (sau khi áp cap)", example = "20") int size,
        @Schema(description = "Tổng số dòng khớp filter (toàn DB)", example = "135") long totalItems,
        @Schema(description = "Tổng số trang", example = "7") int totalPages,
        @Schema(description = "Còn trang kế tiếp?", example = "true") boolean hasNext,
        @Schema(description = "Có trang trước?", example = "false") boolean hasPrev,
        @Schema(description = "Sort đang áp dụng — `field,direction`",
                example = "nameVi,ASC", nullable = true) String sort
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).toList();
        String sort = page.getSort().isSorted()
                ? page.getSort().toString().replace(": ", ",")
                : null;
        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious(),
                sort
        );
    }
}