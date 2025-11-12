package com.example.raziel.ui.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.raziel.R;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main Activity for testing encryption performance optimisations
 *
 * Key Architecture Decisions:
 * Background thread execution that prevents Application Not Responding
 * Handled-based UI updates for thread-safe communication
 *
 * Features:
 * 1. Real-time performance visualisation
 * 2. Chunking for large files (100MB, 500MB, 1GB)
 * 3. Material Design 3 UI (for minimal performance impact and ease of use)
 * 4. Progress tracking with time estimation
 * 5. Device capability detection
 */
public class MainActivity extends AppCompatActivity {
    // UI Components
    private AutoCompleteTextView algorithmDropdown;
    private MaterialButton btnEncrypt, btnDecrypt, btnBenchmark;
    private LinearProgressIndicator progressBar;
    private TextView processStatus, progressTitle, progressPercentage;
    private TextView speedMetric, timeRemaining, fileSizeText;
    private MaterialCardView progressCard, resultsCard;
    private Slider fileSizeSlider;
    private Chip chipDevice, chipMemory, chipCores, chipHardwareStatus;

    // Core components
    private EncryptionManager encryptionManager;
    private File testFile;
    // Track last encryption output to test decryption function
    private File lastEncryptedFile;

    // Performance Tracking
    //private long startTime;
    //private AtomicLong bytesProcessed = new AtomicLong(0);
    //private long totalBytesProcessed = 0;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    //private Runnable progressUpdater;

    //private static final int MAX_THREADS = 4; // For parallel processing
    //private ExecutorService executorService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupEncryptionManager();
        initialiseViews();
        setupFileSizeSlider();

        //Initialise thread pool for large file processing
        //executorService = Executors.newFixedThreadPool(MAX_THREADS);
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
     * Initialise all UI components
     */
    private void initialiseViews() {
        // Algorithm Selection
        algorithmDropdown = findViewById(R.id.algorithmDropdown);

        // Buttons
        btnEncrypt = findViewById(R.id.btnEncrypt);
        btnDecrypt = findViewById(R.id.btnDecrypt);
        btnBenchmark = findViewById(R.id.btnBenchmark);
        //btnCleanup = findViewById(R.id.btnCleanuo);

        // Progress Components
        progressBar = findViewById(R.id.progressBar);
        progressCard = findViewById(R.id.progressCard);
        resultsCard = findViewById(R.id.resultsCard);
        progressTitle = findViewById(R.id.progressTitle);
        progressPercentage = findViewById(R.id.progressPercentage);
        processStatus = findViewById(R.id.processStatus);

        speedMetric = findViewById(R.id.speedMetric);
        timeRemaining = findViewById(R.id.timeRemaining);

        // Device info chips
        chipDevice = findViewById(R.id.chipDevice);
        chipMemory = findViewById(R.id.chipMemory);
        chipCores = findViewById(R.id.chipCores);
        chipHardwareStatus = findViewById(R.id.chipHardwareStatus);

        // Setup device information
        setupDeviceInfo();

        // File Size Components
        fileSizeSlider = findViewById(R.id.fileSizeSlider);
        fileSizeText = findViewById(R.id.fileSizeText);

        // Setup algorithm dropdown
        List<String> algorithms = new ArrayList<>();
        for (InterfaceEncryptionAlgorithm algorithm : encryptionManager.getAvailableAlgorithms()) {
            algorithms.add(algorithm.getAlgorithmName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, algorithms);
        algorithmDropdown.setAdapter(adapter);
        if (!algorithms.isEmpty()) {
            algorithmDropdown.setText(algorithms.get(0), false);
        }
        //algorithmDropdown.setText(algorithms.get(0), false);

        // Button listeners
        btnEncrypt.setOnClickListener(v -> performEncryption());
        btnDecrypt.setOnClickListener(v -> performDecryption());
        btnBenchmark.setOnClickListener(v -> runFullBenchmark());
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
            long totalMemoryMB = memoryInfo.totalMem / (1024 * 1024);
            chipMemory.setText(String.format(Locale.US, "%dMB RAM", totalMemoryMB / 1024));
        }

        // CPU cores
        int availableProcesors = Runtime.getRuntime().availableProcessors();
        chipCores.setText(String.format(Locale.US, "%d Cores", availableProcesors));

        // Hardware Acceleration status
        chipHardwareStatus.setText("Available");
    }


    /**
     * Setup file size slider with predefined sizes
     */
    private void setupFileSizeSlider() {
        fileSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            int sizeMB = (int) value;
            String sizeText;

            if (sizeMB >= 1000) {
                sizeText = String.format(Locale.US, "%.1f GB", sizeMB / 1000.0f);
            } else {
                sizeText = String.format(Locale.US, "%d MB", sizeMB);
            }

            fileSizeText.setText(sizeText);
        });

        fileSizeSlider.setValue(10);
    }


    /**
     * Stop progress monitoring
     */
//    private void stopProgressionMonitoring() {
//        progressHandler.removeCallbacksAndMessages(null);
//        if (progressUpdater != null) {
//            progressHandler.removeCallbacks(progressUpdater);
//            progressUpdater = null;
//        }
//    }


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

    private void updateProgressBar(long bytesProcessed, long totalBytes) {
        int progress = totalBytes > 0 ? (int) ((bytesProcessed * 100) / totalBytes) : 0;
        progressBar.setProgress(progress);

        double processedMB = bytesProcessed / 1024.0 / 1024.0;
        double totalMB = totalBytes / 1024.0 / 1024.0;

        String statusText = String.format("Processing: %.1f / %.1f MB (%d%%)",
                processedMB, totalMB, progress);
        processStatus.setText(statusText);
    }

    /**
     * Update status text on UI thread
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (processStatus != null) {
                processStatus.setText(message);
            }
        });
    }

    /**
     * Show results
     */
    private void showResults(String message) {
        runOnUiThread(() -> {
            if (processStatus != null) {
                processStatus.setText(message);
            }
            if (resultsCard != null) {
                resultsCard.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Show detailed results
     */
    private void showDetailedResults(EncryptionResult result) {
        long fileSizeMB = result.getFileSizeBytes() / (1024 * 1024);
        double timeSec = result.getProcessingTimeMs() / 1000.0;
        double throughputMBps = fileSizeMB / timeSec;

        @SuppressLint("DefaultLocale") String message = String.format(
                "SUCCESSFUL\n\n" +
                        "Operation: %s\n" +
                        "Algorithm: %s\n" +
                        "File Size: %d MB\n" +
                        "Time: %.2f seconds\n" +
                        "Throughput: %.2f MB/s\n" +
                        "Device: %s\n" +
                        "Cores Used: %d",
                result.getOperation(),
                result.getAlgorithmName(),
                fileSizeMB, timeSec, throughputMBps,
                chipDevice != null ? chipDevice.getText() : "Unknown",
                Runtime.getRuntime().availableProcessors()
        );

        showResults(message);
    }

    /**
     * Show/Hide Progress
     */
    private void showProgress(boolean show) {
        runOnUiThread(() -> {
            if (progressCard != null) {
                progressCard.setVisibility(show ? View.VISIBLE : View.GONE);
            }

            if (!show && progressBar != null) {
                progressBar.setProgress(0);
                if (progressPercentage != null) {
                    progressPercentage.setText("0%");
                }
                if (speedMetric != null) {
                    speedMetric.setText("0 MB/s");
                }
                if (timeRemaining != null) {
                    timeRemaining.setText("");
                }
            }
        });
    }

    /**
     * Enable/disable UI controls during operations
     */
    private void setUiEnabled(boolean enabled) {
        runOnUiThread(() -> {
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

    /**
     * Run full benchmark
     */
    private void runFullBenchmark() {
        showResults("Starting comprehensive benchmark...");
        updateStatus("Benchmark functionality will be implemented...");
    }

    /**
     * Clean up resources when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (encryptionManager != null) {
            encryptionManager.cleanup();
        }
        progressHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Create test file with specified size
     */
    private void createTestFile(int sizeMB) throws IOException {
        testFile = new File(getFilesDir(), "test_file_" + sizeMB + "mb.dat");

        updateProgress("Creating " + sizeMB + "MB test file...", -1);

        // Use larger buffer for file creation
        final int BUFFER_SIZE = 1024 * 1024; //1MB buffer
        byte[] buffer = new byte[BUFFER_SIZE];

        // Fill buffer with test data
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i % 256);
        }

        long targetBytes = (long) sizeMB * 1024 * 1024;
        long written = 0;

        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(testFile), BUFFER_SIZE)) {

            while (written < targetBytes) {
                int toWrite = (int) Math.min(BUFFER_SIZE, targetBytes - written);
                bos.write(buffer, 0, toWrite);
                written += toWrite;

                // Update progress
                int progress = (int) ((written * 100) / targetBytes);
                updateProgress("Creating test file...", progress);
            }
        }
    }

    /**
     * Perform Encryption
     */
    private void performEncryption() {
        setUiEnabled(false);
        showProgress(true);
        updateStatus("Starting encryption...");

        new Thread(() -> {
            try {
                // Get file size from slider
                int sizeMB = (int) fileSizeSlider.getValue();

                // Create test file
                createTestFile(sizeMB);

                // Get selected algorithm
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);

                if (algorithm == null) {
                    runOnUiThread(() -> {
                        updateStatus("Algorithm not found: " + algorithmName);
                        setUiEnabled(true);
                        showProgress(false);
                    });
                    return;
                }

                // Set up progress callback
                algorithm.setProgressCallback((bytesProcessed, totalBytes) ->
                        runOnUiThread(() -> updateProgressBar(bytesProcessed, totalBytes)));

                long startTime = System.currentTimeMillis();
                EncryptionResult result = encryptionManager.encryptFile(testFile, algorithm, "encrypted_file.enc");
                long endTime = System.currentTimeMillis();

                // Clear the callback
                algorithm.setProgressCallback(null);

                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        lastEncryptedFile = result.getOutputFile();
                        handleEncryptionResult(result);
                    } else {
                        updateStatus("Encryption Failed: " + result.getErrorMessage());
                    }
                    setUiEnabled(true);
                    showProgress(false);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showResults("Encryption failed: " + e.getMessage());
                    setUiEnabled(true);
                    showProgress(false);
                });
            } finally {
                // Clean up test file
                if (testFile != null && testFile.exists()) {
                    testFile.delete();
                }
            }
        }).start();
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
        showProgress(true);
        updateStatus("Starting decryption...");

        new Thread(() -> {
            try {
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);

                if (algorithm == null) {
                    runOnUiThread(() -> {
                        showResults("Algorithm not found: " + algorithmName);
                        setUiEnabled(true);
                        showProgress(false);
                    });
                    return;
                }

                // Set up progress callback
                algorithm.setProgressCallback((bytesProcessed, totalBytes) ->
                        runOnUiThread(() -> updateProgressBar(bytesProcessed, totalBytes)));

                EncryptionResult result = encryptionManager.decryptFile(lastEncryptedFile, algorithm, "decrypted_file.dat");

                // Clear the callback
                algorithm.setProgressCallback(null);

                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        showDetailedResults(result);
                    } else {
                        showResults("Decryption Failed: " + result.getErrorMessage());
                    }
                    setUiEnabled(true);
                    showProgress(false);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showResults("Decryption error: " + e.getMessage());
                    setUiEnabled(true);
                    showProgress(false);
                });
            }
        }).start();
    }

    /**
     * Handling encryption/decryption result and displaying detailed metrics
     */
    private void handleEncryptionResult(EncryptionResult result) {
        if (result.isSuccess()) {
            // Calculate throughput to validate performance
            long fileSizeMB = result.getFileSizeBytes() / (1024 * 1024);
            double timeSec = result.getProcessingTimeMs() / 1000.0;
            double throughputMBs = timeSec > 0 ? fileSizeMB / timeSec : 0;

            String operation = result.getOperation() != null ? result.getOperation().toString() : "Unknown";
            String algorithm = result.getAlgorithmName() != null ? result.getAlgorithmName() : "Unknown";
            String inputFile = result.getInputFile() != null ? result.getInputFile().getName() : "Unknown";
            String outputFile = result.getOutputFile() != null ? result.getOutputFile().getName() : "Unknown";

            // Use simpler string building to avoid formatting issues
            String message = "SUCCESSFUL!\n\n" +
                    "Operation: " + operation + "\n" +
                    "Algorithm: " + algorithm + "\n" +
                    "File Size: " + fileSizeMB + " MB\n" +
                    "Processing Time: " + result.getProcessingTimeMs() + " ms\n" +
                    "Throughput: " + String.format(Locale.US, "%.2f", throughputMBs) + " MB/s\n\n" +
                    "Input: " + inputFile + "\n" +
                    "Output: " + outputFile;

            updateStatus(message);

        } else {
            String operation = result.getOperation() != null ? result.getOperation().toString() : "Unknown";
            String error = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error";
            String inputFile = result.getInputFile() != null ? result.getInputFile().getName() : "Unknown";

            updateStatus(operation + " FAILED \n\nError: " + error + "\n\n File: " + inputFile);
        }
    }



    /**
     * Start progress monitoring with real-time updates
     */
//    private void startProgressMonitoring(long totalBytes, String operation) {
//        totalBytesProcessed = totalBytes;
//        bytesProcessed.set(0);
//        startTime = SystemClock.elapsedRealtime();
//
//        // Set appropriate progress title based on operation
//        updateProgress(operation + "...", 0);
//
//        progressUpdater = new Runnable() {
//            @Override
//            public void run() {
//                long currentBytes = bytesProcessed.get();
//                long elapsedMs = SystemClock.elapsedRealtime() - startTime;
//                double elapsedSec = elapsedMs / 1000.0;
//
//                // Calculate progress
//                int progress = totalBytesProcessed > 0 ?
//                        (int) ((currentBytes * 100) / totalBytesProcessed) : 0;
//
//                // Calculate speed
//                double speedMBps = 0;
//                if (elapsedSec > 0) {
//                    speedMBps = (currentBytes / (1024.0 * 1024.0)) / elapsedSec;
//                }
//
//                // Estimate time remaining
//                double remainingSec = 0;
//                if (speedMBps > 0 && currentBytes < totalBytesProcessed) {
//                    double remainingMB = (totalBytesProcessed - currentBytes) / (1024.0 * 1024.0);
//                    remainingSec = remainingMB / speedMBps;
//                }
//
//                // Update UI
//                updateProgressUI(progress, speedMBps, remainingSec);
//
//                // Continue updating if not complete
//                if (currentBytes < totalBytesProcessed) {
//                    progressHandler.postDelayed(this, 100); // update every 100ms
//                }
//            }
//        };
//
//        progressHandler.post(progressUpdater);
//    }


    /**
     * Updates bytes processed (this is called during encryption/decryption)
     */
    //public void updateBytesProcessed(long bytes) {
        //bytesProcessed.set(bytes);
    //}

    /**
     * Get algorithm names for spinner display
     *
     * @return Get algorithm names
     */
//    private String[] getAlgorithmNames() {
//        return encryptionManager.getAvailableAlgorithms().stream().map(InterfaceEncryptionAlgorithm::getAlgorithmName).toArray(String[]::new);
//    }


    /**
     * Update status text on UI thread
     */
//    private void updateStatus(String message) {
//        runOnUiThread(() -> {
//            if (processStatus != null) {
//                processStatus.setText(message);
//            }
//        });
//    }


    /**
     * Show results
     */
//    private void showResults(String message) {
//        runOnUiThread(() -> {
//            if (processStatus != null) {
//                processStatus.setText(message);
//            }
//            if (resultsCard != null) {
//                resultsCard.setVisibility(View.VISIBLE);
//            }
//        });
//    }


    /**
     * Show detailed results
     */
//    private void showDetailedResults(EncryptionResult result) {
//        long fileSizeMB = result.getFileSizeBytes() / (1024 * 1024);
//        double timeSec = result.getProcessingTimeMs() / 1000.0;
//        double throughputMBps = fileSizeMB / timeSec;
//
//        @SuppressLint("DefaultLocale") String message = String.format(
//                "SUCCESSFUL\n\n" +
//                        "Operation: %s\n" +
//                        "Algorithm: %s\n" +
//                        "File Size: %d MB\n" +
//                        "Time: %.2f seconds\n" +
//                        "Throughput: %.2f MB/s\n" +
//                        "Device: %s\n" +
//                        "Cores Used: %d",
//                result.getOperation(),
//                result.getAlgorithmName(),
//                fileSizeMB, timeSec, throughputMBps,
//                chipDevice != null ? chipDevice.getText() : "Unknown",
//                Runtime.getRuntime().availableProcessors()
//        );
//
//        showResults(message);
//    }
//
//
//    /**
//     * Show/Hide Progress
//     */
//    private void showProgress(boolean show) {
//        runOnUiThread(() -> {
//            if (progressBar != null) {
//                progressCard.setVisibility(show ? View.VISIBLE : View.GONE);
//            }
//
//            if (!show && progressBar != null) {
//                progressBar.setProgress(0);
//                if (progressPercentage != null) {
//                    progressPercentage.setText("0%");
//                }
//                if (speedMetric != null) {
//                    speedMetric.setText("0 MB/s");
//                }
//                if (timeRemaining != null) {
//                    timeRemaining.setText("");
//                }
//            }
//        });
//    }
//
//
//    /**
//     * Enable/disable UI controls during operations
//     */
//    private void setUiEnabled(boolean enabled) {
//        runOnUiThread(() -> {
//            if (btnEncrypt != null) {
//                btnEncrypt.setEnabled(enabled);
//            }
//            if (btnDecrypt != null) {
//                btnDecrypt.setEnabled(enabled);
//            }
//            if (btnBenchmark != null) {
//                btnBenchmark.setEnabled(enabled);
//            }
//            if (algorithmDropdown != null) {
//                algorithmDropdown.setEnabled(enabled);
//            }
//            if (fileSizeSlider != null) {
//                fileSizeSlider.setEnabled(enabled);
//            }
//        });
//    }
//
//
//    /**
//     * Run full benchmark
//     */
//    private void runFullBenchmark() {
//        showResults("Starting comprehensive benchmark...");
//        //TODO: Implement benchmark class
//        updateStatus("Benchmark functionality will be implemented...");
//    }
//
//
//    /**
//     * Clean up resources when activity is destroyed
//     *
//     * Ensures proper clean up of:
//     * ThreadLocal cipher instances
//     * Cached cryptographic material
//     * Native resources
//     */
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//
////        if(executorService != null) {
////            executorService.shutdown();
////        }
//
//        if (encryptionManager != null) {
//            encryptionManager.cleanup();
//        }
//        stopProgressionMonitoring();
//    }
//
//
//    /**
//     * Create test file with specified size
//     */
//    private void createTestFile(int sizeMB) throws IOException {
//       testFile = new File(getFilesDir(), "test_file_" + sizeMB + "mb.dat");
//
//       updateProgress("Creating " + sizeMB + "MB test file...", -1);
//
//       // Use larger buffer for file creation
//       final int BUFFER_SIZE = 1024 * 1024; //1MB buffer
//       byte[] buffer = new byte[BUFFER_SIZE];
//
//       // Fill buffer with test data
//       for (int i = 0; i < buffer.length; i++) {
//           buffer[i] = (byte) (i % 256);
//       }
//
//       long targetBytes = (long) sizeMB * 1024 * 1024;
//       long written = 0;
//
//       try (BufferedOutputStream bos = new BufferedOutputStream(
//               new FileOutputStream(testFile), BUFFER_SIZE)) {
//
//           while (written < targetBytes) {
//               int toWrite = (int) Math.min(BUFFER_SIZE, targetBytes - written);
//               bos.write(buffer, 0, toWrite);
//               written += toWrite;
//
//               // Update progress
//               int progress = (int) ((written * 100) / targetBytes);
//               updateProgress("Creating test file...", progress);
//           }
//       }
//    }
//
//
//
//    /**
//     * Perform Encryption
//     */
//    private void performEncryption() {
//        if (testFile == null || !testFile.exists()) {
//            updateStatus("Test file not available");
//            return;
//        }
//
//        setUiEnabled(false);
//        showProgress(true);
//        updateStatus("Starting encryption...");
//
//        new Thread(() -> {
//            try {
//                // Get file size from slider
//                int sizeMB = (int) fileSizeSlider.getValue();
//
//                // Create test file
//                createTestFile(sizeMB);
//
//                // Get selected algorithm
//                String algorithmName = algorithmDropdown.getText().toString();
//                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);
//
//                algorithm.setProgressCallback((bytesProcessed, totalBytesProcessed) ->
//                        runOnUiThread(() -> updateProgressBar(bytesProcessed, totalBytesProcessed)));
//
//                long startTime = System.currentTimeMillis();
//                EncryptionResult result = encryptionManager.encryptFile(testFile, algorithm, null);
//                long endTime = System.currentTimeMillis();
//
//                new Handler(Looper.getMainLooper()).post(() -> {
//                    if (result.isSuccess()) {
//                        encryptedFile = result.getOutputFile();
//                        handleEncryptionResult(result);
//                    } else {
//                        updateStatus("Encryption Failed: " + result.getErrorMessage());
//                    }
//                    setUiEnabled(true);
//                });
//
//            } catch (Exception e) {
//                runOnUiThread(() -> {
//                    showResults("Encryption failed: " + e.getMessage());
//                    setUiEnabled(true);
//                    showProgress(false);
//                });
//            } finally {
//                // Clean up test file
//                if (testFile != null && testFile.exists()) {
//                    testFile.delete();
//                }
//            }
//        }).start();
//    }
//
//
//    /**
//     * Perform decryption on background thread
//     *
//     * Note: Currently the encryption requires to be run first to have the file decrypted
//     * TODO: Add file selector to select any file to encrypt
//     */
//    private void performDecryption() {
//        if (lastEncryptedFile == null || !lastEncryptedFile.exists()) {
//            // First must be encrypted
//            //TODO: At the moment just testing file encrypt/decrypt, add file selection to decrypt whenever
//            //TODO: not only after an encryption
//            updateStatus("Use Encrypt first, proper file selection will be added in the future");
//            return;
//        }
//
//        setUiEnabled(false);
//        showProgress(true);
//
//        new Thread(() -> {
//            try {
//                String algorithmName = algorithmDropdown.getText().toString();
//                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);
//
//                if (algorithm == null) {
//                    runOnUiThread(() -> {
//                        showResults("Algorithm not found: " + algorithmName);
//                        setUiEnabled(true);
//                        showProgress(false);
//                    });
//                    return;
//                }
//
//                // Start progress monitor
//                startProgressMonitoring(lastEncryptedFile.length(), "Decrypting");
//
//                //Set up progress callback
//                algorithm.setProgressCallback(new InterfaceEncryptionAlgorithm.ProgressCallback() {
//                    @Override
//                    public void onProgressUpdate(long bytesProcessed, long totalBytes) {
//                        // Update the bytes processed on the main thread
//                        runOnUiThread(() -> updateBytesProcessed(bytesProcessed));
//                    }
//                });
//
//                EncryptionResult result = encryptionManager.decryptFile(lastEncryptedFile, algorithm, null);
//
//                algorithm.setProgressCallback(null);
//                stopProgressionMonitoring();
//
//                if (result.isSuccess()) {
//                    showDetailedResults(result);
//                } else {
//                    showResults("Decryption Failed: " + result.getErrorMessage());
//                }
//
//            } catch (Exception e) {
//                showResults("Decryption error: " + e);
//            } finally {
//                runOnUiThread(() -> {
//                    setUiEnabled(true);
//                    showProgress(false);
//                });
//            }
//        }).start();
//    }
//
//
//    /**
//     * Handling encryption/decryption result and displaying detailed metrics
//     *
//     * These help to validate that optimisations are working by:
//     * Showing processing time
//     * Showing throughput
//     * Showing success or not
//     *
//     * @param result Passing the result of the encryption to be processed for display
//     */
//    private void handleEncryptionResult(EncryptionResult result) {
//        if (result.isSuccess()) {
//            // Calculate throughput to validate performance
//            long fileSizeMB = result.getFileSizeBytes() / 1024 / 1024;
//            double timeSec = result.getProcessingTimeMs() / 1000.0;
//            double throughputMBs = fileSizeMB / timeSec;
//
//            @SuppressLint("DefaultLocale")
//            String message = String.format(
//                    "%s SUCCESSFUL!\n\n" +
//                            "Operation: %s\n" +
//                            "Algorithm: %s\n" +
//                            "File Size: %d MB\n" +
//                            "Processing Time: %d ms\n" +
//                            "Throughput: %.2f MB/s\n\n" +
//                            "Input: %s\n" +
//                            "Output: %s\n\n",
//                    result.getOperation(),
//                    result.getAlgorithmName(),
//                    fileSizeMB,
//                    result.getProcessingTimeMs(),
//                    throughputMBs,
//                    result.getInputFile().getName(),
//                    result.getOutputFile().getName());
//            updateStatus(message);
//
//        } else {
//            updateStatus(String.format(
//                    "%s FAILED \n\nError: %s\n\n File: %s",
//                    result.getOperation(),
//                    result.getErrorMessage(),
//                    result.getInputFile().getName()
//            ));
//        }
//    }
}

