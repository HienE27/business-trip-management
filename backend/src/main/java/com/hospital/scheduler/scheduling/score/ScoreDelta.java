package com.hospital.scheduler.scheduling.score;

/**
 * Immutable record representing score changes.
 *
 * <p>Each field is a delta (can be negative). The {@link MutableScore} applies
 * a delta by adding field-by-field.
 *
 * @param hardDelta      change in hard-constraint violations
 * @param coverageDelta  change in coverage (assigned / required)
 * @param cvDelta        change in coefficient of variation
 * @param weekendDelta   change in weekend-fairness penalty
 * @param consecutiveDelta change in max-consecutive-days penalty
 * @param gapDelta       change in shift-count gap penalty
 * @param giniDelta      change in Gini coefficient
 */
public record ScoreDelta(
        int hardDelta,
        double coverageDelta,
        double cvDelta,
        int weekendDelta,
        int consecutiveDelta,
        int gapDelta,
        double giniDelta
) {
    /** Empty delta — all zeros. */
    public static ScoreDelta zero() {
        return new ScoreDelta(0, 0, 0, 0, 0, 0, 0);
    }

    /** Combine two deltas (sum each field). */
    public ScoreDelta plus(ScoreDelta other) {
        return new ScoreDelta(
                this.hardDelta + other.hardDelta,
                this.coverageDelta + other.coverageDelta,
                this.cvDelta + other.cvDelta,
                this.weekendDelta + other.weekendDelta,
                this.consecutiveDelta + other.consecutiveDelta,
                this.gapDelta + other.gapDelta,
                this.giniDelta + other.giniDelta);
    }

    /** Negate every field. */
    public ScoreDelta negated() {
        return new ScoreDelta(
                -hardDelta,
                -coverageDelta,
                -cvDelta,
                -weekendDelta,
                -consecutiveDelta,
                -gapDelta,
                -giniDelta);
    }
}