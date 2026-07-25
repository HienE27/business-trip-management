package com.hospital.scheduler.algorithm;

import java.util.List;

public record AutoGenConfig(
    boolean enabled,
    int l01MinPerDay, int l02MinPerDay, int l03MinPerDay, int l04MinPerDay,
    int l01MaxPerDay, int l02MaxPerDay, int l03MaxPerDay, int l04MaxPerDay,
    int l01MinPerWeek, int l02MinPerWeek, int l03MinPerWeek, int l04MinPerWeek,
    int l01MaxPerWeek, int l02MaxPerWeek, int l03MaxPerWeek, int l04MaxPerWeek,
    String holidayMode,  // "SKIP" or "PARTIAL"
    List<String> removedShiftTypes,  // e.g. ["L03", "L04"] to skip when generating
    boolean l04CrossSpecialty,  // Allow cross-specialty assignment for L04
    float l04CrossSpecialtyRatio,  // Max ratio (0.0-1.0) of cross-specialty staff per requirement
    List<String> l04AllowedSpecialties,  // Danh sách specialties được gán L04 (null/empty = tất cả)
    List<String> l01AllowedSpecialties,  // Legacy; L01 hiện nhận mọi specialty
    List<String> l02AllowedSpecialties,  // Legacy; L02 hiện nhận mọi specialty
    List<String> l03AllowedSpecialties,  // Legacy; L03 hiện nhận mọi specialty
    // Target ca/người/tháng — input cho recommendAutoGenConfig. Persist vào DB
    // để UI refresh không reset về default. Default L01-L03=2, L04=5 (xem AlgorithmConfigService).
    int l01TargetPerMonth, int l02TargetPerMonth, int l03TargetPerMonth, int l04TargetPerMonth,
    // Chiến lược cân bằng L04 khi cross-specialty:
    // STRICT_MATCH_ONLY | FAIR_DISTRIBUTE | WEIGHTED_FAIR.
    // Frontend default = "FAIR_DISTRIBUTE".
    String l04BalanceStrategy
) {}
