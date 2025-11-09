package com.example.raziel.core.performance;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Performance metrics collector for encryption performance
 *
 * Tracks metrics including:
 * - Operation counts and success rates
 * - Throughput measurements
 * - Latency distributions
 * - Memory allocation patterns
 * - Error rates and failure reasons
 */
public class PerformanceMetrics {
    private static final String TAG = "PerformanceMetrics";

    // Counters
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong successfulOperations= new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeNs = new AtomicLong(0);

    // Algorithm metrics
    private final ConcurrentHashMap<String, AlgorithmMetrics> algorithmMetrics = new ConcurrentHashMap<>();


    public static class AlgorithmMetrics {
        public final String algorithmName;
        public final AtomicLong operations = new AtomicLong(0);
        public final AtomicLong successes = new AtomicLong(0);
        public final AtomicLong failures = new AtomicLong(0);
        public final AtomicLong totalBytes = new AtomicLong(0);
        private double totalTimeMs = 0.0;

        public AlgorithmMetrics(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        public synchronized void recordSuccess(long bytes, double durationMs) {
            operations.incrementAndGet();
            successes.incrementAndGet();
            totalBytes.addAndGet(bytes);
            totalTimeMs += durationMs;
        }

        public void recordFailure() {
            operations.incrementAndGet();
            failures.incrementAndGet();
        }

        public long getOperations() {
            return operations.get();
        }

        public long getSuccesses() {
            return successes.get();
        }

        public long getFailures() {
            return failures.get();
        }

        public synchronized double getAverageThroughputMBps() {
            if (totalTimeMs == 0) return 0.0;
            double totalMB = totalBytes.get() / 1024.0 / 1024.0;
            return totalMB / (totalTimeMs / 1000.0);
        }
    }


    /**
     * Record successful encryption/decryption operation
     */
    public void recordOperation(String algorithmName, long fileSizeBytes, long durationNs, boolean success) {
        totalOperations.incrementAndGet();

        if (success) {
            successfulOperations.incrementAndGet();
            totalBytesProcessed.addAndGet(fileSizeBytes);
            totalProcessingTimeNs.addAndGet(durationNs);

            // Record timing in milliseconds
            double durationMs = durationNs / 1_000_000.0;


            // Update algorithm-specific metrics
            AlgorithmMetrics metrics = algorithmMetrics.computeIfAbsent(
                    algorithmName, k -> new AlgorithmMetrics(algorithmName));
            metrics.recordSuccess(fileSizeBytes, durationMs);

        } else {
            failedOperations.incrementAndGet();

            AlgorithmMetrics metrics = algorithmMetrics.computeIfAbsent(
                    algorithmName, k -> new AlgorithmMetrics(algorithmName));
            metrics.recordFailure();
        }
    }


    /**
     * Immutable snapshot of current performance metrics
     */
    public static class PerformanceSnapshot {
        public final long totalOperations;
        public final long successfulOperations;
        public final long failedOperations;
        public final long totalBytesProcessed;
        public final long totalProcessingTimeNs;
        public final ConcurrentHashMap<String, AlgorithmMetrics> algorithmMetrics;

        public PerformanceSnapshot(
                long totalOperations,
                long successfulOperations,
                long failedOperations,
                long totalBytesProcessed,
                long totalProcessingTimeNs,
                ConcurrentHashMap<String, AlgorithmMetrics> algorithmMetrics) {

                    this.totalOperations = totalOperations;
                    this.successfulOperations = successfulOperations;
                    this.failedOperations = failedOperations;
                    this.totalBytesProcessed = totalBytesProcessed;
                    this.totalProcessingTimeNs = totalProcessingTimeNs;
                    this.algorithmMetrics = algorithmMetrics;
        }

        public double getSuccessRate() {
                    return totalOperations > 0 ? (double) successfulOperations / totalOperations : 0.0;

        }

        public double getAverageThroughputMBps() {
                    if (totalProcessingTimeNs == 0) return 0.0;
                    double totalMB = totalBytesProcessed / 1024.0 / 1024.0;
                    double totalSeconds = totalProcessingTimeNs / 1_000_000_000.0;
                    return totalMB / totalSeconds;
        }
    }


    /**
     * Get current performance snapshot
     */
    public PerformanceSnapshot getSnapshot() {
        return new PerformanceSnapshot(
                totalOperations.get(),
                successfulOperations.get(),
                failedOperations.get(),
                totalBytesProcessed.get(),
                totalProcessingTimeNs.get(),
                new ConcurrentHashMap<>(algorithmMetrics)
        );
    }


    /**
     * Reset all metrics for future testing different configurations
     */
    public void reset() {

        totalOperations.set(0);
        successfulOperations.set(0);
        failedOperations.set(0);
        totalOperations.set(0);
        totalBytesProcessed.set(0);
        totalProcessingTimeNs.set(0);
        algorithmMetrics.clear();

        Log.d(TAG, "Metrics reset");
    }
}
