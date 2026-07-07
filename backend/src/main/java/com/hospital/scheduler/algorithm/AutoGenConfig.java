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
    List<String> l01AllowedSpecialties,  // Danh sách specialties được gán L01 (null/empty = CORE = Ngoại,Nội)
    List<String> l02AllowedSpecialties,  // Danh sách specialties được gán L02 (null/empty = CORE = Ngoại,Nội)
    List<String> l03AllowedSpecialties   // Danh sách specialties được gán L03 (null/empty = CORE = Ngoại,Nội)
) {}
