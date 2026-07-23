package com.hospital.scheduler.scheduling.config.dto;

import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.config.ConfigProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Payload accepted by PUT /api/v1/config/profiles/{id}.
 *
 * <p>All fields are optional so callers can perform partial updates. Any
 * non-null field replaces the existing value on the profile. When
 * {@link #config} is provided, the service validates it before persisting.
 */
@Schema(description = "Payload cập nhật 1 phần — null field nghĩa là giữ nguyên.")
public record UpdateProfileRequest(
        @Size(max = 128, message = "Tên profile (VI) tối đa 128 ký tự")
        @Schema(example = "Cấu hình mới cho khoa Nội (đã cập nhật)",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String nameVi,

        @Size(max = 128, message = "Tên profile (EN) tối đa 128 ký tự")
        @Schema(example = "Updated config", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String nameEn,

        @Size(max = 512, message = "Mô tả tối đa 512 ký tự")
        @Schema(example = "Bổ sung: ca đêm ưu tiên bác sĩ nam",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,

        @Schema(example = "DEPARTMENT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        ConfigProfile.ProfileCategory category,

        @Size(max = 64, message = "Icon tối đa 64 ký tự")
        @Schema(example = "stethoscope", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String icon,

        @Schema(example = "[\"noi-khoa\",\"ngay\",\"dem\"]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String[] tags,

        @Schema(description = "Snapshot config mới — nếu có sẽ validate trước khi lưu",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        ConfigDomain config
) {
}