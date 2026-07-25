package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

public record AutoGenConfigRecommendRequest(
    @NotNull
    @Positive
    Integer periodDays,

    @NotNull
    @Positive
    Integer periodWeeks,

    @NotNull
    @Positive
    Integer totalStaff,

    /**
     * Map shiftTypeId ("L01","L02","L03","L04") → số người đủ điều kiện
     */
    @NotNull
    Map<String, Integer> eligibleStaff,

    /**
     * Map shiftTypeId ("L01","L02","L03","L04") → mục tiêu ca/người/tháng (số nguyên ≥ 0)
     */
    @NotNull
    Map<String, Integer> targetPerStaffPerMonth,

    /**
     * Optional: nếu muốn mở rộng L01/L02/L03 eligibility cho tất cả specialties
     * (true → áp dụng, false/null → giữ Ngoại,Nội)
     */
    Boolean expandNonL04Eligibility,

    /**
     * Optional: danh sách tên specialty mở rộng (nếu expandNonL04Eligibility=true).
     * Mặc định: 10 specialties Ngoại,Nội,Sản,Nhi,Mắt,Răng,Bác sĩ,Điều dưỡng,Kỹ thuật viên,Dược sĩ
     */
    List<String> expandedSpecialties,

    /**
     * Optional: max shifts per staff (từ runtime config).
     * Nếu có, dùng để giới hạn target không vượt năng lực.
     */
    Integer maxShiftsPerStaff,

    @Schema(description = "Kiểu sắp xếp user chọn (INTRA_TYPE | WITH_INTER_BALANCE), null = auto-detect")
    String arrangementMode
) {}