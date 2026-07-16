package com.hospital.scheduler.scheduling.adaptive;

/**
 * Generic perturbation that cycles a {@link Runnable} through a fixed list of
 * values. Used as a building block for the perturbations documented in the
 * roadmap (tabu tenure, SA reheat, candidate-list shrink/expand).
 */
public class CyclicValuePerturbation implements AdaptiveController.AdaptivePerturbation {

    private final String name;
    private final int[] cycle;
    private final Runnable setter;
    private final Runnable resetter;
    private int cursor = 0;

    public CyclicValuePerturbation(String name, int[] cycle, Runnable setter, Runnable resetter) {
        this.name = name;
        this.cycle = cycle;
        this.setter = setter;
        this.resetter = resetter;
    }

    @Override
    public String name() { return name; }

    @Override
    public void apply() {
        if (cycle.length == 0) return;
        int v = cycle[cursor % cycle.length];
        cursor++;
        // Lambdas bind by reference at construction time; we use a setter that
        // accepts a primitive int via a small wrapper.
        if (setter instanceof IntSetter is) {
            is.set(v);
        } else {
            setter.run();
        }
    }

    @Override
    public void reset() {
        if (resetter != null) resetter.run();
        cursor = 0;
    }

    /** Helper interface so the perturbation can pass new int values to lambdas. */
    public interface IntSetter {
        void set(int value);
    }
}
