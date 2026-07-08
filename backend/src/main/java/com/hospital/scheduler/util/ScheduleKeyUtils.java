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
 * {@code _} (underscore) appears in the legacy GA path
 * ({@code GeneticAlgorithmScheduler} / {@code ScheduleChromosome}) for
 * serialised assignment keys.  It is <b>not</b> written to shared caches —
 * only to the GA's own internal structures.  The one exception is
 * {@code scheduleKey()} used by {@code applyPreviewSchedule} for serialising
 * removal-requests across the HTTP boundary; that format is intentionally
 * a three-part key ({@code staffId_date_shiftTypeId}) and is never compared
 * against compensation-day or assignment sets.
 *
 * <h3>Migration note</h3>
 * When the GA path is refactored to use pipe-separated keys internally,
 * the underscore-format methods can be removed.  Until then, callers that
 * bridge between the two worlds (e.g. {@code runGeneticAlgorithm}) are
 * responsible for converting their internal underscore keys to the
 * canonical pipe format before touching shared state.
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
    // Underscore keys — legacy GA internal format
    // ─────────────────────────────────────────────────────────────

    /**
     * Legacy assignment key used inside {@code GeneticAlgorithmScheduler}.
     * Format: {@code staffId_yyyy-MM-dd}
     *
     * @deprecated callers must migrate to the canonical pipe format.
     *             This method will be removed once the GA path is refactored.
     */
    @Deprecated
    public static String gaAssignmentKey(int staffId, LocalDate date) {
        return staffId + UNDERSCORE + date.toString();
    }

    /**
     * Parse the staff ID from a legacy GA assignment key.
     * @deprecated callers must migrate to the canonical pipe format.
     */
    @Deprecated
    public static int parseStaffIdFromGaKey(String key) {
        return Integer.parseInt(key.split(UNDERSCORE)[0]);
    }

    /**
     * Parse the date from a legacy GA assignment key.
     * @deprecated callers must migrate to the canonical pipe format.
     */
    @Deprecated
    public static LocalDate parseDateFromGaKey(String key) {
        return LocalDate.parse(key.split(UNDERSCORE)[1]);
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
