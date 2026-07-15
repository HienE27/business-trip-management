package com.hospital.scheduler.scheduling.score;

import lombok.Getter;

/**
 * Mutable score object for incremental updates.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code hardViolations} — count of hard-constraint violations (BR-01..05). Higher = worse.</li>
 *   <li>{@code coverage} — fraction of required slots filled. Higher = better.</li>
 *   <li>{@code cvTotal} — coefficient of variation across all staff. Lower = fairer.</li>
 *   <li>{@code cvWeekend} — same, but only for weekend shifts. Lower = fairer.</li>
 *   <li>{@code weekendGap} — max-min spread of weekend shifts per staff. Lower = fairer.</li>
 *   <li>{@code consecutiveGap} — max consecutive days over the limit (BR-04). Lower = better.</li>
 *   <li>{@code gap} — overall shift-count gap. Lower = fairer.</li>
 *   <li>{@code gini} — Gini coefficient. Lower = fairer.</li>
 * </ul>
 *
 * <p>Lexicographic ordering: hard violations first (must equal 0), then coverage,
 * then fairness fields in order.
 */
@Getter
public class MutableScore {

    private int hardViolations = 0;
    private double coverage = 0;
    private double cvTotal = 0;
    private double cvWeekend = 0;
    private int weekendGap = 0;
    private int consecutiveGap = 0;
    private int gap = 0;
    private double gini = 0;

    /** Apply a delta — adds each field. */
    public void applyDelta(ScoreDelta delta) {
        this.hardViolations += delta.hardDelta();
        this.coverage += delta.coverageDelta();
        this.cvTotal += delta.cvDelta();
        this.weekendGap += delta.weekendDelta();
        this.consecutiveGap += delta.consecutiveDelta();
        this.gap += delta.gapDelta();
        this.gini += delta.giniDelta();
    }

    /** Undo a delta — subtracts each field. */
    public void undoDelta(ScoreDelta delta) {
        applyDelta(delta.negated());
    }

    /** Reset every field. */
    public void reset() {
        hardViolations = 0;
        coverage = 0;
        cvTotal = 0;
        cvWeekend = 0;
        weekendGap = 0;
        consecutiveGap = 0;
        gap = 0;
        gini = 0;
    }

    /** Snapshot to an immutable value. */
    public ScoreSnapshot toImmutable() {
        return new ScoreSnapshot(
                hardViolations, coverage, cvTotal, cvWeekend,
                weekendGap, consecutiveGap, gap, gini);
    }

    /** Direct setter for cvWeekend (used by ScoreDirector during full recompute). */
    public void setCvWeekend(double v) {
        this.cvWeekend = v;
    }

    /**
     * Lexicographic comparison. Returns negative if this is BETTER than other.
     * Order: hardViolations ↑, coverage ↓, cvTotal ↑, cvWeekend ↑, gap ↑, gini ↑.
     */
    public int compareTo(MutableScore other) {
        int c;
        c = Integer.compare(this.hardViolations, other.hardViolations);
        if (c != 0) return c;
        c = Double.compare(other.coverage, this.coverage); // higher coverage is better
        if (c != 0) return c;
        c = Double.compare(this.cvTotal, other.cvTotal);
        if (c != 0) return c;
        c = Double.compare(this.cvWeekend, other.cvWeekend);
        if (c != 0) return c;
        c = Integer.compare(this.gap, other.gap);
        if (c != 0) return c;
        c = Double.compare(this.gini, other.gini);
        if (c != 0) return c;
        return Integer.compare(this.consecutiveGap, other.consecutiveGap);
    }
}