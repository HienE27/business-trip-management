package com.hospital.scheduler.scheduling.config;

import com.hospital.scheduler.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Whitelist of sortable fields for {@code ConfigProfile} list endpoints.
 *
 * <p>PR-11-03 deliberately restricts sort to known entity fields. Any other
 * field name in {@code ?sort=...} yields HTTP 400. This stops the frontend
 * from accidentally coupling to JPA property names that may be renamed
 * during a future refactor.
 */
public final class ConfigProfileSort {

    private ConfigProfileSort() {}

    /** Fields the caller is allowed to sort by. */
    public static final Set<String> ALLOWED_FIELDS = Set.of(
            "nameVi",
            "nameEn",
            "category",
            "isFavorite",
            "createdAt",
            "updatedAt"
    );

    /** Default sort when the caller omits {@code ?sort=...}. */
    public static final Sort DEFAULT = Sort.by(Sort.Direction.ASC, "nameVi");

    /**
     * Build a {@link Sort} from the request parameter, or return
     * {@link #DEFAULT} if the caller did not specify one.
     *
     * <p>Accepted formats:
     * <ul>
     *   <li>{@code nameVi} → ASC by {@code nameVi}</li>
     *   <li>{@code nameVi,asc} → ASC</li>
     *   <li>{@code updatedAt,desc} → DESC</li>
     * </ul>
     *
     * @throws BadRequestException if the field is not in the whitelist or the
     *                             direction is neither {@code asc} nor
     *                             {@code desc}.
     */
    public static Sort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String[] parts = raw.split(",", -1);
        String field = parts[0].trim();
        if (!ALLOWED_FIELDS.contains(field)) {
            throw new BadRequestException(
                    "Trường sort không hợp lệ: '" + field
                            + "'. Các trường được phép: " + ALLOWED_FIELDS);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String d = parts[1].trim().toLowerCase();
            if (d.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!d.equals("asc") && !d.isEmpty()) {
                throw new BadRequestException(
                        "Hướng sort không hợp lệ: '" + parts[1]
                                + "'. Chỉ chấp nhận 'asc' hoặc 'desc'");
            }
        }
        return Sort.by(direction, field);
    }
}