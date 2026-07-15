package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link StatisticsModule} instances. The search loop calls
 * {@link #apply(Move, WorkingSolution)} / {@link #undo(Move, WorkingSolution)}
 * after each accepted move so every module's view of the world stays
 * consistent with the working solution.
 *
 * <p>Default modules wired up by {@link #create(SolutionDescriptor)}:
 * <ul>
 *   <li>{@link LoadStatistics} — shifts per staff</li>
 *   <li>{@link WeekendStatistics} — weekend shifts per staff</li>
 *   <li>{@link ConsecutiveStatistics} — consecutive-day runs</li>
 *   <li>{@link FairnessStatistics} — CV / gap / max-min</li>
 * </ul>
 */
@Getter
public class IncrementalStatisticsHub {

    private final SolutionDescriptor descriptor;
    private final Map<Class<? extends StatisticsModule>, StatisticsModule> modules = new HashMap<>();

    public IncrementalStatisticsHub(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Build a hub with the four default modules wired up.
     */
    public static IncrementalStatisticsHub create(SolutionDescriptor descriptor) {
        IncrementalStatisticsHub hub = new IncrementalStatisticsHub(descriptor);
        hub.register(LoadStatistics.class, new LoadStatistics(descriptor));
        hub.register(WeekendStatistics.class, new WeekendStatistics(descriptor));
        hub.register(ConsecutiveStatistics.class, new ConsecutiveStatistics(descriptor));
        hub.register(FairnessStatistics.class, new FairnessStatistics(descriptor));
        return hub;
    }

    /** Register a module instance under its class. */
    public <T extends StatisticsModule> void register(Class<T> type, T module) {
        modules.put(type, module);
    }

    @SuppressWarnings("unchecked")
    public <T extends StatisticsModule> T get(Class<T> type) {
        return (T) modules.get(type);
    }

    public void apply(Move move, WorkingSolution solution) {
        for (StatisticsModule m : modules.values()) {
            m.apply(move, solution);
        }
    }

    public void undo(Move move, WorkingSolution solution) {
        for (StatisticsModule m : modules.values()) {
            m.undo(move, solution);
        }
    }

    public void reset(WorkingSolution solution) {
        for (StatisticsModule m : modules.values()) {
            m.reset(solution);
        }
    }
}