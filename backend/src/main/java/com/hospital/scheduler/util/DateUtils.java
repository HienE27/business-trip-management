package com.hospital.scheduler.util;

import java.time.DayOfWeek;

/**
 * Shared utility for date formatting.
 */
public final class DateUtils {

    private DateUtils() {}

    /**
     * Convert day-of-week value (1=Mon ... 7=Sun) to Vietnamese label.
     */
    public static String getDayOfWeekVietnamese(int day) {
        return switch (day) {
            case 1 -> "Thứ 2";
            case 2 -> "Thứ 3";
            case 3 -> "Thứ 4";
            case 4 -> "Thứ 5";
            case 5 -> "Thứ 6";
            case 6 -> "Thứ 7";
            case 7 -> "Chủ Nhật";
            default -> "";
        };
    }

    /**
     * Overload for DayOfWeek enum.
     */
    public static String getDayOfWeekVietnamese(DayOfWeek dow) {
        return getDayOfWeekVietnamese(dow.getValue());
    }
}
