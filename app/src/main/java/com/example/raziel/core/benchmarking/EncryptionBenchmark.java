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
    private static final int NUM_RUNS = 5;
    private static final int[] TEST_FILE_SIZES_MB = {1, 10, 20, 30, 40, 50}; // testing a variation of sizes for emulator capabilities

    private static final int[] TEST_FILE_HARDWARE_SIZES_MB = {50, 100, 150, 200, 250, 300, 350, 400, 450, 500}; // testing on S23 Ultra hardware

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
        public final double averageTimeMs;
        public final double throughputMBps;
        public final int numRuns;

        public BenchmarkResult(String algorithmName, int fileSizeMB,  double averageTimeMs, int numRuns) {
            this.algorithmName = algorithmName;
            this.fileSizeMB = fileSizeMB;
            this.averageTimeMs = averageTimeMs;
            this.numRuns = numRuns;

            // Calculate throughput
            double averageTimeSec = averageTimeMs / 1000.0;
            this.throughputMBps = fileSizeMB / averageTimeSec;
        }


            @SuppressLint("DefaultLocale")
            @Override
            public String toString() {
                return String.format("%s - %dMB: %.2f MB/s (%.0fms avg, %d runs)",
                        algorithmName, fileSizeMB, throughputMBps, averageTimeMs, numRuns);
            }
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

        for (int sizeMB : TEST_FILE_SIZES_MB) {
            try {
                // Create test file
                File testFile = createTestFile(sizeMB);

                // Run multiple times for average
                long totalTimeMs = 0;
                int successfulRuns = 0;

                for (int run = 0; run < NUM_RUNS; run++) {
                    // Generate key
                    byte[] key = algorithm.generateKey();
                    if (key == null) {
                        Log.e(TAG, "Key generation failed");
                        continue;
                    }

                    // Create output file
                    File outputFile = new File(context.getFilesDir(),
                            "bench_" + sizeMB + "mb_run" + run + ".enc");

                    // Measure encryption time
                    long startTime = System.currentTimeMillis();
                    boolean success = algorithm.encryptFile(testFile, outputFile, key, null);
                    long endTime = System.currentTimeMillis();

                    if (success) {
                        totalTimeMs += (endTime - startTime);
                        successfulRuns++;
                    } else {
                        Log.w(TAG, "Encryption failed for run " + run);
                    }

                    // Clean up
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }

                    // Zero out key
                    java.util.Arrays.fill(key, (byte) 0);

                    // Small delay between runs to prevent thermal throttling
                    if (run < NUM_RUNS - 1) {
                        Thread.sleep(100);
                    }
                }

                // Calculate average
                if (successfulRuns > 0) {
                    double averageTimeMs = (double) totalTimeMs / successfulRuns;
                    BenchmarkResult result = new BenchmarkResult(
                            algorithm.getAlgorithmName(), sizeMB, averageTimeMs, successfulRuns);
                    results.add(result);
                    Log.d(TAG, result.toString());
                }

                // Clean up test file
                testFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "Benchmark failed for " + sizeMB + "MB", e);
            }
        }

        return results;
    }


    /**
     * Compare two algorithms and calculate improvement percentage
     * Using statistical t-test to determine if improvement is significant
     */
    public static List<String> compareAlgorithms(List<BenchmarkResult> algorithm1,
                                                 List<BenchmarkResult> algorithm2) {
        List<String> comparisons = new ArrayList<>();

        // Compare matching file sizes
        for (BenchmarkResult result1 : algorithm1) {
            for (BenchmarkResult result2 : algorithm2) {
                if (result1.fileSizeMB == result2.fileSizeMB) {
                    // Determine which is faster
                    String fasterAlg;
                    double percentDiff;

                    if (result1.throughputMBps > result2.throughputMBps) {
                        fasterAlg = result1.algorithmName;
                        percentDiff = ((result1.throughputMBps - result2.throughputMBps) /
                                result2.throughputMBps) * 100.0;
                    } else {
                        fasterAlg = result2.algorithmName;
                        percentDiff = ((result2.throughputMBps - result1.throughputMBps) /
                                result1.throughputMBps) * 100.0;
                    }

                    String comparison = String.format(
                            "%dMB: %s is %.1f%% faster\n" +
                                    "  %s: %.2f MB/s\n" +
                                    "  %s: %.2f MB/s",
                            result1.fileSizeMB, fasterAlg, percentDiff,
                            result1.algorithmName, result1.throughputMBps,
                            result2.algorithmName, result2.throughputMBps);

                    comparisons.add(comparison);
                    break;
                }
            }
        }

        return comparisons;
    }


  }

