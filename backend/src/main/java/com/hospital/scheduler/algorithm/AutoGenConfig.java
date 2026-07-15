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
    
    // L01 cross-specialty
    boolean l01CrossSpecialty,
    float l01CrossSpecialtyRatio,
    List<String> l01AllowedSpecialties,
    String l01BalanceStrategy,  // "STRICT_MATCH_ONLY", "FAIR_DISTRIBUTE", "WEIGHTED_FAIR"
    
    // L02 cross-specialty
    boolean l02CrossSpecialty,
    float l02CrossSpecialtyRatio,
    List<String> l02AllowedSpecialties,
    String l02BalanceStrategy,
    
    // L03 cross-specialty
    boolean l03CrossSpecialty,
    float l03CrossSpecialtyRatio,
    List<String> l03AllowedSpecialties,
    String l03BalanceStrategy,
    
    // L04 cross-specialty
    boolean l04CrossSpecialty,
    float l04CrossSpecialtyRatio,
    List<String> l04AllowedSpecialties,
    String l04BalanceStrategy
) {}
