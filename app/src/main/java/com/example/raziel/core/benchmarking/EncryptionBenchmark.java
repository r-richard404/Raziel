package com.example.raziel.core.benchmarking;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.google.crypto.tink.KeysetHandle;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
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
    private static final int[] FILE_SIZES_AES_MB = {1, 10, 50, 100, 500, 1000}; // testing a variation of sizes for emulator capabilities
    private static final int[] FILE_SIZE_CHACHA_MB = {1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
    private static final String[] FILE_EXTENSIONS = {"txt", "pdf", "jpg", "png", "mp3", "mp4", "zip", "docx"};

    private final Context context;
    private final BenchmarkFileGenerator fileGenerator;

    public EncryptionBenchmark(Context context) throws GeneralSecurityException, IOException {
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

        public final boolean successful;
        public final String errorMessage;

        public BenchmarkResult(String algorithmName, int fileSizeMB, double averageTimeMs, int numRuns) {
            this(algorithmName, fileSizeMB, averageTimeMs, numRuns, true, null);
        }

        public BenchmarkResult(String algorithmName, int fileSizeMB, double averageTimeMs, int numRuns, boolean successful, String errorMessage) {
            this.algorithmName = algorithmName;
            this.fileSizeMB = fileSizeMB;
            this.averageTimeMs = averageTimeMs;
            this.numRuns = numRuns;
            this.successful = successful;
            this.errorMessage = errorMessage;

            // Calculate throughput only for successful runs
            if (successful && averageTimeMs > 0) {
                double averageTimeSec = averageTimeMs / 1000.0;
                this.throughputMBps = fileSizeMB / averageTimeSec;
            } else {
                this.throughputMBps = 0;
            }
        }

        @Override
        public String toString() {
            if (!successful) {
                return String.format("%s - %dMB: FAILED (%s)", algorithmName, fileSizeMB, errorMessage);
            }
            return String.format("%s - %dMB: %.2f MB/s (%.0fms avg, %d runs", algorithmName, fileSizeMB, throughputMBps, averageTimeMs, numRuns);
        }
    }

    /**
     * Comprehensive benchmark result for scoring
     */
    public static class ComprehensiveBenchmarkResult {
        public final String algorithmName;
        public final Map<String, Map<Integer, BenchmarkResult>> resultsByType;

        public int successfulTests;
        public int totalTests;

        public ComprehensiveBenchmarkResult(String algorithmName) {
            this.algorithmName = algorithmName;
            this.resultsByType = new TreeMap<>(); // TreeMap used for sorted display (file types and size)
            this.successfulTests = 0;
            this.totalTests = 0;
        }

        public void addResult(String fileType, int sizeMB, BenchmarkResult result) {
            // TreeMap for sorted file sizes display
            resultsByType.computeIfAbsent(fileType, k -> new TreeMap<>()).put(sizeMB, result);

            totalTests++;
            if (result.successful) {
                successfulTests++;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Benchmark Results  for ").append(algorithmName).append(" (").append(successfulTests)
                    .append("/").append(totalTests).append("tests successful):\n");

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

    // Get appropriate file sizes for each algorithm
    private int[] getFileSizesForAlgorithm(InterfaceEncryptionAlgorithm algorithm) {
        if (algorithm.getAlgorithmName().contains("AES")) {
            return FILE_SIZES_AES_MB;
        } else {
            return FILE_SIZE_CHACHA_MB;
        }
    }

    // Calculate total steps fod different file sizes per algorithm
    private int calculateTotalSteps(List<InterfaceEncryptionAlgorithm> algorithms) {
        int totalSteps = 0;
        for (InterfaceEncryptionAlgorithm algorithm : algorithms) {
            int[] fileSizes = getFileSizesForAlgorithm(algorithm);
            totalSteps += FILE_EXTENSIONS.length * fileSizes.length * NUM_RUNS;
        }
        return totalSteps;
    }

    // Safe file deletion
    private void safeDelete(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception e){
                Log.w(TAG, "Failed to delete file: " + file.getAbsolutePath(), e);
            }
        }
    }


    // === Benchmark Algorithm ===

    private BenchmarkResult benchmarkFileSizeAndType(InterfaceEncryptionAlgorithm algorithm, int sizeMB, String fileExtension, int currentStep, int totalSteps) throws Exception {
        long totalTimeMS = 0;
        int successfulRuns = 0;
        String lastError = null;

        for (int run = 0; run < NUM_RUNS; run++) {
            try {
                // Update progress - starting new run
                if (progressCallback != null) {
                    int step = currentStep + run;
                    String operation = String.format("Testing %s - %dMB %s (Run %d/%d)", algorithm.getAlgorithmName(), sizeMB, fileExtension, run + 1, NUM_RUNS);
                    progressCallback.onBenchmarkProgress(step, totalSteps, operation);
                }

                // Create test files in Android's app cached directory
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

                        Log.d(TAG, String.format("Run %d successful: %dms", run, avgTime));
                    } else {
                        lastError = "Decryption failed";
                    }
                } else {
                    lastError = "Encryption failed";
                }

                // CleanUp
                safeDelete(testFile);
                safeDelete(encryptedFile);
                safeDelete(decryptedFile);

                // Cool down between runs
                if (run < NUM_RUNS - 1) {
                    Thread.sleep(500);
                }
                // Catch XChaCha20-Poly1305 exception to compare
            } catch (OutOfMemoryError e) {
                lastError = "Out of Memory - file too large";
                Log.w(TAG, "OutOfMemoryError for " + sizeMB + "MB: " + e.getMessage());
                break; // Stop trying this file size if memory limit is hit
            } catch (Exception e) {
                lastError = e.getMessage();
                Log.w(TAG, "Run " + run + " failed for " + sizeMB + "MB" + e.getMessage());
            }
        }

        if (successfulRuns > 0) {
            double averageTimeMs = (double) totalTimeMS / successfulRuns;
            return new BenchmarkResult(algorithm.getAlgorithmName(), sizeMB, averageTimeMs, successfulRuns);
        } else {
            return new BenchmarkResult(algorithm.getAlgorithmName(), sizeMB, 0, 0, false, lastError != null ? lastError : "All runs failed");
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

            int[] fileSizes = getFileSizesForAlgorithm(algorithm);

            for (String fileExtension : FILE_EXTENSIONS) {
                Log.d(TAG, "Testing file type: " + fileExtension);

                for (int sizeMB : fileSizes) {
                    try {
                        BenchmarkResult result = benchmarkFileSizeAndType(algorithm, sizeMB, fileExtension, currentStep, totalSteps);
                        algorithmResult.addResult(fileExtension, sizeMB, result);

                        if (result.successful) {
                            Log.d(TAG, String.format("  %dMB: %.2f MB/s", sizeMB, result.throughputMBps));
                        } else {
                            Log.w(TAG, String.format("  %dMB: FAILED - %s", sizeMB, result.errorMessage));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Benchmark failed for " + sizeMB + "MB " + fileExtension, e);

                        BenchmarkResult failedResult = new BenchmarkResult(algorithm.getAlgorithmName(), sizeMB, 0, 0, false, e.getMessage());
                    }
                    currentStep += NUM_RUNS;
                }
                results.put(algorithm.getAlgorithmName(), algorithmResult);

                Log.d(TAG, String.format("Completed %s: %d/%d tests successful", algorithm.getAlgorithmName(), algorithmResult.successfulTests, algorithmResult.totalTests));
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
        public int comparableTests; // Not all tests will be comparable since XChaCha20 will fail after 50MB file size
        public int totalTests;  // Storing all tests including failed ones

        public BenchmarkComparison(String alg1, String alg2) {
            this.algorithm1 = alg1;
            this.algorithm2 = alg2;
            this.comparisonByType = new TreeMap<>();
            this.comparableTests = 0;
            this.totalTests = 0;
        }

        public void addComparison(String fileType, int sizeMB, String comparison, boolean comparable) {
            comparisonByType.computeIfAbsent(fileType, k -> new TreeMap<>()).put(sizeMB, comparison);
            totalTests++;
            if (comparable) {
                comparableTests++;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Comparison: ").append(algorithm1).append(" vs ").append(algorithm2).append(" (").append(comparableTests).append("/").append(totalTests)
                    .append("tests comparable)\n");

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
        // Handle the cases where one or both results failed
        if (!r1.successful && !r2.successful) {
            return "BOTH FAILED";
        } else if (!r1.successful) {
            return String.format("%s FAILED, %s: %.1f MB/s", r1.algorithmName, r2.algorithmName, r2.throughputMBps);
        } else if (!r2.successful) {
            return String.format("%s: %.1f MB/s, %s FAILED", r1.algorithmName, r1.throughputMBps, r2.algorithmName);
        }

        // If both successful then compare throughput
        double percentDiff;
        String fasterAlg;

        if (r1.throughputMBps > r2.throughputMBps) {
            fasterAlg = r1.algorithmName;
            percentDiff = ((r1.throughputMBps - r2.throughputMBps) / r2.throughputMBps) * 100;
        } else {
            fasterAlg = r2.algorithmName;
            percentDiff = ((r2.throughputMBps - r1.throughputMBps) / r1.throughputMBps) * 100;
        }
        return String.format("%s +%.1f%% (%.1f MB/s vs %.1f MB/s)", fasterAlg, percentDiff, r1.throughputMBps, r2.throughputMBps);
    }

    public static BenchmarkComparison compareAlgorithms(ComprehensiveBenchmarkResult result1, ComprehensiveBenchmarkResult result2) {
        BenchmarkComparison comparison = new BenchmarkComparison(result1.algorithmName, result2.algorithmName);

        for (String fileType : result1.resultsByType.keySet()) {
            if (result2.resultsByType.containsKey(fileType)) {
                Map<Integer, BenchmarkResult> results1 = result1.resultsByType.get(fileType);
                Map<Integer, BenchmarkResult> results2 = result2.resultsByType.get(fileType);

                assert results1 != null;
                for (int sizeMB : results1.keySet()) {
                    assert results2 != null;
                    if (results2.containsKey(sizeMB)) {
                        BenchmarkResult r1 = results1.get(sizeMB);
                        BenchmarkResult r2 = results2.get(sizeMB);

                        String compare = compareTwoResults(r1, r2);
                        boolean comparable = r1.successful && r2.successful;
                        comparison.addComparison(fileType, sizeMB, compare, comparable);
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

