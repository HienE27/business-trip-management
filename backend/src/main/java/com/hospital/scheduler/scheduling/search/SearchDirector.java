package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.event.InMemorySearchEventPublisher;
import com.hospital.scheduler.scheduling.event.NullSearchEventPublisher;
import com.hospital.scheduler.scheduling.event.SearchEvent;
import com.hospital.scheduler.scheduling.event.SearchEventPublisher;
import com.hospital.scheduler.scheduling.event.SearchEventType;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the search process.
 *
 * <p>Owns the live {@link SearchState}, the {@link ScoreDirector}, and the
 * {@link IncrementalStatisticsHub}. The {@link LocalSearchAlgorithm} reads
 * state from here and writes back results.
 */
@Slf4j
@Getter
public class SearchDirector {

    private final SearchState state = new SearchState();
    private WorkingSolution bestSolution;
    private ScoreSnapshot bestScore;

    /**
     * BUGFIX (M08-BALANCE-V10): per-staff L01/L02/L03 mix deviation of the
     * stored best solution. The acceptance rule now treats coverage-flat
     * mix-improving moves as "improving", so onNewBest must not blindly
     * overwrite the best — a mix-up move applied after coverage-dropping
     * sideways churn would otherwise record a WORSE solution as the new best.
     * Best is replaced only when coverage strictly rises, or coverage is equal
     * and the mix deviation narrowed.
     */
    private double bestMixDeviation = Double.MAX_VALUE;

    private final ScoreDirector scoreDirector;
    private final IncrementalStatisticsHub statisticsHub;
    private final SearchEventPublisher eventPublisher;
    private String runId = "no-run";

    public SearchDirector(ScoreDirector scoreDirector,
                          IncrementalStatisticsHub statisticsHub) {
        this(scoreDirector, statisticsHub, NullSearchEventPublisher.INSTANCE);
    }

    public SearchDirector(ScoreDirector scoreDirector,
                          IncrementalStatisticsHub statisticsHub,
                          SearchEventPublisher eventPublisher) {
        this.scoreDirector = scoreDirector;
        this.statisticsHub = statisticsHub;
        this.eventPublisher = eventPublisher != null ? eventPublisher : NullSearchEventPublisher.INSTANCE;
    }

    /** Set the run id used to tag every published event. */
    public void setRunId(String runId) {
        this.runId = runId != null ? runId : "no-run";
    }

    /** Called by the algorithm when a new best score is found. */
    public void onNewBest(WorkingSolution solution) {
        // BUGFIX (M08-BALANCE-V10): gate — only replace the best when this
        // solution genuinely beats it (coverage up, or equal coverage with a
        // narrower L01/L02/L03 mix deviation). Coverage is read from the
        // solution itself (bestSolution), not from the score snapshot, which
        // only refreshes on recomputeFull() and would pin coverage at the
        // initial-greedy value.
        double cov = solution.getCoverage();
        double mix = solution.mixDeviation();
        if (bestSolution != null && bestScore != null) {
            double bestCov = bestSolution.getCoverage();
            double bestMix = bestMixDeviation;
            if (cov < bestCov - 1e-9) {
                return; // strictly worse coverage — not a new best
            }
            if (Math.abs(cov - bestCov) <= 1e-9 && mix >= bestMix) {
                return; // equal coverage, not better mix — not a new best
            }
            // FIX (plateau-bug): uphill move (cov improved) with worse mix should
            // NOT overwrite best. Previously this edge case was missed: bestCov=0.9935
            // mix=31.0 was overwritten by current with cov=0.9968 mix=43.6, because
            // cov > bestCov fell through both gates and bestMix was overwritten.
            if (cov > bestCov + 1e-9 && mix > bestMix) {
                return; // uphill on coverage but regressed on mix — not a new best
            }
        }
        bestMixDeviation = mix;
        // Deep-copy via toImmutable on each assignment
        this.bestSolution = copySolution(solution);
        this.bestScore = scoreDirector.getCurrent().toImmutable();
        ScoreSnapshot previousBest = state.getBestScore();
        state.setBestScore(bestScore);
        state.resetNoImprove();
        if (previousBest != null) {
            publish(SearchEventType.SCORE_IMPROVED, "improvement", 0, 0.0);
        }
    }

    /** Update the current score (called every iteration). */
    public void onIteration(WorkingSolution solution) {
        state.setCurrentScore(scoreDirector.getCurrent().toImmutable());
        publish(SearchEventType.ITERATION, "iteration", 0, 0.0);
    }

    /** Track a no-improvement iteration. */
    public void onNoImprove() {
        state.incrementNoImprove();
        // Diversification signal — too many no-improve iterations
        if (state.getNoImproveIterations() > 0
                && state.getNoImproveIterations() % 50 == 0) {
            publish(SearchEventType.DIVERSIFIED, "stagnation", 0, 0.0);
        }
    }

    /** Track an accepted move. */
    public void onAccepted() {
        state.incrementAccepted();
        publish(SearchEventType.MOVE_ACCEPTED, "move", 0, 0.0);
    }

    /** Track a rejected move. */
    public void onRejected() {
        state.incrementRejected();
        publish(SearchEventType.MOVE_REJECTED, "move", 0, 0.0);
    }

    /** Track a tabu rejection — separate event for live tabu-hit rate chart. */
    public void onTabuHit() {
        publish(SearchEventType.TABU_HIT, "tabu", 0, 0.0);
    }

    private void publish(SearchEventType type, String moveType, int hardDelta, double coverageDelta) {
        try {
            eventPublisher.publish(new SearchEvent(
                    runId,
                    state.getIteration(),
                    state.getElapsedMillis(),
                    type,
                    state.getCurrentScore(),
                    state.getBestScore(),
                    moveType,
                    hardDelta,
                    coverageDelta));
        } catch (RuntimeException ignored) {
            // Telemetry must never break the search loop
        }
    }

    /** Deep-copy a {@link WorkingSolution} so we can keep the best one. */
    private WorkingSolution copySolution(WorkingSolution source) {
        WorkingSolution copy = WorkingSolution.fromProblem(
                source.getConfig(), source.getDescriptor());
        for (var a : source.getAssignments()) {
            if (a.staffId > 0) {
                copy.assign(a.slotId, a.staffId);
            }
        }
        return copy;
    }

    /** Convenience — create a director with a unique run id. */
    public static SearchDirector withRunId(ScoreDirector score,
                                           IncrementalStatisticsHub hub,
                                           SearchEventPublisher publisher) {
        SearchDirector director = new SearchDirector(score, hub, publisher);
        director.setRunId(InMemorySearchEventPublisher.newRunId());
        return director;
    }
}