package com.hospital.scheduler.scheduling.tune;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Lightweight Latin-hypercube sampler for the AutoTuner. Generates a small
 * grid of candidate parameter combinations to try during the warm-up phase.
 */
public class LatinHypercube {

    public static class Sample {
        public final int[] candidateListSize;
        public final int tabuTenure;
        public final double saT0;
        public final double saCooling;
        public final int iterations;

        public Sample(int[] candidateListSize, int tabuTenure, double saT0,
                      double saCooling, int iterations) {
            this.candidateListSize = candidateListSize;
            this.tabuTenure = tabuTenure;
            this.saT0 = saT0;
            this.saCooling = saCooling;
            this.iterations = iterations;
        }
    }

    private final int sampleCount;
    private final long seed;

    public LatinHypercube(int sampleCount, long seed) {
        this.sampleCount = Math.max(1, sampleCount);
        this.seed = seed;
    }

    public List<Sample> sample() {
        Random rng = new Random(seed);
        int[] candidateOptions = { 16, 32, 50, 80, 120 };
        int[] tenureOptions = { 3, 5, 8, 12, 20 };
        double[] t0Options = { 100.0, 300.0, 1000.0, 3000.0, 10000.0 };
        double[] coolingOptions = { 0.95, 0.97, 0.99, 0.995, 0.999 };
        int[] iterOptions = { 200, 400, 600, 1000, 2000 };
        // Shuffle each dimension so the resulting tuples cover the space
        shuffle(candidateOptions, rng);
        shuffle(tenureOptions, rng);
        shuffle(t0Options, rng);
        shuffle(coolingOptions, rng);
        shuffle(iterOptions, rng);
        return Arrays.asList(
                new Sample(new int[]{candidateOptions[0]}, tenureOptions[0], t0Options[0],
                        coolingOptions[0], iterOptions[0]),
                new Sample(new int[]{candidateOptions[1]}, tenureOptions[1], t0Options[1],
                        coolingOptions[1], iterOptions[1]),
                new Sample(new int[]{candidateOptions[2]}, tenureOptions[2], t0Options[2],
                        coolingOptions[2], iterOptions[2]),
                new Sample(new int[]{candidateOptions[3]}, tenureOptions[3], t0Options[3],
                        coolingOptions[3], iterOptions[3]),
                new Sample(new int[]{candidateOptions[4]}, tenureOptions[4], t0Options[4],
                        coolingOptions[4], iterOptions[4])
        );
    }

    private static void shuffle(int[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }

    private static void shuffle(double[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            double tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }
}