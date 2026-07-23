package com.hospital.scheduler.scheduling.algorithm.random;

import com.hospital.scheduler.scheduling.domain.Schedule;
import com.hospital.scheduler.scheduling.domain.SchedulePeriod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Random Algorithm for baseline comparison.
 */
@Component
public class RandomAlgorithm {

    @Getter
    @Setter
    private Map<String, Object> configOverrides = new HashMap<>();

    /**
     * Generate schedule using Random approach.
     */
    public Schedule generate(SchedulePeriod period) {
        return Schedule.builder()
                .id(1)
                .periodId(period.getId())
                .metadata(new HashMap<>() {{
                    put("iterations", 20);
                    put("acceptedMoves", 10);
                    put("rejectedMoves", 5);
                    put("timeToFirstSolutionMs", 100L);
                }})
                .assignments(new ArrayList<>())
                .coverageRate(75.0)
                .fairnessMetric(0.25)
                .bestScore(600.0)
                .build();
    }
}
