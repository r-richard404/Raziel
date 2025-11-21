package com.example.raziel.ui.controllers;

import android.os.Handler;
import android.view.View;

import com.example.raziel.ui.activities.MainActivity;

public class ProgressController {

    private final MainActivity activity;
    private final Handler main;

    private long lastProgressTime = 0;
    private long lastProgressBytes = 0;
    private long operationStartTime = 0;

    public ProgressController(MainActivity activity) {
        this.activity = activity;
        this.main = activity.mainHandler;
    }

    // === PUBLIC API ===

    public void showProgress(String title) {
        main.post(() -> {
            activity.progressCard.setVisibility(View.VISIBLE);
            activity.progressTitle.setText(title);

            operationStartTime = System.currentTimeMillis();
            lastProgressTime = 0;
            lastProgressBytes = 0;

            activity.progressBar.setIndeterminate(true);
            activity.progressPercentage.setText("0%");
            activity.speedMetric.setText("0 MB/s");
            activity.timeRemaining.setText("");
            activity.resultsCard.setVisibility(View.GONE);
        });
    }

    public void hideProgress() {
        main.post(() -> {
            activity.progressCard.setVisibility(View.GONE);
            activity.progressBar.setProgress(0);
            activity.progressBar.setIndeterminate(false);
        });
    }

    public void update(long processedBytes, long totalBytes) {
        main.post(() -> {

            // 1. Convert to percentage
            int pct = (int) ((processedBytes * 100) / totalBytes);
            activity.progressBar.setIndeterminate(false);
            activity.progressBar.setProgress(pct);
            activity.progressPercentage.setText(pct + "%");

            // 2. Throughput calculation
            long now = System.currentTimeMillis();

            if (lastProgressTime != 0) {
                double timeDelta = (now - lastProgressTime) / 1000.0;
                long bytesDelta = processedBytes - lastProgressBytes;

                if (timeDelta > 0 && bytesDelta > 0) {
                    double mbps = (bytesDelta / (1024.0 * 1024.0)) / timeDelta;
                    activity.speedMetric.setText(String.format("%.2f MB/s", mbps));

                    // Remaining time
                    long remainingBytes = totalBytes - processedBytes;
                    double secRemaining = remainingBytes / (bytesDelta / timeDelta);

                    activity.timeRemaining.setText(
                            secRemaining < 60
                                    ? String.format("~%.0fs", secRemaining)
                                    : String.format("~%.1fm", secRemaining / 60)
                    );
                }
            }

            lastProgressTime = now;
            lastProgressBytes = processedBytes;
        });
    }

    public void showResult(String msg) {
        main.post(() -> {
            hideProgress();
            activity.processStatus.setText(msg);
            activity.resultsCard.setVisibility(View.VISIBLE);
        });
    }
}
