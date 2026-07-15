package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;

/**
 * Placeholder interface for incremental statistics hub.
 * Will be implemented in Phase 3.
 */
public interface IncrementalStatisticsHubInterface {
    
    void apply(com.hospital.scheduler.scheduling.move.Move move, 
                com.hospital.scheduler.scheduling.solution.WorkingSolution solution);
    
    void undo(com.hospital.scheduler.scheduling.move.Move move,
              com.hospital.scheduler.scheduling.solution.WorkingSolution solution);
    
    void reset(com.hospital.scheduler.scheduling.solution.WorkingSolution solution);
    
    double getCV();
    
    int getGap();
    
    double getMean();
    
    int getTotalShifts();
}
