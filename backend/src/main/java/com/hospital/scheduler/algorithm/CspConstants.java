package com.hospital.scheduler.algorithm;

/**
 * Shared shift-type constants and lookup helpers used across all CSP modules.
 * Keeping them in one place avoids the "string soup" of L0X literals and
 * prevents drift between modules (e.g. conflict rules vs. result rendering).
 */
public final class CspConstants {

    public static final String DIRECT_24H = "L01";
    public static final String THONG_TAM = "L02";
    public static final String DICH_VU = "L03";
    public static final String CHUYEN_GIA = "L04";

    public static final String[] SHIFT_ORDER = {DIRECT_24H, THONG_TAM, DICH_VU, CHUYEN_GIA};

    private CspConstants() {}

    /**
     * BR-01: L01 ↔ L02 conflict
     * BR-02: L03 ↔ L04 conflict
     * Same shift type on the same day is allowed (multiple L01 slots is fine
     * since {@code requiredStaffCount} may be > 1).
     */
    public static boolean conflicts(String t1, String t2) {
        if (t1.equals(t2)) return false;
        if ((t1.equals(DIRECT_24H) && t2.equals(THONG_TAM)) ||
            (t1.equals(THONG_TAM) && t2.equals(DIRECT_24H))) return true;
        if ((t1.equals(DICH_VU) && t2.equals(CHUYEN_GIA)) ||
            (t1.equals(CHUYEN_GIA) && t2.equals(DICH_VU))) return true;
        return false;
    }

    public static int getShiftIdx(String shiftTypeId) {
        for (int i = 0; i < SHIFT_ORDER.length; i++) {
            if (SHIFT_ORDER[i].equals(shiftTypeId)) return i;
        }
        return -1;
    }

    public static String getShiftTypeName(String shiftType) {
        return switch (shiftType) {
            case DIRECT_24H -> "Lịch trực 24/24";
            case THONG_TAM -> "Lịch thông tầm";
            case DICH_VU -> "Lịch phòng khám dịch vụ";
            case CHUYEN_GIA -> "Lịch phòng khám chuyên gia";
            default -> shiftType;
        };
    }
}
