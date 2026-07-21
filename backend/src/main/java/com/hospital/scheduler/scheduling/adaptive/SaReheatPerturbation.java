package com.hospital.scheduler.scheduling.adaptive;

import java.util.function.DoubleSupplier;

/**
 * Resets an SA temperature back to its initial value. Used when stagnation is
 * detected mid-run to reheat the search.
 */
public class SaReheatPerturbation implements AdaptiveController.AdaptivePerturbation {

    private final DoubleSupplier temperatureReader;
    private final java.util.function.DoubleConsumer temperatureWriter;
    private final double initialTemperature;

    public SaReheatPerturbation(DoubleSupplier temperatureReader,
                                 java.util.function.DoubleConsumer temperatureWriter,
                                 double initialTemperature) {
        this.temperatureReader = temperatureReader;
        this.temperatureWriter = temperatureWriter;
        this.initialTemperature = initialTemperature;
    }

    @Override
    public String name() { return "SA reheat"; }

    @Override
    public void apply() {
        // ignore current temperature — always reheat to T0
        temperatureWriter.accept(initialTemperature);
    }

    @Override
    public void reset() {
        temperatureWriter.accept(initialTemperature);
    }
}
