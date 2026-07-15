package com.hospital.scheduler.scheduling.score;

import java.io.Serializable;

/**
 * Immutable snapshot of a score at a point in time.
 * 
 * <p>Used to restore previous score states and track best scores.</p>
 */
public final class ScoreSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int hardViolations;
    private final double coverage;
    private final double cvTotal;
    private final double cvWeekend;
    private final int softViolations;
    private final int gap;
    private final double gini;
    private final double mad;
    private final double totalScore;

    public ScoreSnapshot(int hardViolations, double coverage, double cvTotal,
                        double cvWeekend, int softViolations, int gap,
                        double gini, double mad, double totalScore) {
        this.hardViolations = hardViolations;
        this.coverage = coverage;
        this.cvTotal = cvTotal;
        this.cvWeekend = cvWeekend;
        this.softViolations = softViolations;
        this.gap = gap;
        this.gini = gini;
        this.mad = mad;
        this.totalScore = totalScore;
    }

    /**
     * Create snapshot from MutableScore.
     */
    public static ScoreSnapshot from(MutableScore score) {
        return new ScoreSnapshot(
                score.hardViolations(),
                score.coverage(),
                score.cvTotal(),
                score.cvWeekend(),
                score.softViolations(),
                score.gap(),
                score.gini(),
                score.mad(),
                score.totalScore()
        );
    }

    /**
     * Create an empty snapshot.
     */
    public static ScoreSnapshot empty() {
        return new ScoreSnapshot(0, 0, 1.0, 1.0, 0, 0, 0, 0, 0);
    }

    // Getters
    public int hardViolations() { return hardViolations; }
    public double coverage() { return coverage; }
    public double cvTotal() { return cvTotal; }
    public double cvWeekend() { return cvWeekend; }
    public int softViolations() { return softViolations; }
    public int gap() { return gap; }
    public double gini() { return gini; }
    public double mad() { return mad; }
    public double totalScore() { return totalScore; }

    public boolean isFeasible() {
        return hardViolations == 0;
    }

    public boolean isBetterThan(ScoreSnapshot other) {
        if (other == null) return true;
        return totalScore > other.totalScore;
    }

    @Override
    public String toString() {
        return String.format("ScoreSnapshot[hard=%d, cov=%.1f%%, cv=%.2f%%, soft=%d, gap=%d, score=%.2f]",
                hardViolations, coverage, cvTotal * 100, softViolations, gap, totalScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreSnapshot that = (ScoreSnapshot) o;
        return hardViolations == that.hardViolations &&
                Double.compare(that.coverage, coverage) == 0 &&
                Double.compare(that.cvTotal, cvTotal) == 0 &&
                softViolations == that.softViolations;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hardViolations, coverage, cvTotal, softViolations);
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int hardViolations = 0;
        private double coverage = 0;
        private double cvTotal = 1.0;
        private double cvWeekend = 1.0;
        private int softViolations = 0;
        private int gap = 0;
        private double gini = 0;
        private double mad = 0;
        private double totalScore = 0;

        public Builder hardViolations(int v) { this.hardViolations = v; return this; }
        public Builder coverage(double v) { this.coverage = v; return this; }
        public Builder cvTotal(double v) { this.cvTotal = v; return this; }
        public Builder cvWeekend(double v) { this.cvWeekend = v; return this; }
        public Builder softViolations(int v) { this.softViolations = v; return this; }
        public Builder gap(int v) { this.gap = v; return this; }
        public Builder gini(double v) { this.gini = v; return this; }
        public Builder mad(double v) { this.mad = v; return this; }
        public Builder totalScore(double v) { this.totalScore = v; return this; }

        public ScoreSnapshot build() {
            return new ScoreSnapshot(hardViolations, coverage, cvTotal, cvWeekend,
                    softViolations, gap, gini, mad, totalScore);
        }
    }
}
