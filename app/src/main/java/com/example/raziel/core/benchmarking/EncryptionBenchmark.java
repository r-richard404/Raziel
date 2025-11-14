package com.example.raziel.core.benchmarking;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.google.crypto.tink.KeysetHandle;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Performance benchmarking framework for encryption algorithms
 */
public class EncryptionBenchmark {
    private static final String TAG = "EncryptionBenchmark";

    // Progress callback interface
    public interface BenchmarkProgressCallback {
        void onBenchmarkProgress(int currentStep, int totalSteps, String currentOperation);
    }
    private BenchmarkProgressCallback progressCallback;

    // Benchmarking configurations
    private static final int NUM_RUNS = 3;
    private static final int[] FILE_SIZES_MB = {1, 10, 50, 100, 500, 1000}; // testing a variation of sizes for emulator capabilities
    private static final String[] FILE_EXTENSIONS = {"txt", "pdf", "jpg", "png", "mp3", "mp4", "zip", "docx"};

    private final Context context;
    private final BenchmarkFileGenerator fileGenerator;

    public EncryptionBenchmark(Context context) {
        this.context = context;
        this.fileGenerator = new BenchmarkFileGenerator(context);
    }

    /**
     *  Benchmark Result structure for essential data
     */
    public static class BenchmarkResult {
        public final String algorithmName;
        public final int fileSizeMB;
        public final double averageTimeMs;
        public final double throughputMBps;
        public final int numRuns;

        public BenchmarkResult(String algorithmName, int fileSizeMB, double averageTimeMs, int numRuns) {
            this.algorithmName = algorithmName;
            this.fileSizeMB = fileSizeMB;
            this.averageTimeMs = averageTimeMs;
            this.numRuns = numRuns;

            // Calculate throughput
            double averageTimeSec = averageTimeMs / 1000.0;
            this.throughputMBps = fileSizeMB / averageTimeSec;
        }

        @Override
        public String toString() {
            return String.format("%s - %dMB: %.2f MB/s (%.0fms avg, %d runs", algorithmName, fileSizeMB, throughputMBps, averageTimeMs, numRuns);
        }
    }

    /**
     * Comprehensive benchmark result for scoring
     */
    public static class ComprehensiveBenchmarkResult {
        public final String algorithmName;
        public final Map<String, Map<Integer, BenchmarkResult>> resultsByType;
        public final double overallScore;

        public ComprehensiveBenchmarkResult(String algorithmName) {
            this.algorithmName = algorithmName;
            this.resultsByType = new TreeMap<>(); // TreeMap used for sorted display (file types and size)
            this.overallScore = 0.0;
        }

        public void addResult(String fileType, int sizeMB, BenchmarkResult result) {
            // TreeMap for sorted file sizes display
            resultsByType.computeIfAbsent(fileType, k -> new TreeMap<>()).put(sizeMB, result);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Benchmark Results  for ").append(algorithmName).append(":\n");

            for (Map.Entry<String, Map<Integer, BenchmarkResult>> typeEntry : resultsByType.entrySet()) {
                sb.append("\nFile Type: ").append(typeEntry.getKey()).append("\n");

                for (Map.Entry<Integer, BenchmarkResult> sizeEntry : typeEntry.getValue().entrySet()) {
                    sb.append("  ").append(sizeEntry.getValue().toString()).append("\n");
                }
            }
            return sb.toString();
        }
    }

    // === PROGRESS CALLBACK ===
    public void setProgressCallback(BenchmarkProgressCallback callback) {
        this.progressCallback = callback;
    }

    private int calculateTotalSteps(List<InterfaceEncryptionAlgorithm> algorithms) {
        return algorithms.size() * FILE_EXTENSIONS.length * FILE_SIZES_MB.length * NUM_RUNS;
    }


    // === Benchmark Algorithm ===

    private BenchmarkResult benchmarkFileSizeAndType(InterfaceEncryptionAlgorithm algorithm, int sizeMB, String fileExtension, int currentStep, int totalSteps) throws Exception {
        long totalTimeMS = 0;
        int successfulRuns = 0;

        for (int run = 0; run < NUM_RUNS; run++) {
            try {
                // Update progress - starting new run
                if (progressCallback != null) {
                    int step = currentStep * NUM_RUNS + run;
                    String operation = String.format("Testing %s - %dMB %s (Run %d/%d)", algorithm.getAlgorithmName(), sizeMB, fileExtension, run + 1, NUM_RUNS);
                    progressCallback.onBenchmarkProgress(step, totalSteps, operation);
                }

                // Create test file
                File testFile = fileGenerator.createBenchmarkFile(sizeMB, fileExtension, algorithm.getAlgorithmName());
                File encryptedFile = new File(context.getCacheDir(), "bench_enc_" + System.currentTimeMillis() + ".enc");
                File decryptedFile = new File(context.getCacheDir(), "bench_dec_" + System.currentTimeMillis() + ".dec");

                // Generate keyset for this run
                KeysetHandle keysetHandle = fileGenerator.createKeysetForAlgorithm(algorithm);

                // Benchmark encryption
                long startTime = System.currentTimeMillis();
                boolean encryptSuccess = algorithm.encryptFile(testFile, encryptedFile, keysetHandle, null);
                long encryptTime = System.currentTimeMillis() - startTime;

                if (encryptSuccess) {
                    // Benchmark decryption
                    startTime = System.currentTimeMillis();
                    boolean decryptSuccess = algorithm.decryptFile(encryptedFile, decryptedFile, keysetHandle, null);
                    long decryptTime = System.currentTimeMillis() - startTime;

                    if (decryptSuccess) {
                        // Use average of encrypt and decrypt times
                        long avgTime = (encryptTime + decryptTime) / 2;
                        totalTimeMS += avgTime;
                        successfulRuns++;
                    }
                }

                // CleanUp
                testFile.delete();
                encryptedFile.delete();
                decryptedFile.delete();

                // Cool down between runs
                if (run < NUM_RUNS - 1) {
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                Log.w(TAG, "Run " + run + " failed for " + sizeMB + "MB", e);
            }
        }

        if (successfulRuns > 0) {
            double averageTimeMs = (double) totalTimeMS / successfulRuns;
            return new BenchmarkResult(algorithm.getAlgorithmName(), sizeMB, averageTimeMs, successfulRuns);
        } else {
            throw new Exception("All benchmark runs failed for " + sizeMB + "MB");
        }
    }

    /**
     * Run benchmark for an encryption algorithm
     */
    public Map<String, ComprehensiveBenchmarkResult> runComprehensiveBenchmark(List<InterfaceEncryptionAlgorithm> algorithms) {
        Log.d(TAG, "Starting benchmark...");

        Map<String, ComprehensiveBenchmarkResult> results = new HashMap<>();

        int totalSteps = calculateTotalSteps(algorithms);
        int currentStep = 0;

        for (InterfaceEncryptionAlgorithm algorithm : algorithms) {
            Log.d(TAG, "Benchmarking algorithm: " + algorithm.getAlgorithmName());
            ComprehensiveBenchmarkResult algorithmResult = new ComprehensiveBenchmarkResult(algorithm.getAlgorithmName());

            for (String fileExtension : FILE_EXTENSIONS) {
                Log.d(TAG, "Testing file type: " + fileExtension);

                for (int sizeMB : FILE_SIZES_MB) {
                    try {
                        BenchmarkResult avgResult = benchmarkFileSizeAndType(algorithm, sizeMB, fileExtension, currentStep, totalSteps);
                        algorithmResult.addResult(fileExtension, sizeMB, avgResult);

                        Log.d(TAG, String.format("  %dMB: %.2f MB/s", sizeMB, avgResult.throughputMBps));

                    } catch (Exception e) {
                        Log.e(TAG, "Benchmark failed for " + sizeMB + "MB " + fileExtension, e);
                    }
                    currentStep++;
                }
                results.put(algorithm.getAlgorithmName(), algorithmResult);
            }
            // Final progress update
            if (progressCallback != null) {
                progressCallback.onBenchmarkProgress(totalSteps, totalSteps, "Benchmark Complete");
            }
        }
        return results;
    }

    // === Compare Algorithms ===
    public static class BenchmarkComparison {
        public final String algorithm1;
        public final String algorithm2;
        public final Map<String, Map<Integer, String>> comparisonByType;

        public BenchmarkComparison(String alg1, String alg2) {
            this.algorithm1 = alg1;
            this.algorithm2 = alg2;
            this.comparisonByType = new TreeMap<>();
        }

        public void addComparison(String fileType, int sizeMB, String comparison) {
            comparisonByType.computeIfAbsent(fileType, k -> new TreeMap<>()).put(sizeMB, comparison);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Comparison: ").append(algorithm1).append(" vs ").append(algorithm2).append("\n");

            for (Map.Entry<String, Map<Integer, String>> typeEntry : comparisonByType.entrySet()) {
                sb.append("\n").append(typeEntry.getKey()).append(":\n");

                for (Map.Entry<Integer, String> sizeEntry : typeEntry.getValue().entrySet()) {
                    sb.append("  ").append(sizeEntry.getKey()).append("MB: ").append(sizeEntry.getValue()).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private static String compareTwoResults(BenchmarkResult r1, BenchmarkResult r2) {
        double percentDiff;
        String fasterAlg;

        if (r1.throughputMBps > r2.throughputMBps) {
            fasterAlg = r1.algorithmName;
            percentDiff = ((r1.throughputMBps - r2.throughputMBps) / r2.throughputMBps) * 100;
        } else {
            fasterAlg = r2.algorithmName;
            percentDiff = ((r2.throughputMBps - r1.throughputMBps) / r1.throughputMBps) * 100;
        }

        return String.format("%s +%.1f%% (%.1f MB/s vs %.1f MB/s", fasterAlg, percentDiff, r1.throughputMBps, r2.throughputMBps);
    }

    public static BenchmarkComparison compareAlgorithms(ComprehensiveBenchmarkResult result1, ComprehensiveBenchmarkResult result2) {
        BenchmarkComparison comparison = new BenchmarkComparison(result1.algorithmName, result2.algorithmName);

        for (String fileType : result1.resultsByType.keySet()) {
            if (result2.resultsByType.containsKey(fileType)) {
                Map<Integer, BenchmarkResult> results1 = result1.resultsByType.get(fileType);
                Map<Integer, BenchmarkResult> results2 = result2.resultsByType.get(fileType);

                for (int sizeMB : results1.keySet()) {
                    if (results2.containsKey(sizeMB)) {
                        BenchmarkResult r1 = results1.get(sizeMB);
                        BenchmarkResult r2 = results2.get(sizeMB);

                        String compare = compareTwoResults(r1, r2);
                        comparison.addComparison(fileType, sizeMB, compare);
                    }
                }
            }
        }
        return comparison;
    }

    public void cleanUp() {
        fileGenerator.cleanUpBenchmarkFiles();
    }
}

