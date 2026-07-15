package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ObjectiveScore;

/**
 * Interface for move acceptors.
 * 
 * <p>Determines whether a move should be accepted based on
 * the current, proposed, and best scores.</p>
 */
public interface MoveAcceptor {

    /**
     * Decide whether to accept a move.
     */
    AcceptResult shouldAccept(Move move, ObjectiveScore current, 
                            ObjectiveScore proposed, ObjectiveScore best);

    /**
     * Result of accept decision.
     */
    record AcceptResult(boolean accept, String reason) {
        public static AcceptResult accept(String reason) {
            return new AcceptResult(true, reason);
        }

        public static AcceptResult reject(String reason) {
            return new AcceptResult(false, reason);
        }

        public boolean accept() {
            return accept;
        }

        public String reason() {
            return reason;
        }
    }
}
