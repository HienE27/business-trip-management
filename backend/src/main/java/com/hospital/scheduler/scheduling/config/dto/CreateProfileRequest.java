package com.hospital.scheduler.scheduling.config.dto;

import com.hospital.scheduler.scheduling.config.ConfigProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload accepted by POST /api/v1/config/profiles.
 */
@Schema(description = "Payload tạo profile mới — Service clone config hiện tại vào profile.")
public record CreateProfileRequest(
        @NotBlank(message = "Tên profile (VI) không được để trống")
        @Size(max = 128, message = "Tên profile tối đa 128 ký tự")
        @Schema(description = "Tên tiếng Việt — bắt buộc, 1–128 ký tự",
                example = "Cấu hình mới cho khoa Nội",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,

        @Size(max = 128, message = "Tên profile (EN) tối đa 128 ký tự")
        @Schema(description = "Tên tiếng Anh (optional)",
                example = "New config for Internal Medicine",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String nameEn,

        @Size(max = 512, message = "Mô tả tối đa 512 ký tự")
        @Schema(description = "Mô tả ngắn (optional)",
                example = "Ưu tiên bác sĩ nữ, ca ngày",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,

        @Schema(description = "Loại profile (mặc định GENERAL)",
                example = "DEPARTMENT",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        ConfigProfile.ProfileCategory category,

        @Size(max = 64, message = "Icon tối đa 64 ký tự")
        @Schema(description = "Material Symbols icon name",
                example = "stethoscope",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String icon,

        @Schema(description = "Tag — dùng cho filter",
                example = "[\"noi-khoa\",\"ngay\"]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String[] tags
) {
}