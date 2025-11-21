package com.example.raziel.ui.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.raziel.R;
import com.example.raziel.core.benchmarking.EncryptionBenchmark;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.managers.file.FileSelectionManager;
import com.example.raziel.ui.controllers.BenchmarkController;
import com.example.raziel.ui.controllers.CacheController;
import com.example.raziel.ui.controllers.EncryptionController;
import com.example.raziel.ui.controllers.FileSelectionController;
import com.example.raziel.ui.controllers.ProgressController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Main Activity for testing encryption performance optimisations
 *
 * Key Architecture Decisions:
 * Background thread execution that prevents Application Not Responding
 * Handled-based UI updates for thread-safe communication
 *
 * Features:
 * 1. Real-time performance visualisation
 * 3. Material Design 3 UI (for minimal performance impact and ease of use)
 * 4. Progress tracking with time estimation
 * 5. Device capability detection
 */
public class MainActivity extends AppCompatActivity implements FileSelectionManager.FileSelectionCallback {
    private static final String TAG = "MainActivity";
    private static final int MAX_THREADS = 4;

    // Progress Tracking
    public long lastProgressTime = 0;
    public long lastProgressBytes = 0;
    public long operationsStartTime = 0;


    // === Managers / Controllers ===
    private FileSelectionManager fileSelectionManager;
    private FileSelectionController fileSelectionController;
    public ProgressController progressController;
    public EncryptionController encryptionController;
    public EncryptionManager encryptionManager;
    private CacheController cacheController;
    private BenchmarkController benchmarkController;

    public java.util.concurrent.ExecutorService executorService;

    // === UI Components needed by FileSelectionController ===
    public FileSelectionManager.FileInfo selectedFile;

    // === Thread handler ===
    public Handler mainHandler = new Handler(Looper.getMainLooper());

    // last encrypted
    public File lastEncryptedFile;


    // UI Components
    public AutoCompleteTextView algorithmDropdown, fileTypeDropdown;
    public MaterialButton btnEncrypt, btnDecrypt, btnBenchmark, btnSelectFile;
    public LinearProgressIndicator progressBar;
    public TextView processStatus;
    public TextView progressTitle;
    public TextView progressPercentage;
    public TextView fileTypeDescription;
    public TextView fileInfoText;
    public TextView speedMetric, timeRemaining, fileSizeText;
    public MaterialCardView progressCard, resultsCard, fileSelectionCard;
    public Chip chipDevice, chipMemory, chipCores, chipHardwareStatus;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // === 1. Initialise UI ===
        findViews();
        progressController = new ProgressController(this);

        try {
            encryptionManager = new EncryptionManager(this);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        EncryptionBenchmark encryptionBenchmark;
        try {
            encryptionBenchmark = new EncryptionBenchmark(this);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }

        fileSelectionManager    = new FileSelectionManager(this);
        fileSelectionController = new FileSelectionController(this, fileSelectionManager);
        encryptionController    = new EncryptionController(this, encryptionManager);
        benchmarkController     = new BenchmarkController(this, encryptionManager, encryptionBenchmark);
        cacheController         = new CacheController(this, encryptionManager);
        cacheController.attachButtons();

        executorService = java.util.concurrent.Executors.newFixedThreadPool(MAX_THREADS);

        encryptionController.setupAlgorithmDropdown();
        setupDeviceInfo();

        // === 4. File picker button ===
        MaterialButton btnSelect = findViewById(R.id.btnSelectFile);
        btnSelect.setOnClickListener(v -> fileSelectionController.openPicker());

        encryptionController = new EncryptionController(this, encryptionManager);

        setupButtonListeners();

        // Background threads
        executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }

    // === Required by FileSelectionManager callback ===
    @Override
    public void onFileSelected(FileSelectionManager.FileInfo fileInfo) {
        // Forward to controller (you *must* keep this)
        fileSelectionController.onFileSelected(fileInfo);
    }

    @Override
    public void onFileSelectionError(String error) {
        showError("File Selection Error", error);
    }

    // --- Small helper used by controller ---
    public void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Initialise all UI components
     */
    private void findViews() {
        // Algorithm Selection
        algorithmDropdown = findViewById(R.id.algorithmDropdown);

        // Buttons
        btnEncrypt = findViewById(R.id.btnEncrypt);
        btnDecrypt = findViewById(R.id.btnDecrypt);
        btnBenchmark = findViewById(R.id.btnBenchmark);
        btnSelectFile = findViewById(R.id.btnSelectFile);

        // Progress Components
        progressBar = findViewById(R.id.progressBar);
        progressCard = findViewById(R.id.progressCard);
        resultsCard = findViewById(R.id.resultsCard);
        progressTitle = findViewById(R.id.progressTitle);
        progressPercentage = findViewById(R.id.progressPercentage);
        processStatus = findViewById(R.id.processStatus);

        fileSelectionCard = findViewById(R.id.fileSelectionCard);
        fileInfoText = findViewById(R.id.fileInfoText);

        speedMetric = findViewById(R.id.speedMetric);
        timeRemaining = findViewById(R.id.timeRemaining);

        // Device info chips
        chipDevice = findViewById(R.id.chipDevice);
        chipMemory = findViewById(R.id.chipMemory);
        chipCores = findViewById(R.id.chipCores);
        chipHardwareStatus = findViewById(R.id.chipHardwareStatus);
    }

    private void setupButtonListeners() {

        btnSelectFile.setOnClickListener(v -> fileSelectionManager.openFilePicker());

        btnEncrypt.setOnClickListener(v -> {
            if (encryptionController != null) {
                encryptionController.encryptSelectedFile();
            }
        });

        btnDecrypt.setOnClickListener(v -> {
            if (encryptionController != null) {
                encryptionController.decryptSelectedFile();
            }
        });

        btnBenchmark.setOnClickListener(v -> {
            if (benchmarkController != null) {
                benchmarkController.runComprehensiveBenchmark();
            }
        });

        // Cache buttons (either via cacheController.attachButtons() or explicitly):
        MaterialButton btnCacheStats = findViewById(R.id.btnCacheStats);
        MaterialButton btnClearCache = findViewById(R.id.btnClearCache);

        if (btnCacheStats != null) {
            btnCacheStats.setOnClickListener(v -> {
                if (cacheController != null) cacheController.showCachePerformance();
            });
        }

        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> {
                if (cacheController != null) cacheController.clearCache();
            });
        }
    }


    public void setUiEnabled(boolean enabled) {
        btnEncrypt.setEnabled(enabled);
        btnDecrypt.setEnabled(enabled);
        btnBenchmark.setEnabled(enabled);
        btnSelectFile.setEnabled(enabled);
        algorithmDropdown.setEnabled(enabled);
    }

    public void showProgress(boolean visible, String title, boolean indeterminate) {
        if (progressController == null) return;

        if (visible) {
            progressController.showProgress(title);
        } else {
            progressController.hideProgress();
        }
    }


    public void updateProgressBar(long processedBytes, long totalBytes) {
        if (progressController != null) {
            progressController.update(processedBytes, totalBytes);
        }
    }


    /**
     * Setup device information
     */
    private void setupDeviceInfo() {
        // Device model
        String deviceModel = Build.MODEL;
        if (deviceModel != null && !deviceModel.isEmpty()) {
            chipDevice.setText(deviceModel);
        }

        // Memory Info
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memoryInfo);
            long totalMemoryGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
            chipMemory.setText(String.format(Locale.US, "%dGB RAM", totalMemoryGB));
        }

        // CPU cores
        int availableProcesors = Runtime.getRuntime().availableProcessors();
        chipCores.setText(String.format(Locale.US, "%d Cores", availableProcesors));

        // Hardware Acceleration status
        chipHardwareStatus.setText("AES Hardware: " + (encryptionManager.getRecommendedAlgorithm().getAlgorithmName().contains("AES") ? "YES" : "NO"));
    }

    private void initialiseViews() {
        findViews();
        setupButtonListeners();
        //setupAlgorithmDropdown();
        setupDeviceInfo();
    }

    /**
     * Initialising encryption manager with this activity's context
     */
    private void setupEncryptionManager() throws GeneralSecurityException, IOException {
        encryptionManager = new EncryptionManager(this);

        // Show recommended algorithm
        InterfaceEncryptionAlgorithm recommended = encryptionManager.getRecommendedAlgorithm();
        updateStatus("Initialised. Recommended algorithm: " + recommended.getAlgorithmName());
    }

    /**
     * Show results
     */
    public void showResults(String message) {
        mainHandler.post(() -> {
            if (processStatus != null && resultsCard != null) {
                processStatus.setText(message);
                resultsCard.setVisibility(View.VISIBLE);

                Log.d("UI_Debug", "Results card made visible with message: " + message);
            } else {
                Log.e("UI_Debug", "processStatus or resultsCard is null!");
                if (processStatus == null) {
                    Log.e("UI_Debug", "processStatus is null");
                }
                if (resultsCard == null) {
                    Log.e("UI_Debug", "resultsCard is null");
                }
            }
        });
    }

    public void resetProgressTracking() {
        operationsStartTime = System.currentTimeMillis();
        lastProgressTime = operationsStartTime;
        lastProgressBytes = 0;
    }


    /**
     * Show detailed results
     */
    public void showDetailedResults(EncryptionResult result) {
        double fileSizeMB = result.getFileSizeBytes() / (1024.0 * 1024.0);
        double timeSec = result.getProcessingTimeMs() / 1000.0;
        double throughputMBps = fileSizeMB / timeSec;

        @SuppressLint("DefaultLocale") String message = String.format(
                "SUCCESSFUL\n\n" +
                        "Operation: %s\n" +
                        "Algorithm: %s\n" +
                        "File Size: %.2f MB\n" +
                        "Time: %.2f seconds\n" +
                        "Throughput: %.2f MB/s\n" +
                        "Device: %s\n" +
                        "Cores Used: %d",
                result.getOperation(),
                result.getAlgorithmName(),
                fileSizeMB, timeSec, throughputMBps,
                chipDevice.getText(),
                Runtime.getRuntime().availableProcessors()
        );
        showResults(message);
    }

    /**
     * Update status text on UI thread
     */
    public void updateStatus(String message) {
        mainHandler.post(() -> {
            if (processStatus != null) {
                processStatus.setText(message);
            }
        });
    }

    /**
     * Clean up resources when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up any remaining temp files
        if (fileSelectionManager != null) {
            fileSelectionManager.cleanUpTempFiles();
        }

        if (encryptionManager != null) {
            encryptionManager.cleanup();
        }
        mainHandler.removeCallbacksAndMessages(null);
    }
}

