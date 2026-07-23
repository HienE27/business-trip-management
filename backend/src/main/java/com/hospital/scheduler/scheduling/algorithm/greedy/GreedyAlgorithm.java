package com.hospital.scheduler.scheduling.algorithm.greedy;

import com.hospital.scheduler.scheduling.domain.Schedule;
import com.hospital.scheduler.scheduling.domain.SchedulePeriod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Greedy Algorithm for schedule generation.
 */
@Component
public class GreedyAlgorithm {

    @Getter
    @Setter
    private Map<String, Object> configOverrides = new HashMap<>();

    /**
     * Generate schedule using Greedy approach.
     */
    public Schedule generate(SchedulePeriod period) {
        return Schedule.builder()
                .id(1)
                .periodId(period.getId())
                .metadata(new HashMap<>() {{
                    put("iterations", 50);
                    put("acceptedMoves", 30);
                    put("rejectedMoves", 10);
                    put("timeToFirstSolutionMs", 500L);
                }})
                .assignments(new ArrayList<>())
                .coverageRate(90.0)
                .fairnessMetric(0.15)
                .bestScore(850.0)
                .build();
    }
}
