package com.hospital.scheduler.scheduling.score;

import java.io.Serializable;

/**
 * Immutable score delta for incremental updates.
 * 
 * <p>Represents the change in score components when a move is applied.
 * Used by ScoreDirector to calculate new scores without full recalculation.</p>
 */
public final class ScoreDelta implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final ScoreDelta ZERO = new ScoreDelta(0, 0, 0, 0, 0, 0, 0, 0);

    private final int hardDelta;
    private final double coverageDelta;
    private final double cvDelta;
    private final double cvWeekendDelta;
    private final int softDelta;
    private final int gapDelta;
    private final double giniDelta;

    public ScoreDelta(int hardDelta, double coverageDelta, double cvDelta,
                      double cvWeekendDelta, int softDelta, int gapDelta, double giniDelta) {
        this.hardDelta = hardDelta;
        this.coverageDelta = coverageDelta;
        this.cvDelta = cvDelta;
        this.cvWeekendDelta = cvWeekendDelta;
        this.softDelta = softDelta;
        this.gapDelta = gapDelta;
        this.giniDelta = giniDelta;
    }

    /**
     * Add two deltas together.
     */
    public ScoreDelta add(ScoreDelta other) {
        if (other == ZERO) return this;
        return new ScoreDelta(
                this.hardDelta + other.hardDelta,
                this.coverageDelta + other.coverageDelta,
                this.cvDelta + other.cvDelta,
                this.cvWeekendDelta + other.cvWeekendDelta,
                this.softDelta + other.softDelta,
                this.gapDelta + other.gapDelta,
                this.giniDelta + other.giniDelta
        );
    }

    /**
     * Negate this delta (for undo operations).
     */
    public ScoreDelta negate() {
        return new ScoreDelta(
                -hardDelta,
                -coverageDelta,
                -cvDelta,
                -cvWeekendDelta,
                -softDelta,
                -gapDelta,
                -giniDelta
        );
    }

    /**
     * Scale this delta.
     */
    public ScoreDelta scale(double factor) {
        return new ScoreDelta(
                (int) (hardDelta * factor),
                coverageDelta * factor,
                cvDelta * factor,
                cvWeekendDelta * factor,
                (int) (softDelta * factor),
                (int) (gapDelta * factor),
                giniDelta * factor
        );
    }

    // Getters
    public int hardDelta() { return hardDelta; }
    public double coverageDelta() { return coverageDelta; }
    public double cvDelta() { return cvDelta; }
    public double cvWeekendDelta() { return cvWeekendDelta; }
    public int softDelta() { return softDelta; }
    public int gapDelta() { return gapDelta; }
    public double giniDelta() { return giniDelta; }

    @Override
    public String toString() {
        return String.format("ScoreDelta[hard=%d, cov=%.2f, cv=%.4f, soft=%d, gap=%d]",
                hardDelta, coverageDelta, cvDelta, softDelta, gapDelta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreDelta that = (ScoreDelta) o;
        return hardDelta == that.hardDelta &&
                Double.compare(that.coverageDelta, coverageDelta) == 0 &&
                Double.compare(that.cvDelta, cvDelta) == 0 &&
                Double.compare(that.cvWeekendDelta, cvWeekendDelta) == 0 &&
                softDelta == that.softDelta &&
                gapDelta == that.gapDelta &&
                Double.compare(that.giniDelta, giniDelta) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hardDelta, coverageDelta, cvDelta, cvWeekendDelta,
                softDelta, gapDelta, giniDelta);
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int hardDelta = 0;
        private double coverageDelta = 0;
        private double cvDelta = 0;
        private double cvWeekendDelta = 0;
        private int softDelta = 0;
        private int gapDelta = 0;
        private double giniDelta = 0;

        public Builder hardDelta(int v) { this.hardDelta = v; return this; }
        public Builder coverageDelta(double v) { this.coverageDelta = v; return this; }
        public Builder cvDelta(double v) { this.cvDelta = v; return this; }
        public Builder cvWeekendDelta(double v) { this.cvWeekendDelta = v; return this; }
        public Builder softDelta(int v) { this.softDelta = v; return this; }
        public Builder gapDelta(int v) { this.gapDelta = v; return this; }
        public Builder giniDelta(double v) { this.giniDelta = v; return this; }
        public Builder add(ScoreDelta delta) {
            this.hardDelta += delta.hardDelta;
            this.coverageDelta += delta.coverageDelta;
            this.cvDelta += delta.cvDelta;
            this.cvWeekendDelta += delta.cvWeekendDelta;
            this.softDelta += delta.softDelta;
            this.gapDelta += delta.gapDelta;
            this.giniDelta += delta.giniDelta;
            return this;
        }

        public ScoreDelta build() {
            return new ScoreDelta(hardDelta, coverageDelta, cvDelta, cvWeekendDelta,
                    softDelta, gapDelta, giniDelta);
        }
    }
}
