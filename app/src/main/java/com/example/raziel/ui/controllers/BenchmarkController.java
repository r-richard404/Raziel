package com.example.raziel.ui.controllers;

import com.example.raziel.core.benchmarking.EncryptionBenchmark;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.ui.activities.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BenchmarkController {

    private final MainActivity activity;
    private final EncryptionManager encryptionManager;
    private final EncryptionBenchmark encryptionBenchmark;

    public BenchmarkController(MainActivity activity, EncryptionManager encryptionManager, EncryptionBenchmark encryptionBenchmark) {
        this.activity = activity;
        this.encryptionManager = encryptionManager;
        this.encryptionBenchmark = encryptionBenchmark;
    }

    // Public entry point (call from MainActivity button)
    public void runComprehensiveBenchmark() {
        activity.setUiEnabled(false);
        activity.showProgress(true, "Running Comprehensive Benchmark...", true);

        encryptionBenchmark.setProgressCallback((currentStep, totalSteps, currentOperation) -> {
            int progress = totalSteps > 0
                    ? (int) ((currentStep * 100) / (double) totalSteps)
                    : 0;

            // Reuse progress title + percentage in activity
            activity.mainHandler.post(() -> {
                activity.progressTitle.setText(currentOperation);
                activity.progressBar.setIndeterminate(false);
                activity.progressBar.setProgress(progress);
                activity.progressPercentage.setText(progress + "%");
            });
        });

        activity.executorService.execute(() -> {
            try {
                List<InterfaceEncryptionAlgorithm> algorithms =
                        encryptionManager.getAvailableAlgorithms();

                Map<String, EncryptionBenchmark.ComprehensiveBenchmarkResult> results =
                        encryptionBenchmark.runComprehensiveBenchmark(algorithms);

                activity.mainHandler.post(() -> {
                    encryptionBenchmark.setProgressCallback(null);
                    displayBenchmarkResults(results);
                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", true);
                });
            } catch (Exception e) {
                activity.mainHandler.post(() -> {
                    encryptionBenchmark.setProgressCallback(null);
                    activity.showError("Benchmark Failed", e.getMessage());
                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", true);
                });
            }
        });
    }

    // Internal: build big summary string and show via activity.showResults()
    private void displayBenchmarkResults(Map<String, EncryptionBenchmark.ComprehensiveBenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPREHENSIVE BENCHMARK RESULTS ===\n\n");

        for (EncryptionBenchmark.ComprehensiveBenchmarkResult result : results.values()) {
            sb.append(result.toString()).append("\n");
        }

        // If exactly 2 algorithms => include comparison table
        if (results.size() == 2) {
            List<EncryptionBenchmark.ComprehensiveBenchmarkResult> resultList =
                    new ArrayList<>(results.values());

            EncryptionBenchmark.BenchmarkComparison comparison =
                    EncryptionBenchmark.compareAlgorithms(resultList.get(0), resultList.get(1));

            sb.append("\n").append(comparison.toString());
        }

        activity.showResults(sb.toString());
    }
}
