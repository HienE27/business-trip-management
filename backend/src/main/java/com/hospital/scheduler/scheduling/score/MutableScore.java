package com.hospital.scheduler.scheduling.score;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;

/**
 * Mutable score object with incremental update support.
 * 
 * <p>Tracks all score components and supports apply/undo operations
 * for efficient local search.</p>
 */
public class MutableScore {

    private final SchedulingConfig config;

    // Score components
    private int hardViolations = 0;
    private double coverage = 0;
    private double cvTotal = 0;
    private double cvWeekend = 0;
    private int softViolations = 0;
    private int gap = 0;
    private double gini = 0;
    private double mad = 0;

    // Total score (computed)
    private double totalScore = 0;

    public MutableScore() {
        this(new SchedulingConfig());
    }

    public MutableScore(SchedulingConfig config) {
        this.config = config;
        recomputeTotalScore();
    }

    /**
     * Apply a delta to this score.
     */
    public void applyDelta(ScoreDelta delta) {
        if (delta == null || delta == ScoreDelta.ZERO) return;

        hardViolations += delta.hardDelta();
        coverage = clamp(coverage + delta.coverageDelta(), 0, 100);
        cvTotal = clamp(cvTotal + delta.cvDelta(), 0, 10);
        cvWeekend = clamp(cvWeekend + delta.cvWeekendDelta(), 0, 10);
        softViolations += delta.softDelta();
        gap = Math.max(0, gap + delta.gapDelta());
        gini = clamp(gini + delta.giniDelta(), 0, 1);

        recomputeTotalScore();
    }

    /**
     * Undo a delta from this score.
     */
    public void undoDelta(ScoreDelta delta) {
        if (delta == null || delta == ScoreDelta.ZERO) return;
        applyDelta(delta.negate());
    }

    /**
     * Reset to snapshot state.
     */
    public void reset(ScoreSnapshot snapshot) {
        this.hardViolations = snapshot.hardViolations();
        this.coverage = snapshot.coverage();
        this.cvTotal = snapshot.cvTotal();
        this.cvWeekend = snapshot.cvWeekend();
        this.softViolations = snapshot.softViolations();
        this.gap = snapshot.gap();
        this.gini = snapshot.gini();
        this.mad = snapshot.mad();
        this.totalScore = snapshot.totalScore();
    }

    /**
     * Compare to another score using lexicographic ordering.
     */
    public int compareTo(MutableScore other) {
        if (other == null) return 1;

        // Lexicographic comparison:
        // 1. Hard violations (less is better)
        if (this.hardViolations != other.hardViolations) {
            return Integer.compare(this.hardViolations, other.hardViolations);
        }

        // 2. Coverage (more is better)
        if (Math.abs(this.coverage - other.coverage) > 0.0001) {
            return Double.compare(other.coverage, this.coverage);
        }

        // 3. CV (less is better)
        if (Math.abs(this.cvTotal - other.cvTotal) > 0.0001) {
            return Double.compare(this.cvTotal, other.cvTotal);
        }

        // 4. Soft violations (less is better)
        if (this.softViolations != other.softViolations) {
            return Integer.compare(this.softViolations, other.softViolations);
        }

        // 5. Gap (less is better)
        if (this.gap != other.gap) {
            return Integer.compare(this.gap, other.gap);
        }

        // 6. Total score (more is better)
        return Double.compare(this.totalScore, other.totalScore);
    }

    /**
     * Compare to immutable ObjectiveScore.
     */
    public int compareTo(ObjectiveScore other) {
        if (other == null) return 1;

        if (this.hardViolations != other.hardViolations()) {
            return Integer.compare(this.hardViolations, other.hardViolations());
        }
        if (Math.abs(this.coverage - other.coverage()) > 0.0001) {
            return Double.compare(other.coverage(), this.coverage);
        }
        if (Math.abs(this.cvTotal - other.cvTotal()) > 0.0001) {
            return Double.compare(this.cvTotal, other.cvTotal());
        }
        if (this.softViolations != other.softViolations()) {
            return Integer.compare(this.softViolations, other.softViolations());
        }
        return Double.compare(this.totalScore, other.totalScore());
    }

    /**
     * Convert to immutable ObjectiveScore.
     */
    public ObjectiveScore toImmutable() {
        return new ObjectiveScore(
                hardViolations, coverage, cvTotal, cvWeekend,
                softViolations, gap, gini, mad
        );
    }

    /**
     * Convert to snapshot.
     */
    public ScoreSnapshot toSnapshot() {
        return ScoreSnapshot.builder()
                .hardViolations(hardViolations)
                .coverage(coverage)
                .cvTotal(cvTotal)
                .cvWeekend(cvWeekend)
                .softViolations(softViolations)
                .gap(gap)
                .gini(gini)
                .mad(mad)
                .totalScore(totalScore)
                .build();
    }

    /**
     * Check if score represents a feasible solution.
     */
    public boolean isFeasible() {
        return hardViolations == 0;
    }

    /**
     * Check if score meets quality threshold.
     */
    public boolean passesThreshold() {
        return totalScore >= 80.0;
    }

    // Setters for full score calculation
    public void setHardViolations(int v) { this.hardViolations = v; recomputeTotalScore(); }
    public void setCoverage(double v) { this.coverage = clamp(v, 0, 100); recomputeTotalScore(); }
    public void setCvTotal(double v) { this.cvTotal = clamp(v, 0, 10); recomputeTotalScore(); }
    public void setCvWeekend(double v) { this.cvWeekend = clamp(v, 0, 10); recomputeTotalScore(); }
    public void setSoftViolations(int v) { this.softViolations = v; recomputeTotalScore(); }
    public void setGap(int v) { this.gap = Math.max(0, v); recomputeTotalScore(); }
    public void setGini(double v) { this.gini = clamp(v, 0, 1); recomputeTotalScore(); }
    public void setMad(double v) { this.mad = v; recomputeTotalScore(); }

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

    private void recomputeTotalScore() {
        double wCov = config.getFairness().getCoverageWeight();
        double wFair = config.getFairness().getFairnessWeight();
        double wCon = config.getFairness().getConstraintWeight();

        // Coverage score (already 0-100)
        double coverageScore = coverage;

        // Fairness score (100 - cv * 100, capped at 0-100)
        double fairnessScore = Math.max(0, Math.min(100, 100 - cvTotal * 100));

        // Constraint score (100 - penalties)
        double constraintPenalty = hardViolations * 25.0 + softViolations * 5.0;
        double constraintScore = Math.max(0, 100 - constraintPenalty);

        totalScore = wCov * coverageScore + wFair * fairnessScore + wCon * constraintScore;
        totalScore = clamp(totalScore, 0, 100);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String toString() {
        return String.format("MutableScore[hard=%d, cov=%.1f%%, cv=%.2f%%, soft=%d, gap=%d, total=%.2f]",
                hardViolations, coverage, cvTotal * 100, softViolations, gap, totalScore);
    }
}

/**
 * Immutable objective score for external use.
 */
public class ObjectiveScore {

    private final int hardViolations;
    private final double coverage;
    private final double cvTotal;
    private final double cvWeekend;
    private final int softViolations;
    private final int gap;
    private final double gini;
    private final double mad;

    public ObjectiveScore(int hardViolations, double coverage, double cvTotal,
                        double cvWeekend, int softViolations, int gap,
                        double gini, double mad) {
        this.hardViolations = hardViolations;
        this.coverage = coverage;
        this.cvTotal = cvTotal;
        this.cvWeekend = cvWeekend;
        this.softViolations = softViolations;
        this.gap = gap;
        this.gini = gini;
        this.mad = mad;
    }

    public int hardViolations() { return hardViolations; }
    public double coverage() { return coverage; }
    public double cvTotal() { return cvTotal; }
    public double cvWeekend() { return cvWeekend; }
    public int softViolations() { return softViolations; }
    public int gap() { return gap; }
    public double gini() { return gini; }
    public double mad() { return mad; }
}
