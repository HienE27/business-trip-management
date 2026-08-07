package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.move.Move;

import java.util.List;
import java.util.Map;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Main search loop — coordinates move selection, evaluation, acceptance,
 * application, and termination.
 *
 * <p>Algorithm:
 * <pre>
 *   1. Initialize: build the working solution, recompute full score
 *   2. Repeat until terminated:
 *      a. Select candidates
 *      b. For each candidate, doMove, evaluate delta, decide accept
 *      c. If accepted: update stats, score; else: undo
 *   3. Return best solution
 * </pre>
 */
@Slf4j
@Getter
public class LocalSearchAlgorithm {

    private final SchedulingConfig config;
    private final MoveSelector moveSelector;
    private final MoveAcceptor moveAcceptor;
    private final Termination termination;
    private final SearchDirector director;
    private final ScoreDirector scoreDirector;
    private final ConstraintRegistry constraintRegistry;
    private final IncrementalStatisticsHub statisticsHub;

    public LocalSearchAlgorithm(SchedulingConfig config,
                                 MoveSelector moveSelector,
                                 MoveAcceptor moveAcceptor,
                                 Termination termination,
                                 SearchDirector director,
                                 ScoreDirector scoreDirector,
                                 ConstraintRegistry constraintRegistry,
                                 IncrementalStatisticsHub statisticsHub) {
        this.config = config;
        this.moveSelector = moveSelector;
        this.moveAcceptor = moveAcceptor;
        this.termination = termination;
        this.director = director;
        this.scoreDirector = scoreDirector;
        this.constraintRegistry = constraintRegistry;
        this.statisticsHub = statisticsHub;
    }

    /**
     * Run the search starting from {@code initial}.
     */
    public SearchResult search(WorkingSolution initial) {
        WorkingSolution current = initial;

        scoreDirector.recomputeFull(current);

        // Populate hardViolations from constraint registry so the hard-fence check
        // in processMove() has an accurate baseline. recomputeFull() only sets
        // coverage/fairness; hard constraints are evaluated here.
        ScoreDelta initialConstraintDelta = ScoreDelta.zero();
        for (Constraint c : constraintRegistry.all()) {
            initialConstraintDelta = initialConstraintDelta.plus(c.evaluate(current));
        }
        scoreDirector.applyDelta(initialConstraintDelta);

        director.onNewBest(current);
        director.onIteration(current);
        log.info("v10 search starting: {} slots, {} staff, score={}",
                current.getAssignments().size(),
                current.getDescriptor().staffCount(),
                scoreDirector.getCurrent().toImmutable());

        while (!director.getState().isTerminated()
                && !termination.isTerminated(director.getState())) {
            director.getState().incrementIteration();
            int batchSize = config.getSearch().getCandidateListSize();
            List<Move> candidates = moveSelector.select(current, batchSize);

            int acceptedThisIteration = 0;
            for (Move move : candidates) {
                if (processMove(current, move)) {
                    acceptedThisIteration++;
                }
            }
            director.onIteration(current);

            if (acceptedThisIteration == 0) {
                director.onNoImprove();
            }
        }

        SearchResult result = new SearchResult();
        result.solution = director.getBestSolution();
        result.score = director.getBestScore();
        result.iterations = director.getState().getIteration();
        result.elapsedMillis = director.getState().getElapsedMillis();
        result.terminationReason = director.getState().getTerminationReason();
        result.acceptedMoves = director.getState().getAcceptedMoves();
        result.rejectedMoves = director.getState().getRejectedMoves();
        log.info("v10 search finished: reason={}, iters={}, elapsed={}ms",
                result.terminationReason, result.iterations, result.elapsedMillis);
        return result;
    }

    /**
     * Process a single move: try it, evaluate delta, decide.
     * Returns true if accepted.
     *
     * <p>Decision rules:
     * <ol>
     *   <li><b>Hard violations MUST NEVER increase.</b> A move that increases
     *       {@code hardViolations} (BR-01..05: L01↔L02 same day, L03↔L04 same
     *       day, conflict with compensation, conflict with approved leave,
     *       adjacent L01) is always rejected regardless of the soft
     *       acceptance policy. Hard constraints are non-negotiable business
     *       rules and the search must not be allowed to escape them.</li>
     *   <li>If hard is unchanged, improving = coverage went up (or the
     *       per-type balance gap narrowed at flat coverage). Improving
     *       moves are always accepted (aspiration).</li>
     *   <li>If neither (no improvement, no violation), the soft acceptance
     *       policy decides. For tabu the {@link MoveAcceptor#isTabu} gate
     *       is consulted first; otherwise the acceptor decides uphill
     *       moves (SA / LAP / GreatDeluge / VNS).</li>
     * </ol>
     */
    private boolean processMove(WorkingSolution solution, Move move) {
        // Snapshot pre-move score for "improving" check
        int preHard = scoreDirector.getCurrent().toImmutable().getHardViolations();
        // BUGFIX (M08-EXPAND-V10): coverage must come from the solution itself.
        // scoreDirector.getCurrent().getCoverage() is only set once in
        // recomputeFull() — constraint deltas carry coverageDelta=0 — so the
        // old postCoverage > preCoverage check was always false and the search
        // could never accept an AssignMove (it only dropped coverage). Reading
        // solution.getCoverage() makes assigning a slot genuinely "improving".
        double preCoverage = solution.getCoverage();
        // BUGFIX (M08-BALANCE-V10): soft per-type MIX fairness tiebreak — a
        // move that keeps coverage flat but narrows each staff's L01/L02/L03
        // deviation from the average mix is "improving" too, so the search
        // actively rebalances the mix per staff instead of only top-up filling.
        double preBalance = solution.mixDeviation();

        // BUGFIX (2026-08-03): constraints evaluate ABSOLUTE counts, not
        // deltas. Adding a full re-evaluation to the accumulated score
        // double-counts when the initial solution already has hard violations
        // (current.hard=5 → post = 5 + 5 = 10 > 5 → EVERY move rejected → the
        // search froze and could never repair a dirty initial solution).
        // Evaluate before AND after the move and apply the true difference.
        ScoreDelta preDelta = evaluateConstraints(solution);

        // Apply move + statistics
        move.doMove(solution);
        statisticsHub.apply(move, solution);

        // Compute delta for undo (used by RULE 0 and the hard-fence)
        ScoreDelta delta = evaluateConstraints(solution).minus(preDelta);
        scoreDirector.applyDelta(delta);

        // RULE 0 (Greedy preservation — L01/L02/L03 lock): The initial solution from
        // Greedy already has even L01/L02/L03 per-staff distribution (Tier 1-3 of its 7-tier
        // comparator guarantee). The search is ONLY allowed to improve L04 slots.
        // Any ASSIGN or CHANGE_STAFF that would alter an L01/L02/L03 slot would
        // undo Greedy's fairness work and concentrate those types on a subset of staff
        // (the exact problem this fix targets: V10 search degraded mixDeviation from 0 → 43.6
        // because assign moves freely re-roled L01/L02/L03 staff). L04 is the residual
        // buffer type (M07-B3 "L04 gets residual capacity") — search rebalancing there
        // does not break Greedy's L01/L02/L03 fairness.
        if (move.type() == Move.MoveType.ASSIGN
                || move.type() == Move.MoveType.CHANGE_STAFF) {
            int slotId = -1;
            if (move.type() == Move.MoveType.ASSIGN) {
                slotId = ((com.hospital.scheduler.scheduling.move.AssignMove) move).slotId();
            } else {
                slotId = ((com.hospital.scheduler.scheduling.move.ChangeStaffMove) move).slotId();
            }
            if (slotId > 0) {
                var ma = solution.getAssignment(slotId);
                if (ma != null && ma.shiftTypeId != null
                        && ("L01".equals(ma.shiftTypeId)
                            || "L02".equals(ma.shiftTypeId)
                            || "L03".equals(ma.shiftTypeId))) {
                    // Undo immediately — L01/L02/L03 slots are locked by Greedy's initial solution.
                    move.undo(solution);
                    statisticsHub.undo(move, solution);
                    scoreDirector.undoDelta(delta);
                    director.onRejected();
                    return false;
                }
            }
        }

        // Decide
        int postHard = scoreDirector.getCurrent().toImmutable().getHardViolations();
        double postCoverage = solution.getCoverage();
        double postBalance = solution.mixDeviation();

        // RULE 1 (hard-fence): never accept a move that grows hard violations.
        // The acceptor exists to escape local optima for soft (fairness)
        // objectives, NOT to allow BR-01..05 violations. This guarantees that
        // the search result satisfies every HARD business rule on exit.
        if (postHard > preHard) {
            // Undo immediately, do not even consult the acceptor
            move.undo(solution);
            statisticsHub.undo(move, solution);
            scoreDirector.undoDelta(delta);
            director.onRejected();
            return false;
        }

        // RULE 2: improving = hard unchanged AND (coverage went up, OR
        // coverage stayed flat AND the per-type balance gap narrowed).
        // Aspiration: always accept. The balance tiebreak must NOT cost
        // coverage — an UnassignMove that drops coverage is still not
        // improving and goes to the soft-acceptance policy (which is why
        // unassign stays L04-only in the selector).
        double coverageDelta = postCoverage - preCoverage;
        boolean coverageUp = postHard == preHard && coverageDelta > 1e-9;
        boolean balanceUp = postHard == preHard
                && Math.abs(coverageDelta) <= 1e-9
                && postBalance < preBalance;
        boolean improving = (postHard < preHard) || coverageUp || balanceUp;

        // RULE 3: for non-improving moves that don't increase hard violations,
        // the soft acceptance policy decides. Tabu gate first (if the
        // acceptor maintains one), then the policy's uphill-or-sideways call.
        int iteration = director.getState().getIteration();
        boolean accept = improving;
        boolean tabu = false;
        if (!improving) {
            if (moveAcceptor.isTabu(move, iteration)) {
                accept = false;
                tabu = true;
            } else {
                accept = moveAcceptor.accept(delta, iteration, improving);
            }
        }

        if (accept) {
            moveAcceptor.rememberApplied(move, iteration);
            director.onAccepted();
            // Check if this is the new best
            if (improving) {
                director.onNewBest(solution);
            } else {
                director.onNoImprove();
            }
            return true;
        } else {
            // Undo
            move.undo(solution);
            statisticsHub.undo(move, solution);
            scoreDirector.undoDelta(delta);
            director.onRejected();
            if (tabu) {
                director.onTabuHit();
            }
            return false;
        }
    }

    /**
     * Absolute constraint evaluation — every {@link Constraint} reports its
     * current violation count for the whole solution.
     */
    private ScoreDelta evaluateConstraints(WorkingSolution solution) {
        ScoreDelta delta = ScoreDelta.zero();
        for (Constraint c : constraintRegistry.all()) {
            ScoreDelta d = c.evaluate(solution);
            delta = delta.plus(d);
        }
        return delta;
    }

    @Getter
    @Setter
    public static class SearchResult {
        private WorkingSolution solution;
        private com.hospital.scheduler.scheduling.score.ScoreSnapshot score;
        private int iterations;
        private long elapsedMillis;
        private String terminationReason;
        private int acceptedMoves;
        private int rejectedMoves;
    }
}