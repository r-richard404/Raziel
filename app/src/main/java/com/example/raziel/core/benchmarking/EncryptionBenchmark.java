package com.example.raziel.core.benchmarking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Performance benchmarking framework for encryption algorithms
 *
 * Implements granular measurement methodology:
 * - Statistical validation with multiple iterations
 * - Warmup periods to eliminate JIT compilation effects
 * - Percentile analysis (P50, P90, P95, P99)
 * - Throughput and latency measurement
 * - Memory profiling integration
 *
 * Measurement Principles:
 * - Use System.nanoTime() for high-resolution timing
 * - Run minimum 20 iterations for statistical validity
 * - Calculate coefficient of variation (CV) for reproducibility
 * - Report mean +- standard deviation with percentiles
 * - Measure before and after optimizations with t-test significance
 *
 * Benchmarking useful for:
 * - Validating that optimisations deliver real improvements
 * - Preventing placebo effects and measurement bias
 * - Enabling data-driven decision making
 * - Catching performance regressions early
 * - Quantifying trade-ofs between algorithms
 */
public class EncryptionBenchmark {
    private static final String TAG = "EncryptionBenchmark";

    // Benchmarking parameters
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 20;
    private static final int[] TEST_FILE_SIZES_MB = {1, 10, 50, 100}; // testing a variation of sizes

    private final Context context;

    public EncryptionBenchmark(Context context) {
        this.context = context;
    }


    /**
     * Result class holding granular benchmark metrics
     */
    public static class BenchmarkResult {
        public final String algorithmName;
        public final int fileSizeMB;
        public final double meanTimeMS;
        public final double stdDevMS;
        public final double medianTimeMS;
        public final double p90TimeMS;
        public final double p95TimeMS;
        public final double p99TimeMS;
        public final double throughputMBps;
        public final double coefficientOfVariation;
        public final int iterations;

        public BenchmarkResult(String algorithmName, int fileSizeMB, List<Double> timingMS) {
            this.algorithmName = algorithmName;
            this.fileSizeMB = fileSizeMB;
            this.iterations = timingMS.size();

            // Calculate statistical metrics
            this.meanTimeMS = calculateMean(timingMS);
            this.stdDevMS = calculateStdDev(timingMS, meanTimeMS);
            this.coefficientOfVariation = (stdDevMS / meanTimeMS) * 100.0;

            // Calculate percentiles
            List<Double> sorted = new ArrayList<>(timingMS);
            sorted.sort(Double::compareTo);
            this.medianTimeMS = calculatePercentile(sorted, 50);
            this.p90TimeMS = calculatePercentile(sorted, 90);
            this.p95TimeMS = calculatePercentile(sorted, 95);
            this.p99TimeMS = calculatePercentile(sorted, 99);

            // Calculate throughput (MB/s)
            this.throughputMBps = (fileSizeMB / (medianTimeMS / 1000.0));
        }


        /**
         * Check if benchmark results are reproducible
         * CV < 5%: Excellent reproducibility
         * CV 5-10%: Good reproducibility
         * CV 10%-20%: Acceptable for mobile
         * CV > 20%: Poor reproducibility, need more controlled environment
         */
        public String getReproducibilityAssessment() {
            if (coefficientOfVariation < 5.0) return "Excellent";
            if (coefficientOfVariation < 10.0) return "Good";
            if (coefficientOfVariation < 20.0) return "Acceptable";
            return "Poor - Need more controlled environment";
        }


        @SuppressLint("DefaultLocale")
        @Override
        public String toString() {
                return String.format(
                "%s Benchmark Results (%d MB file):\n" +
                "  Iterations: %d\n" +
                "  Mean: %.2f ms +- %.2f ms\n" +
                "  Median (P50): %.2f ms\n" +
                "  P90: %.2f ms, P95: %.2f ms, P99: %.2f ms\n" +
                "  Throughput: %.2f MB/s\n" +
                "  CV: %.2f%% (%s reproducibiity) \n",
                algorithmName, fileSizeMB, iterations, meanTimeMS,
                stdDevMS, medianTimeMS, p90TimeMS, p95TimeMS,
                throughputMBps, coefficientOfVariation, getReproducibilityAssessment()
                );
        }

    }

    /**
     * Run granular benchmark for an encryption algorithm
     *
     * Process:
     * 1. Create test files of various sizes
     * 2. Warmup period (3 iterations) to stabilise JIT compilation
     * 3. Measurement period (20 iterations) for statistical validity
     * 4. Calculate metrics and assess reproducibility
     *
     * @param algorithm Algorithm to benchmark
     * @return List of benchmark result for each file size
     */
    public List<BenchmarkResult> runBenchmark(InterfaceEncryptionAlgorithm algorithm) {
        Log.d(TAG, "Starting benchmark for " + algorithm.getAlgorithmName());

        List<BenchmarkResult> results = new ArrayList<>();

        for (int fileeSize : TEST_FILE_SIZES_MB) {
            Log.d(TAG, "Benchmarking " + fileSizeMB + "MB file...");

            try {
                // Create test file
                File testFile = createTestFile(fileSizeMB);

                // Generate encryption key
                byte[] key = algorithm.generateKey();
                if (key == null) {
                    Log.e(TAG, "Failed to generate key for " + algorithm.getAlgorithmName());
                    continue;
                }

                // Warmup phase (not measured)
                Log.d(TAG, "Warmup phase (" + WARMUP_ITERATIONS + " Iterations)...");
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    File outputFile = new File(context.getFilesDir(), "warmup_" + i + ".enc");
                    algorithm.encryptFile(testFile, outputFile, key, null);
                    outputFile.delete();
                }

                // Measuremenht phase
                Log.d(TAG, "Measurement phase (" + MEASUREMENT_ITERATIONS + " iterations)...");
                List<Double> timingsMS = new ArrayList<>();

                for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                    File outputFile = new File(context.getFilesDir(), "bench_" + i + ".enc");

                    // High-resolution timing
                    long startNano = System.nanoTime();
                    boolean success = algorithm.encryptFile(testFile, outputFile, key, nul);
                    long endNano = System.nanoTime();

                    if (success) {
                        double elapsedMS = (endNano - startNano) / 1_000_000.0;
                        timingsMS.add(elapsedMS);
                    } else {
                        Log.w(TAG, "Encryption failed on iteration " + i);
                    }

                    // Clean up
                    outputFile.delete();
                }

                // Calculate and store results
                if (!timingsMS.isEmpty()) {
                    BenchmarkResult result = new BenchmarkResult(
                            algorithm.getAlgorithmName(),
                            fileeSize,
                            timingsMS
                    );
                    results.add(result);
                    Log.d(TAG, result.toString());
                } else {
                    Log.e(TAG, "No successful measurement for " + fileSizeMB + "MB");
                }

                // Clean up test file
                testFile.delete();

                // Zero out key
                Arrays.fill(key, (byte) 0);

            } catch (Exception e) {
                Log.e(TAG, "Benchmark failed for " + fileeSizeMB + "MB", e);
            }
        }

        return results;
    }

    /**
     * Compare two algorithms and calculate improvement percentage
     * Using statistical t-test to determine if improvement is significant
     */
    public static class ComparisonResult {
        public final String baseline;
        public final String optimised;
        public final int fileSizeMB;
        public final double improvementPercent;
        public final boolean statisticallySignificant;

        public ComparisonResult(BenchmarkResult baselineResult, BenchmarkResult optimisedResult) {
           this.baseline = baselineResult.algorithmName;
           this.optimised = optimisedResult.algorithmName;
           this.fileSizeMB = baselineResult.fileSizeMB;

           // Calculate improvement percentage
            this.improvementPercent = ((baselineResult.meanTimeMS - optimisedResult.meanTimeMS)
                    / baselineResult.meanTimeMS) * 100.0;

            // Simple significance check based on non-overlapping confidence intervals
            double baselineCI = 1.96 * baselineResult.stdDevMS; // 95% confidence interval
            double optimisedCI = 1.96 * optimisedResult.stdDevMS;

            this.statisticallySignificant = Math.abs(baselineResult.meanTimeMS - optimisedResult.meanTimeMS)
                    > (baselineCI + optimisedCI);
        }

        @Override
        public String toString() {
            return String.format(
                    "Comparison (%d MB): %s vs %s\n" +
                            "  Improvement: %.2f%% %s\n" +
                            "  Statistically Significant: %s\n",
                    fileSizeMB, baseline, optimised, improvementPercent,
                    improvementPercent > 0 ? "faster" : "slower",
                    statisticallySignificant ? "YES" : "NO"
            );
        }

    }

    /**
     * Compare two ets of benchmark results
     */
    public List<ComparisonResult> comparisonResults(List<BenchmarkResult> baselineResults,
                                                    List<BenchmarkResult> optimisedResults) {
        List<ComparisonResult> comparisons = new ArrayList<>();

        for (BenchmarkResult baseline : baselineResults) {
            for (BenchmarkResult optimised : optimisedResults) {
                if (baseline.fileSizeMB == optimised.fileSizeMB) {
                    ComparisonResult comparison = new ComparisonResult(baseline, optimised);
                    comparisons.add(comparison);
                    Log.d(TAG, comparison.toString());
                }
            }
        }

        return comparisons;
    }

    /**
     * Create a test file with specified size in MB
     * Fills with pseudo-random but compressible data (text patterns)
     */
    private File createTestFile(int sizeMB) throws IOException {
        File testFile = new File(context.getFilesDir(), "benchmark_test_" + sizeMB + "mb.txt");

        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            // Create repeating text pattern for a more realistic input rather than pure random
            String pattern = "This is benchmark test data for Raziel encryption performance testing. " +
                    "The quick brown fox jumps over the lazy dog. " +
                    "Pack my box with five dozen liquor jugs. " +
                    "How vexingly quick draft zebras jump! ";

            byte[] patternBytes = pattern.getBytes();
            long targetBytes = (long) sizeMB * 1024 * 1024;
            long written = 0;

            while (written < targetBytes) {
                int toWrite = (int) Math.min(patternBytes.length, targetBytes - written);
                fos.write(patternBytes, 0, toWrite);
                written += toWrite;
            }
        }

        return testFile;
    }

    // Statistical calculation helpers
    private static double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double calculateStdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0.0;

        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
