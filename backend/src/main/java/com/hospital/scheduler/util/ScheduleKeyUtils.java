package com.hospital.scheduler.util;

import java.time.LocalDate;

/**
 * Centralised key-factory for the three string-key formats that appear
 * throughout the auto-scheduling pipeline.
 *
 * <p>All keys involving a date use the ISO format ({@code yyyy-MM-dd}) so
 * that lexical ordering equals chronological ordering.
 *
 * <h3>Canonical format</h3>
 * The CSP layer ({@code CspSearchEngine}, {@code CspResultBuilder}) produces
 * assignment keys with the pipe separator.  This is the <b>canonical</b>
 * format — all code that writes to shared caches ({@code ThreadLocal}
 * sets, compensation-day sets) must use this format so that reads are
 * always consistent regardless of which algorithm generated the data.
 *
 * <h3>Why two separators?</h3>
 * {@code _} (underscore) appears in serialised schedule-removal keys
 * passed across the HTTP boundary ({@code applyPreviewSchedule}).  Those
 * keys are intentionally a three-part tuple
 * ({@code staffId_date_shiftTypeId}) and never compared against the
 * canonical pipe-separator assignment keys used by the CSP layer.
 *
 * <h3>Migration note</h3>
 * Shared state (compensation-day sets, ThreadLocal caches) must always
 * use the canonical pipe format.  When new auto-scheduling algorithms
 * are introduced they should likewise write through the canonical format
 * so reads stay consistent across the codebase.
 */
public final class ScheduleKeyUtils {

    private static final String PIPE = "|";
    private static final String UNDERSCORE = "_";

    private ScheduleKeyUtils() {}

    // ─────────────────────────────────────────────────────────────
    // Canonical (pipe) keys — used by CSP and all shared caches
    // ─────────────────────────────────────────────────────────────

    /**
     * Canonical assignment key produced by the CSP layer.
     * Format: {@code staffId|yyyy-MM-dd}
     */
    public static String cspAssignmentKey(int staffId, LocalDate date) {
        return staffId + PIPE + date.toString();
    }

    /**
     * Canonical assignment key produced by the CSP layer.
     * Format: {@code staffId|yyyy-MM-dd}
     */
    public static String cspAssignmentKey(Integer staffId, LocalDate date) {
        return staffId + PIPE + date.toString();
    }

    /**
     * Parse the staff ID from a canonical CSP assignment key.
     * @param key a key in the format {@code staffId|yyyy-MM-dd}
     */
    public static int parseStaffIdFromCspKey(String key) {
        return Integer.parseInt(key.split("\\" + PIPE)[0]);
    }

    /**
     * Parse the date from a canonical CSP assignment key.
     * @param key a key in the format {@code staffId|yyyy-MM-dd}
     */
    public static LocalDate parseDateFromCspKey(String key) {
        return LocalDate.parse(key.split("\\" + PIPE)[1]);
    }

    // ─────────────────────────────────────────────────────────────
    // Three-part schedule key (HTTP boundary)
    // ─────────────────────────────────────────────────────────────

    /**
     * Three-part key used for serialising schedule removal requests
     * across the HTTP boundary ({@code applyPreviewSchedule}).
     * Format: {@code staffId_yyyy-MM-dd_shiftTypeId}
     * <p>
     * This format is never compared against assignment or compensation-day
     * sets — it is purely for HTTP payload serialisation.
     */
    public static String scheduleRemovalKey(int staffId, LocalDate date, String shiftTypeId) {
        return staffId + UNDERSCORE + date.toString() + UNDERSCORE + shiftTypeId;
    }

    public static String scheduleRemovalKey(Integer staffId, LocalDate date, String shiftTypeId) {
        return staffId + UNDERSCORE + date.toString() + UNDERSCORE + shiftTypeId;
    }
}
