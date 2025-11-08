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
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private long startTime;
    private long bytesProcessed;
    private Handler progressHandler = new Handler(Looper.getMainLooper());

    // Chunking for Large Files
    private static final int CHUNK_SIZE_MB = 10; // Process in 10MB chunks large files
    private static final int MAX_THREADS = 4; // For parallel processing
    private ExecutorService executorService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupEncryptionManager();
        initialiseViews();
        detectDeviceCapabilities();
        setupFileSizeSlider();

        //Initialise thread pool for large file processing
        executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * Initialising encryption manager with this activity's context
     *
     * Context is important because it allows EncryptionManager to:
     * 1. Detect device memory class in order to determine optimal buffer size
     * 2. Accesses ActivityManager for system capabilities
     * TODO: Future functionalities
     * 3. Detecting CPU cores for parallel processing
     * 4. Accessing Android KeyStore for secure key storage
     */
    private void setupEncryptionManager() {
        encryptionManager = new EncryptionManager(this);
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

        // Progress Components
        progressBar = findViewById(R.id.progressBar);
        progressCard = findViewById(R.id.progressCard);
        resultsCard = findViewById(R.id.resultsCard);
        progressTitle = findViewById(R.id.progressTitle);
        progressPercentage = findViewById(R.id.progressPercentage);
        speedMetric = findViewById(R.id.speedMetric);
        timeRemaining = findViewById(R.id.speedMetric);
        processStatus = findViewById(R.id.processStatus);

        // File Size Components
        fileSizeSlider = findViewById(R.id.fileSizeSlider);
        fileSizeText = findViewById(R.id.fileSizeText);

        // Device Info chips
        chipDevice = findViewById(R.id.chipDevice);
        chipMemory = findViewById(R.id.chipMemory);
        chipCores = findViewById(R.id.chipCores);
        chipHardwareStatus = findViewById(R.id.chipHardwareStatus);

        // Setup algorithm dropdown
        List<String> algorithms = new ArrayList<>();
        for (InterfaceEncryptionAlgorithm algorithm : encryptionManager.getAvailableAlgorithms()) {
            algorithms.add(algorithm.getAlgorithmName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, algorithms);
        algorithmDropdown.setAdapter(adapter);
        algorithmDropdown.setText(algorithms.get(0), false);

        // Button listeners
        btnEncrypt.setOnClickListener(v -> performEncryption());
        btnDecrypt.setOnClickListener(v -> performDecryption());
        btnBenchmark.setOnClickListener(v -> runFullBenchmark());
    }

    /**
     * Check for hardware AES support
     */
    private boolean checkHardwareAESSupport() {
        // On ARM devices check for AES instructions
        //TODO: Have a better check for later project stages
        String cpuFeatures = System.getProperty("os.arch");
        if (cpuFeatures != null && (cpuFeatures.contains("aarch64") || cpuFeatures.contains("arm64"))) {

            return true;
        } else {
            return false;
        }
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
    private void stopProgressionMonitoring() {
        progressHandler.removeCallbacksAndMessages(null);
    }


    /**
     * Update progress UI
     */
    private void updateProgressUI(int progress, double speedMBps, double remainingSec) {
        runOnUiThread(() ->{
            progressBar.setProgress(progress);
            progressPercentage.setText(String.format("%d%%", progress));
            speedMetric.setText(String.format("%.1f MB/s", speedMBps));

            if (remainingSec < 60) {
                timeRemaining.setText(String.format("~%.0fs remaining", remainingSec));
            } else {
                timeRemaining.setText(String.format("~%.1fm remaining", remainingSec / 60));
            }
        });
    }


    /**
     * Upgrade progress message
     */
    private void updateProgress(String message, int progress) {
        runOnUiThread(() -> {
            progressTitle.setText(message);
            if (progress >= 0) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progress);
                progressPercentage.setText(progress + "%");
            } else {
                progressBar.setIndeterminate(true);
                progressPercentage.setText("");
            }
        });
    }


    /**
     * Start progress monitoring with real-time updates
     */
    private void startProgressMonitoring(long totalBytes) {
        Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                long elapsedMs = SystemClock.elapsedRealtime() - startTime;
                double elapsedSec = elapsedMs / 1000.0;

                // Calculate progress
                int progress = (int) ((bytesProcessed * 100) / totalBytes);

                // Calculate speed
                double speedMBps = 0;
                if (elapsedSec > 0) {
                    speedMBps = (bytesProcessed / (1024.0 * 1024.0)) / elapsedSec;
                }

                // Estimate time remaining
                double remainingSec = 0;
                if (speedMBps > 0) {
                    double remainingMB = (totalBytes - bytesProcessed) / (1024.0 * 1024.0);
                    remainingSec = remainingMB / speedMBps;
                }

                // Update UI
                updateProgressUI(progress, speedMBps, remainingSec);

                // Continue updating if not complete
                if (bytesProcessed < totalBytes) {
                    progressHandler.postDelayed(this, 100); // update every 100ms
                }
            }
        };

        progressHandler.post(progressUpdater);
    }


    /**
     * Detect and display device capabilities
     */
    private void detectDeviceCapabilities() {
        // Device model
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        chipDevice.setText(deviceModel);

        // Memory info
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memoryInfo);
            long totalMem = memoryInfo.totalMem / (1024 * 1024 * 1024); // Convert to GB
            chipMemory.setText(String.format("%d GB RAM", totalMem));
        }

        // CPU cores
        int cores = Runtime.getRuntime().availableProcessors();
        chipCores.setText(String.format("%d Cores", cores));

        // Hardware acceleration (check for AES support)
        boolean hasHardwareAES = checkHardwareAESSupport();
        chipHardwareStatus.setText(hasHardwareAES ? "AES-NI" : "Software");
        chipHardwareStatus.setChipBackgroundColorResource(hasHardwareAES ? android.R.color.holo_red_dark : android.R.color.holo_blue_bright);
    }


    /**
     * Get algorithm names for spinner display
     *
     * @return Get algorithm names
     */
    private String[] getAlgorithmNames() {
        return encryptionManager.getAvailableAlgorithms().stream().map(InterfaceEncryptionAlgorithm::getAlgorithmName).toArray(String[]::new);
    }


    /**
     * Update status text on UI thread
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> processStatus.setText(message));
    }


    /**
     * Show results
     */
    private void showResults(String message) {
        runOnUiThread(() -> {
            processStatus.setText(message);
            resultsCard.setVisibility(View.VISIBLE);
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
                        "Algorithm: %s\n" +
                        "File Size: %d MB\n" +
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
     * Show/Hide Progress
     */
    private void showProgress(boolean show) {
        runOnUiThread(() -> {
            progressCard.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) {
                progressBar.setProgress(0);
                progressPercentage.setText("0%");
                speedMetric.setText("0 MB/s");
                timeRemaining.setText("");
            }
        });
    }


    /**
     * Enable/disable UI controls during operations
     */
    private void setUiEnabled(boolean enabled) {
        runOnUiThread(() -> {
            btnEncrypt.setEnabled(enabled);
            btnDecrypt.setEnabled(enabled);
            btnBenchmark.setEnabled(enabled);
            algorithmDropdown.setEnabled(enabled);
            fileSizeSlider.setEnabled(enabled);
        });
    }


    /**
     * Run full benchmark
     */
    private void runFullBenchmark() {
        showResults("Starting comprehensive benchmark...");
        //TODO: Implement benchmark class
    }


    /**
     * Clean up resources when activity is destroyed
     *
     * Ensures proper clean up of:
     * ThreadLocal cipher instances
     * Cached cryptographic material
     * Native resources
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(executorService != null) {
            executorService.shutdown();
        }

        if (encryptionManager != null) {
            encryptionManager.cleanup();
        }
        stopProgressionMonitoring();
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
     * Analysing performance metrics and providing feedback
     *
     * @param throughputMBs Input throughput
     * @param fileSizeMB future measure
     * @return Feedback based on performance obtained
     */
    private String analysePerformance(double throughputMBs, long fileSizeMB) {
        if (throughputMBs > 200) {
            return "Excellent! Hardware acceleration or optimal buffering detected.";
        } else if(throughputMBs > 100) {
            return "Good performance. Software optimisation working effectively.";
        } else if(throughputMBs > 50) {
            return "Fair performance. Consider enabling additional optimisations.";
        } else {
            return "Below expected performance. Check device capabilities and buffer sizing.";
        }
    }


    /**
     * Standard encryption for smaller files
     */
    private void performStandardEncryption(File inputFile, InterfaceEncryptionAlgorithm algorithm) {
        startTime = SystemClock.elapsedRealtime();
        bytesProcessed = 0;

        // Start progress monitoring
        startProgressMonitoring(inputFile.length());

        EncryptionResult result = encryptionManager.encryptFile(inputFile, algorithm, null);

        stopProgressionMonitoring();

        if (result.isSuccess()) {
            lastEncryptedFile = result.getOutputFile();
            showDetailedResults(result);
        } else {
            showResults("Encryption Failed: " + result.getErrorMessage());
        }

        runOnUiThread(() -> {
            setUiEnabled(true);
            showProgress(false);
        });
    }


    /**
     * Chunk processing result
     */
    private static class ChunkResult {
        final boolean success;
        final long bytesProcessed;

        ChunkResult(boolean success, long bytesProcessed) {
            this.success = success;
            this.bytesProcessed = bytesProcessed;
        }
    }


    /**
     * Process individual chunk
     */
    private ChunkResult processChunk(File inputFile, File outputFile, InterfaceEncryptionAlgorithm algorithm,
                                     byte[] key, int chunkIndex, long chunkStart, int chunkSize) {
        try {
            // Read chunk from input file
            byte[] chunkData = new byte[chunkSize];
            try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
                raf.seek(chunkStart);
                raf.readFully(chunkData);
            }

            return new ChunkResult(true, chunkSize);

        } catch (Exception e) {
            return new ChunkResult(false, 0);
        }
    }


    /**
     * Chunked encryption for large files (>= 100MB)
     * Uses parallel processing for better performance
     */
    private void performChunkedEncryption(File inputFile, InterfaceEncryptionAlgorithm algorithm) {
        long fileSize = inputFile.length();
        int chunkSize = CHUNK_SIZE_MB * 1024 * 1024; // conver to bytes
        int numChunks = (int) Math.ceil((double) fileSize / chunkSize);

        startTime = SystemClock.elapsedRealtime();
        bytesProcessed = 0;

        updateProgress("Encryption with " + numChunks + " chunks...", 0);
        startProgressMonitoring(fileSize);

        try {
            // Generate encryption key once for all chunks
            byte[] key = algorithm.generateKey();

            // Output file for encrypted data
            File outputFile = new File(inputFile.getParent(), inputFile.getName() + ".encrypted");

            // Process chunks in parallel
            List<Future<ChunkResult>> futures = new ArrayList<>();

            for (int i = 0; i < numChunks; i++) {
                final int chunkIndex = i;
                final long chunkStart = (long) i * chunkSize;
                final int currentChunkSize = (int) Math.min(chunkSize, fileSize - chunkStart);

                Future<ChunkResult> future = executorService.submit(() ->
                        processChunk(inputFile, outputFile, algorithm, key, chunkIndex, chunkStart, currentChunkSize));
                futures.add(future);
            }

            // Wait for all chunks to complete
            boolean success = true;
            for (Future<ChunkResult> future : futures) {
                ChunkResult result = future.get();
                if (!result.success) {
                    success = false;
                    break;
                }
                bytesProcessed += result.bytesProcessed;
            }

            stopProgressionMonitoring();

            if (success) {
                lastEncryptedFile = outputFile;
                long elapsedMs = SystemClock.elapsedRealtime() - startTime;
                showChunkedResults(fileSize, elapsedMs, numChunks);
            } else {
                showResults("Chunked encryption failed");
            }
        } catch (Exception e) {
            showResults("Chunked encryption error: " + e.getMessage());
        }

        runOnUiThread(() -> {
            setUiEnabled(true);
            showProgress(false);
        });
    }


    /**
     * Show chunked encryption results
     */
    private void showChunkedResults(long fileSize, long elapsedMs, int chunks) {
        long fileSizeMB = fileSize / (1024 * 1024);
        double timeSec = elapsedMs / 1000.0;
        double throughputMBps = fileSizeMB / timeSec;

        @SuppressLint("DefaultLocale") String message = String.format(
                "CHUNKED ENCRYPTION SUCCESSFUL\n\n" +
                "File Size: %d MB\n" +
                "Chunks: %d x %d MB\n" +
                "Time: %.2f MB/s\n" +
                "Throughput: %.2f MB/s\n" +
                "Parallel Threads: %d\n" +
                "Optimisation: %.1fx speedup",
                fileSize, chunks, CHUNK_SIZE_MB,
                timeSec, throughputMBps, MAX_THREADS,
                Math.min(MAX_THREADS, chunks) * 0.8 // estimated speedup

        );

        showResults(message);
    }


    /**
     * Perform Encryption
     *
     * Threading Architecture:
     * UI Thread = User interaction | status updates | progress display
     * Background Thread = Encryption work (CPU | IO operations)
     * Handler = Thread-safe communication back to UI thread
     *
     * Background thread is critical:
     * For Android's main thread must respond within 5 seconds or the system displays
     * "Application Not Responding" dialog. Encryption of 10MB file should take 50-200ms, but
     * is still done in the background for architectural purposes
     */
    private void performEncryption() {
        setUiEnabled(false);
        showProgress(true);

        new Thread(() -> {
            try {
                // Get file size from slider
                int sizeMB = (int) fileSizeSlider.getValue();

                // Create test file
                createTestFile(sizeMB);

                // Get selected algorithm
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);

                // Check if we should use chunking for large files
                if (sizeMB >= 100) {
                    performChunkedEncryption(testFile, algorithm);
                } else {
                    performStandardEncryption(testFile, algorithm);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showResults("Encryption failed: " + e.getMessage());
                    setUiEnabled(true);
                    showProgress(false);
                });
            }
        }).start();
    }


    /**
     * Perform decryption on background thread
     *
     * Note: Currently the encryption requires to be run first to have the file decrypted
     * TODO: Add file selector to select any file to encrypt
     */
    private void performDecryption() {
        if (lastEncryptedFile == null || !lastEncryptedFile.exists()) {
            // First must be encrypted
            //TODO: At the moment just testing file encrypt/decrypt, add file selection to decrypt whenever
            //TODO: not only after an encryption
            updateStatus("Use Encrypt first, proper file selection will be added in the future");
            return;
        }

        setUiEnabled(false);
        showProgress(true);

        new Thread(() -> {
            String algorithmName = algorithmDropdown.getText().toString();
            InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);

            EncryptionResult result = encryptionManager.decryptFile(lastEncryptedFile, algorithm, null);

            if (result.isSuccess()) {
                showDetailedResults(result);
            } else {
                showResults("Decryption Failed: " + result.getErrorMessage());
            }

            runOnUiThread(() -> {
                setUiEnabled(true);
                showProgress(false);
            });
        }).start();
    }




    /**
     * Handling encryption/decryption result and displaying detailed metrics
     *
     * These help to validate that optimisations are working by:
     * Showing processing time
     * Showing throughput
     * Showing success or not
     *
     * @param result Passing the result of the encryption to be processed for display
     */
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
//                            "Algorithm: %s\n" +
//                            "File Size: %d MB\n" +
//                            "Processing Time: %d ms\n" +
//                            "Throughput: %.2f MB/s\n\n" +
//                            "Input: %s\n" +
//                            "Output: %s\n\n" +
//                            "Performance Analysis: \n%s",
//                    result.getOperation(),
//                    result.getAlgorithmName(),
//                    fileSizeMB,
//                    result.getProcessingTimeMs(),
//                    throughputMBs,
//                    result.getInputFile().getName(),
//                    result.getOutputFile().getName(),
//                    analysePerfomance(throughputMBs, fileSizeMB));
//
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

