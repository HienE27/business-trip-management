package com.hospital.scheduler.algorithm;

import java.util.List;

/**
 * Configuration for L04 cross-specialty assignment, shared by all schedulers.
 *
 * <p>When {@link #enabled()} is {@code true}, eligible staff whose specialty does not match
 * the L04 requirement's specialty may still be assigned, subject to:
 * <ul>
 *   <li>{@link #ratio()} — max proportion of cross-specialty staff per requirement (0.0–1.0).
 *       E.g. ratio=0.3 on a requirement of 5 staff → at most ceil(5×0.3)=2 cross-specialty slots.</li>
 *   <li>{@link #allowedSpecialties()} — if non-empty, only requirements whose specialty name
 *       appears in this list may receive cross-specialty staff. Empty = all specialties.</li>
 * </ul>
 *
 * @param enabled            master switch: {@code false} → strict specialty match only
 * @param ratio              max cross-specialty ratio per requirement (0.0–1.0)
 * @param allowedSpecialties specialty names that permit cross-specialty (empty = all)
 */
public record L04CrossSpecialtyConfig(boolean enabled, float ratio, List<String> allowedSpecialties) {

    /** Convenience: an always-strict (cross disabled) config. */
    public static final L04CrossSpecialtyConfig DISABLED =
            new L04CrossSpecialtyConfig(false, 0.0f, List.of());

    /**
     * Whether cross-specialty is permitted for the given requirement specialty name.
     * Returns {@code true} when:
     * <ul>
     *   <li>{@link #enabled()} is {@code true}, AND</li>
     *   <li>{@code specName} is {@code null} OR {@link #allowedSpecialties()} is empty
     *       OR {@code specName} is contained in {@link #allowedSpecialties()}.</li>
     * </ul>
     */
    public boolean isPermittedFor(String specName) {
        if (!enabled) return false;
        if (specName == null || allowedSpecialties.isEmpty()) return true;
        return allowedSpecialties.contains(specName);
    }

    /**
     * Max number of cross-specialty staff for a requirement of the given size.
     * Always at least 1 when {@link #enabled()} is {@code true}, so that a single
     * cross-specialty slot is available even on small requirements.
     */
    public int crossCap(int requiredStaffCount) {
        if (!enabled) return 0;
        return Math.max(1, (int) Math.ceil(requiredStaffCount * ratio));
    }
}
