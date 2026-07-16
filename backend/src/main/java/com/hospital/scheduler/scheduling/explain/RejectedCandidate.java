package com.hospital.scheduler.scheduling.explain;

import lombok.Builder;
import lombok.Getter;

/**
 * Why a particular staff candidate was not chosen for a slot.
 */
@Getter
@Builder
public class RejectedCandidate {

    private final int staffId;
    private final String reason;

    /** Specific constraint that excluded this candidate, if any. */
    private final String blockingConstraintId;
}