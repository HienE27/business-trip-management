package com.hospital.scheduler.algorithm;

public record AutoGenConfig(
    boolean enabled,
    int l01MinPerDay, int l02MinPerDay, int l03MinPerDay, int l04MinPerDay,
    int l01MaxPerDay, int l02MaxPerDay, int l03MaxPerDay, int l04MaxPerDay,
    int l01MinPerWeek, int l02MinPerWeek, int l03MinPerWeek, int l04MinPerWeek,
    int l01MaxPerWeek, int l02MaxPerWeek, int l03MaxPerWeek, int l04MaxPerWeek,
    String holidayMode  // "SKIP" or "PARTIAL"
) {}
