package com.example.raziel.ui.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.raziel.R;
import com.example.raziel.core.benchmarking.EncryptionBenchmark;
import com.example.raziel.core.caching.CacheManager;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.managers.file.FileSelectionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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
    private long lastProgressTime = 0;
    private long lastProgressBytes = 0;
    private long operationsStartTime = 0;

    // UI Components
    private AutoCompleteTextView algorithmDropdown, fileTypeDropdown;
    private MaterialButton btnEncrypt, btnDecrypt, btnBenchmark, btnSelectFile;
    private LinearProgressIndicator progressBar;
    private TextView processStatus, progressTitle, progressPercentage, fileTypeDescription, fileInfoText;
    private TextView speedMetric, timeRemaining, fileSizeText;
    private MaterialCardView progressCard, resultsCard, fileSelectionCard;
    private Slider fileSizeSlider;
    private Chip chipDevice, chipMemory, chipCores, chipHardwareStatus;

    // Core components
    private EncryptionManager encryptionManager;
    private FileSelectionManager fileSelectionManager;
    private EncryptionBenchmark encryptionBenchmark;
    private ExecutorService executorService;

    // State
    private FileSelectionManager.FileInfo selectedFile;
    private File lastEncryptedFile;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupCoreComponents();
        setupEncryptionManager();
        initialiseViews();
        setupButtonListeners();
        //setupFileSizeSlider();

        executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }

    // === SETUPS AND CONFIGS ===

    private void setupCoreComponents() {
        encryptionManager = new EncryptionManager(this);

        fileSelectionManager = new FileSelectionManager(this);
        fileSelectionManager.setCallback(this);

        encryptionBenchmark = new EncryptionBenchmark(this);

        InterfaceEncryptionAlgorithm recommendedAlgorithm = encryptionManager.getRecommendedAlgorithm();
        updateStatus("Ready - Recommended: " + recommendedAlgorithm.getAlgorithmName());
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

        // File Size Components
       // fileSizeSlider = findViewById(R.id.fileSizeSlider);
        //fileSizeText = findViewById(R.id.fileSizeText);

        // File Type Dropdown
        //fileTypeDropdown = findViewById(R.id.fileTypeDropdown);
       // fileTypeDescription = findViewById(R.id.fileTypeDescription);

    }

    private void setupButtonListeners() {

        btnSelectFile.setOnClickListener(v -> {
            if (fileSelectionManager != null) {
                fileSelectionManager.openFilePicker();
            } else {
                Log.e(TAG, "fileSelectionManager is null!");
                showError("Initialisation Error", "File selection manager not initialised");
            }
        });
        btnEncrypt.setOnClickListener(v -> performEncryption());
        btnDecrypt.setOnClickListener(v -> performDecryption());
        btnBenchmark.setOnClickListener(v -> runComprehensiveBenchmark());

        // Cache control buttons
        MaterialButton btnCacheStats = findViewById(R.id.btnCacheStats);
        MaterialButton btnClearCache = findViewById(R.id.btnClearCache);

        if (btnCacheStats != null) {
            btnCacheStats.setOnClickListener(v -> showCachePerformance());
        }

        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> clearCache());
        }
    }

    private void setupAlgorithmDropdown() {
        List<String> algorithms = new ArrayList<>();

        for (InterfaceEncryptionAlgorithm algorithm : encryptionManager.getAvailableAlgorithms()) {
            algorithms.add(algorithm.getAlgorithmName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, algorithms);
        algorithmDropdown.setAdapter(adapter);

        if (!algorithms.isEmpty()) {
            // Set to recommended algorithm
            InterfaceEncryptionAlgorithm recommended = encryptionManager.getRecommendedAlgorithm();
            algorithmDropdown.setText(recommended.getAlgorithmName(), false);
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
        setupAlgorithmDropdown();
        setupDeviceInfo();
    }

    /**
     * Initialising encryption manager with this activity's context
     */
    private void setupEncryptionManager() {
        encryptionManager = new EncryptionManager(this);

        // Show recommended algorithm
        InterfaceEncryptionAlgorithm recommended = encryptionManager.getRecommendedAlgorithm();
        updateStatus("Initialised. Recommended algorithm: " + recommended.getAlgorithmName());
    }

    /**
     * Setup file size slider with predefined sizes
     */
//    private void setupFileSizeSlider() {
//        fileSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
//            int sizeMB = (int) value;
//            String sizeText;
//
//            if (sizeMB >= 1000) {
//                sizeText = String.format(Locale.US, "%.1f GB", sizeMB / 1000.0f);
//            } else {
//                sizeText = String.format(Locale.US, "%d MB", sizeMB);
//            }
//            fileSizeText.setText(sizeText);
//        });
//        fileSizeSlider.setValue(10);
//    }


    // === FILE SELECTION ===

    /**
     * Setup file type dropdown with supported file types and descriptions
     */
//    private void setupFileTypeDropdown() {
//        // Define supported file types with descriptions
//        Map<String, String> fileTypes = new LinkedHashMap<String, String>() {{
//            put("TXT", "Text file with readable content");
//            put("PDF", "PDF document with structured content");
//            put("JPG", "JPEG image file (simulated)");
//            put("PNG", "PNG image file (simulated)");
//            put("MP3", "Audio file (simulated)");
//            put("MP4", "Video file (simulated)");
//            put("DOCX", "Word document (simulated)");
//            put("ZIP", "Compressed archive (simulated)");
//        }};
//
//        List<String> fileTypeList = new ArrayList<>(fileTypes.keySet());
//
//        ArrayAdapter<String> fileTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, fileTypeList);
//        fileTypeDropdown.setAdapter(fileTypeAdapter);
//
//        // Update description when selection changes
//        fileTypeDropdown.setOnItemClickListener((parent, view, position, id) -> {
//            String selectedType = fileTypeList.get(position);
//            String description = fileTypes.get(selectedType);
//            fileTypeDescription.setText(description);
//        });
//
//        // Set initial description
//        fileTypeDescription.setText(fileTypes.get("TXT"));
//    }

    /**
     * File Selection Callback
     */
    @Override
    public void onFileSelected(FileSelectionManager.FileInfo fileInfo) {
        this.selectedFile = fileInfo;

        mainHandler.post(() -> {
            float sizeInMB = fileInfo.size / (1024.0f * 1024.0f);
            String fileInfoString = String.format(Locale.US,
                    "Selected: %s (%.2f MB)",
                    fileInfo.name, sizeInMB);

            if (fileInfoText != null) {
                fileInfoText.setText(fileInfoString);
            }
            if (fileSelectionCard != null){
                fileSelectionCard.setVisibility(View.VISIBLE);
            }
            updateStatus("File selected: " + fileInfo.name);
        });
    }

    @Override
    public void onFileSelectionError(String error) {
        mainHandler.post(() -> {
            updateStatus("File selection failed: " + error);
            showError("File Selection Error", error);
        });
    }

    /**
     * Update progress UI
     */
    private void updateProgressUI(int progress, double speedMBps, double remainingSec) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
            if (progressPercentage != null) {
                progressPercentage.setText(String.format("%d%%", progress));
            }
            if (speedMetric != null) {
                speedMetric.setText(String.format("%.1f MB/s", speedMBps));
            }

            if (timeRemaining != null) {
                if (remainingSec < 60) {
                    timeRemaining.setText(String.format("~%.0fs remaining", remainingSec));
                } else {
                    timeRemaining.setText(String.format("~%.1fm remaining", remainingSec / 60));
                }
            }
        });
    }


    /**
     * Upgrade progress message
     */
    private void updateProgress(String message, int progress) {
        runOnUiThread(() -> {
            if (progressTitle != null) {
                progressTitle.setText(message);
            }
            if (progressBar != null) {
                if (progress >= 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(progress);
                    if (progressPercentage != null) {
                        progressPercentage.setText(progress + "%");
                    }
                } else {
                    progressBar.setIndeterminate(true);
                    if (progressPercentage != null) {
                        progressPercentage.setText("");
                    }
                }
            }
        });
    }

    // === UI Methods ===

    private void updateProgressBar(long bytesProcessed, long totalBytes) {
        mainHandler.post(() -> {
            int progress = totalBytes > 0 ? (int) ((bytesProcessed * 100) / totalBytes) : 0;

            progressBar.setProgress(progress);
            progressPercentage.setText(progress + "%");

            double processedMB = bytesProcessed / (1024.0 * 1024.0);
            double totalMB = totalBytes / (1024.0 * 1024.0);

            // Calculate real-time throughput
            long currentTime = System.currentTimeMillis();
            if (lastProgressTime > 0) {
                double timeDiffSec = (currentTime - lastProgressTime) / 1000.0;
                double bytesDiff = bytesProcessed - lastProgressBytes;
                double instantThroughputMBps = (bytesDiff / (1024.0 * 1024.0)) / timeDiffSec;

                if (speedMetric != null) {
                    speedMetric.setText(String.format("%.1f MB/s", instantThroughputMBps));
                }

                // Estimate time remaining
                if (bytesProcessed > 0 && timeRemaining != null) {
                    double bytesPerMs = bytesProcessed / (double)(currentTime - operationsStartTime);
                    long remainingBytes = totalBytes - bytesProcessed;
                    double remainingSec = remainingBytes / bytesPerMs / 1000.0;

                    if (remainingSec < 60) {
                        timeRemaining.setText(String.format("~%.0fs", remainingSec));
                    } else {
                        timeRemaining.setText(String.format("~%.1fm", remainingSec / 60));
                    }
                }
            }

            lastProgressTime = currentTime;
            lastProgressBytes = bytesProcessed;

            String statusText = String.format("Processing: %.1f / %.1f MB (%d%%)",
                    processedMB, totalMB, progress);
            processStatus.setText(statusText);
        });
    }

    /**
     * Update status text on UI thread
     */
    private void updateStatus(String message) {
        mainHandler.post(() -> {
            if (processStatus != null) {
                processStatus.setText(message);
            }
        });
    }

    // Show errors
    private void showError(String title, String message) {
        mainHandler.post(() -> {
            Toast.makeText(MainActivity.this, title + ": " + message, Toast.LENGTH_LONG).show();
            updateStatus(title + ": " + message);
        });
    }

    /**
     * Show results
     */
    private void showResults(String message) {
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

    /**
     * Show/Hide Progress
     */
    private void showProgress(boolean show, String title, boolean isBenchmark) {
        mainHandler.post(() -> {
            progressCard.setVisibility(show ? View.VISIBLE : View.GONE);

            if (show && title != null) {
                progressTitle.setText(title);
            }

            if (show) {
                if (isBenchmark) {
                    // Hide MB/s and time remaining for benchmarks
                    if (speedMetric != null) {
                        speedMetric.setVisibility(View.GONE);
                    }
                    if(timeRemaining != null) {
                        timeRemaining.setVisibility(View.GONE);
                    }
                    // Show percentage for benchmark progress
                    if (progressPercentage != null) {
                        progressPercentage.setVisibility(View.VISIBLE);
                    }
                } else {
                    // Show everything for normal operations
                    if (speedMetric != null) {
                        speedMetric.setVisibility(View.VISIBLE);
                        speedMetric.setText("0 MB/s");
                    }
                    if (timeRemaining != null) {
                        timeRemaining.setVisibility(View.VISIBLE);
                        timeRemaining.setText("");
                    }
                    if (progressPercentage != null) {
                        progressPercentage.setVisibility(View.VISIBLE);
                    }
                }
            }

            if (!show) {
                progressBar.setProgress(0);
                progressBar.setIndeterminate(false); // Reset to determinate
                if (progressPercentage != null) {
                    progressPercentage.setText("0%");
                }
                // Restore visibility when hiding progress
                if (speedMetric != null) {
                    speedMetric.setVisibility(View.VISIBLE);
                    speedMetric.setText("0 MB/s");
                }
                if (timeRemaining != null) {
                    timeRemaining.setVisibility(View.VISIBLE);
                    timeRemaining.setText("");
                }
            } else {
                // When showing progress, start as indeterminate
                progressBar.setIndeterminate(true);
            }
        });
    }

    /**
     * Show detailed results
     */
    private void showDetailedResults(EncryptionResult result) {
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
        // Also show a toast for immediate feedback
//        Toast.makeText(this,
//                String.format("%s completed: %.1f MB/s", result.getOperation(), throughputMBps),
//                Toast.LENGTH_LONG).show();
    }

    /**
     * Enable/disable UI controls during operations
     */
    private void setUiEnabled(boolean enabled) {
        mainHandler.post(() -> {
            if (btnEncrypt != null) {
                btnEncrypt.setEnabled(enabled);
            }
            if (btnDecrypt != null) {
                btnDecrypt.setEnabled(enabled);
            }
            if (btnBenchmark != null) {
                btnBenchmark.setEnabled(enabled);
            }
            if (algorithmDropdown != null) {
                algorithmDropdown.setEnabled(enabled);
            }
            if (fileSizeSlider != null) {
                fileSizeSlider.setEnabled(enabled);
            }
        });
    }


    // === ENCRYPTION/DECRYPTION ===

    /**
     * Perform Encryption on background thread
     */
    private void performEncryption() {
        if (selectedFile == null) {
            showError("No File Selected", "Please select a file first");
            return;
        }

        setUiEnabled(false);
        showProgress(true, "Encrypting...", false);

        // Reset progress tracking
        operationsStartTime = System.currentTimeMillis();
        lastProgressTime = operationsStartTime;
        lastProgressBytes = 0;

        executorService.execute(() -> {
            try {
                // Get selected algorithm
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(algorithmName);

                if (algorithm == null) {
                    showError("Algorithm Error", "Algorithm not found: " + algorithmName);
                    return;
                }

                algorithm.setProgressCallback(this::updateProgressBar);

                File inputFile = selectedFile.tempFile;
                String outputFileName = "encrypted_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".raziel";
                File outputFile = getOutputFileInDownloads(outputFileName);

                EncryptionResult result = encryptionManager.encryptFile(inputFile, algorithm, outputFile);

                if (result.isSuccess() && selectedFile.tempFile != null && selectedFile.tempFile.exists()) {
                    boolean deleted = selectedFile.tempFile.delete();
                    Log.d(TAG, "Deleted temp input file: " + deleted + " - " + selectedFile.tempFile.getAbsolutePath());
                }

                // Debug
                Log.d("EncryptionDebug", "=== ENCRYPTION RESULT ===");
                Log.d("EncryptionDebug", "Success: " + result.isSuccess());
                Log.d("EncryptionDebug", "File size: " + result.getFileSizeBytes());
                Log.d("EncryptionDebug", "Time: " + result.getProcessingTimeMs());
                Log.d("EncryptionDebug", "Algorithm: " + result.getAlgorithmName());
                Log.d("EncryptionDebug", "Operation: " + result.getOperation());
                Log.d("EncryptionDebug", "Error: " + result.getErrorMessage());
                Log.d("EncryptionDebug", "Output file: " + result.getOutputFile().getAbsolutePath());
                if (result.getOutputFile() != null && result.getOutputFile().exists()) {
                    Log.d("EncryptionDebug", "Output file exists: " + result.getOutputFile().exists());
                    Log.d("EncryptionDebug", "Output file size: " + result.getOutputFile().length());
                }

                algorithm.setProgressCallback(null);

                mainHandler.post(() -> {
                    if (result.isSuccess()) {
                        lastEncryptedFile = result.getOutputFile();
                        showDetailedResultsWithCache(result);

                        Log.d("UI_Debug", "About to call showDetailedResults");
                        //showDetailedResults(result);
                        Log.d("UI_Debug", "After showDetailedResults");
                        //updateStatus("Encryption completed successfully");

                        // Log file locations for debugging
                        Log.d("FileLocations", "Original temp file deleted: " + selectedFile.tempFile.getAbsolutePath());
                        Log.d("FileLocations", "Encrypted: " + lastEncryptedFile.getAbsolutePath());

                    } else {
                        showError("Encryption Failed", result.getErrorMessage());
                    }
                    setUiEnabled(true);
                    showProgress(false, "", false);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    showResults("Encryption failed: " + e.getMessage());
                    setUiEnabled(true);
                    showProgress(false, "", false);
                });
            }
        });
    }

    /**
     * Perform decryption on background thread
     */
    private void performDecryption() {
        if (lastEncryptedFile == null || !lastEncryptedFile.exists()) {
            updateStatus("Use Encrypt first to create a file for decryption");
            return;
        }
        setUiEnabled(false);
        showProgress(true, "Decrypting...", false);

        executorService.execute(() -> {
            try {
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(algorithmName);

                if (algorithm == null) {
                    showError("Algorithm Error", "Algorithm not found: " + algorithmName);
                    return;
                }

                algorithm.setProgressCallback(this::updateProgressBar);

                String outputFileName = "decrypted_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".dec";
                File outputFile = getOutputFileInDownloads(outputFileName);
                EncryptionResult result = encryptionManager.decryptFile(lastEncryptedFile, algorithm, outputFile);

                // Debug
                Log.d("DecryptionDebug", "=== DECRYPTION RESULT ===");
                Log.d("DecryptionDebug", "Success: " + result.isSuccess());
                Log.d("DecryptionDebug", "File size: " + result.getFileSizeBytes());
                Log.d("DecryptionDebug", "Time: " + result.getProcessingTimeMs());
                Log.d("DecryptionDebug", "Algorithm: " + result.getAlgorithmName());
                Log.d("DecryptionDebug", "Operation: " + result.getOperation());
                Log.d("DecryptionDebug", "Error: " + result.getErrorMessage());
                Log.d("DecryptionDebug", "Output file: " + result.getOutputFile());
                Log.d("DecryptionDebug", "Output file exists: " + (result.getOutputFile() != null && result.getOutputFile().exists()));
                if (result.getOutputFile() != null) {
                    Log.d("DecryptionDebug", "Output file exists: " + result.getOutputFile().exists());
                    Log.d("DecryptionDebug", "Output file size: " + result.getOutputFile().length());
                }

                algorithm.setProgressCallback(null);

                mainHandler.post(() -> {
                    if (result.isSuccess()) {
                        showDetailedResults(result);
                        //updateStatus("Decryption competed successfully");
                    } else {
                        showError("Decryption Failed", result.getErrorMessage());
                    }
                    setUiEnabled(true);
                    showProgress(false, "", false);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    showResults("Decryption error: " + e.getMessage());
                    setUiEnabled(true);
                    showProgress(false, "", false);
                });
            }
        });
    }


    // === BENCHMARK ===

    private void displayBenchmarkResults(Map<String, EncryptionBenchmark.ComprehensiveBenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPREHENSIVE BENCHAMRK RESULTS ===\n\n");

        for (EncryptionBenchmark.ComprehensiveBenchmarkResult result : results.values()) {
            sb.append(result.toString()).append("\n");
        }

        // Compare results
        if (results.size() == 2) {
            List<EncryptionBenchmark.ComprehensiveBenchmarkResult> resultList = new ArrayList<>(results.values());

            EncryptionBenchmark.BenchmarkComparison comparison = EncryptionBenchmark.compareAlgorithms(resultList.get(0), resultList.get(1));
            sb.append("\n").append(comparison.toString());
        }
        showResults(sb.toString());
    }

    private void runComprehensiveBenchmark() {
        setUiEnabled(false);
        showProgress(true, "Running Comprehensive Benchmark...", true);

        // Track progress
        encryptionBenchmark.setProgressCallback(new EncryptionBenchmark.BenchmarkProgressCallback() {
            @Override
            public void onBenchmarkProgress(int currentStep, int totalSteps, String currentOperation) {
                int progress = totalSteps > 0 ? (int) ((currentStep * 100) / (double) totalSteps) : 0;
                updateProgress(currentOperation, progress);
            }
        });

        executorService.execute(() -> {
            try {
                List<InterfaceEncryptionAlgorithm> algorithms = encryptionManager.getAvailableAlgorithms();
                Map<String, EncryptionBenchmark.ComprehensiveBenchmarkResult> results = encryptionBenchmark.runComprehensiveBenchmark(algorithms);

                mainHandler.post(() -> {
                    encryptionBenchmark.setProgressCallback(null);
                    displayBenchmarkResults(results);
                    setUiEnabled(true);
                    showProgress(false, "", true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    encryptionBenchmark.setProgressCallback(null);
                    showError("Benchmark Failed", e.getMessage());
                    setUiEnabled(true);
                    showProgress(false, "", true);
                });
            }
        });
    }

    // === CACHE BENCHMARK
    /**
     * Show cache performance metrics
     */
    private void showCachePerformance() {
        CacheManager.CacheStats stats = encryptionManager.getCacheStats();

        String cacheMessage = String.format(Locale.US,
                "=== CACHE PERFORMANCE ===\n\n" +
                        "Keyset Cache: %d hits, %d misses (%.1f%%)\n", //+
                       // "Cipher Cache: %d hits, %d misses (%.1f%%)\n" +
                       // "Key Derivation: %d hits, %d misses (%.1f%%)\n\n" +
                       // "Performance Impact:\n" +
                       // "• Keyset generation: %dms vs <1ms cached\n" +
                        //"• Cipher creation: %dms vs <1ms cached\n" +
                        //"• Overall speedup: ~%dx faster",
                stats.keysetHits, stats.keysetMisses, stats.keysetHitRate //,
               // stats.cipherHits, stats.cipherMisses, stats.cipherHitRate,
               // stats.keyDerivationHits, stats.keyDerivationMisses, stats.keyDerivationHitRate,
                //stats.avgKeysetGenerationTimeMs,
               // stats.avgCipherCreationTimeMs,
               // Math.max(10, stats.avgKeysetGenerationTimeMs / 10) // Estimated speedup
        );

        showResults(cacheMessage);
    }

    /**
     * Clear all caches and show impact
     */
    private void clearCache() {
        if (encryptionManager != null) {
            encryptionManager.cleanup(); // This should call cacheManager.clearAll()
            updateStatus("All caches cleared. Next operations will be slower.");

            // Show cache stats after clear
            mainHandler.postDelayed(this::showCachePerformance, 1000);
        }
    }

    /**
     * Enhanced detailed results with cache info
     */
    private void showDetailedResultsWithCache(EncryptionResult result) {
        double fileSizeMB = result.getFileSizeBytes() / (1024.0 * 1024.0);
        double timeSec = result.getProcessingTimeMs() / 1000.0;
        double throughputMBps = fileSizeMB / timeSec;

        // Get cache stats
        CacheManager.CacheStats cacheStats = encryptionManager.getCacheStats();

        @SuppressLint("DefaultLocale") String message = String.format(
                "ENCRYPTION SUCCESSFUL\n\n" +
                        "Performance Metrics:\n" +
                        "• File Size: %.2f MB\n" +
                        "• Time: %.2f seconds\n" +
                        "• Throughput: %.2f MB/s\n" +
                        "• Algorithm: %s\n\n" +
                        "Cache Performance:\n" +
                        "• Keyset: %d/%d (%.1f%% hit rate)\n" +
                      //  "• Cipher: %d/%d (%.1f%% hit rate)\n" +
                       // "• Estimated cache speedup: ~%dx\n\n" +
                        "Device: %s (%d cores)",
                fileSizeMB, timeSec, throughputMBps,
                result.getAlgorithmName(),
                cacheStats.keysetHits, cacheStats.keysetHits + cacheStats.keysetMisses, cacheStats.keysetHitRate,
               // cacheStats.cipherHits, cacheStats.cipherHits + cacheStats.cipherMisses, cacheStats.cipherHitRate,
               // Math.max(10, cacheStats.avgKeysetGenerationTimeMs / 10),
                chipDevice.getText(),
                Runtime.getRuntime().availableProcessors()
        );

        showResults(message);
    }

    // Add a button to show cache stats
    private void addCacheStatsButton() {
        MaterialButton btnCacheStats = findViewById(R.id.btnCacheStats);
        if (btnCacheStats != null) {
            btnCacheStats.setOnClickListener(v -> showCachePerformance());
        }
    }


    // === UTILITY METHODS ===

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

    // Get easier access on real hardware device testing
    private File getOutputFileInDownloads(String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        return new File(downloadsDir, fileName);
    }
}

