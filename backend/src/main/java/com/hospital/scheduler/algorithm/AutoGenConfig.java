package com.hospital.scheduler.algorithm;

public record AutoGenConfig(
    boolean enabled,
    int l01RequiredPerDay,
    int l02RequiredPerDay,
    int l03RequiredPerDay,
    int l04RequiredPerDay,
    int minL01PerWeek,
    int minL02PerWeek,
    int minL03PerWeek,
    int minL04PerWeek,
    String holidayMode  // "SKIP" or "PARTIAL"
) {}
