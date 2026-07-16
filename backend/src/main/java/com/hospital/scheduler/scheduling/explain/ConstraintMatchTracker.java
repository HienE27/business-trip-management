package com.hospital.scheduler.scheduling.explain;

import com.hospital.scheduler.scheduling.move.Move;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Tracks per-constraint match counts during a search run.
 *
 * <p>The search loop calls {@link #recordAccepted(Move, AssignmentExplanation)}
 * after every accepted move. The buffer is bounded so memory stays predictable
 * even on long runs.
 */
public class ConstraintMatchTracker {

    /** Ring buffer capacity (last N accepted moves retained). */
    private static final int RING_CAPACITY = 10_000;

    private final Deque<AssignmentExplanation> ring = new ArrayDeque<>(RING_CAPACITY);

    /** Record an explanation tied to an accepted move. */
    public void recordAccepted(Move move, AssignmentExplanation explanation) {
        if (ring.size() >= RING_CAPACITY) {
            ring.pollFirst();
        }
        ring.offerLast(explanation);
    }

    /** Most-recent first. */
    public List<AssignmentExplanation> recent(int max) {
        int n = Math.min(max, ring.size());
        List<AssignmentExplanation> out = new ArrayList<>(n);
        var it = ring.descendingIterator();
        for (int i = 0; i < n && it.hasNext(); i++) {
            out.add(it.next());
        }
        return Collections.unmodifiableList(out);
    }

    public int size() {
        return ring.size();
    }
}