package com.hospital.scheduler.scheduling.config.dto;

import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.config.ConfigProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Read DTO returned from ConfigProfileService to the controller layer.
 */
@Schema(description = "Profile cấu hình — read model trả về cho client.")
public record ConfigProfileDto(
        @Schema(description = "DB primary key", example = "1") Long id,
        @Schema(description = "Slug ổn định, dùng cho URL / export / import",
                example = "balanced") String profileKey,
        @Schema(description = "Tên tiếng Việt (bắt buộc, unique logic)",
                example = "Cân bằng") String nameVi,
        @Schema(description = "Tên tiếng Anh (optional)",
                example = "Balanced") String nameEn,
        @Schema(description = "Mô tả ngắn (≤ 512 ký tự)") String description,
        @Schema(description = "Loại profile — dùng để nhóm",
                example = "GENERAL") ConfigProfile.ProfileCategory category,
        @Schema(description = "Material Symbols icon name",
                example = "balance") String icon,
        @Schema(description = "Tag — dùng cho filter mở rộng",
                example = "[\"starter\",\"safe\"]") String[] tags,
        @Schema(description = "Profile hệ thống (read-only ngoại trừ apply)",
                example = "true") boolean isSystem,
        @Schema(description = "Profile đang làm default",
                example = "true") boolean isDefault,
        @Schema(description = "User đã đánh dấu yêu thích",
                example = "false") boolean isFavorite,
        @Schema(description = "Snapshot config thuật toán (null nếu parse lỗi)") ConfigDomain config,
        @Schema(description = "Username tạo profile",
                example = "system") String createdBy,
        @Schema(description = "Timestamp tạo",
                example = "2026-05-12T08:30:00") LocalDateTime createdAt,
        @Schema(description = "Timestamp cập nhật gần nhất",
                example = "2026-07-01T14:22:00") LocalDateTime updatedAt
) {
}