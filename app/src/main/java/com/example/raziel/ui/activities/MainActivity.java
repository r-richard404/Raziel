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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private AutoCompleteTextView algorithmDropdown, fileTypeDropdown;
    private MaterialButton btnEncrypt, btnDecrypt, btnBenchmark;
    private LinearProgressIndicator progressBar;
    private TextView processStatus, progressTitle, progressPercentage, fileTypeDescription;
    private TextView speedMetric, timeRemaining, fileSizeText;
    private MaterialCardView progressCard, resultsCard;
    private Slider fileSizeSlider;
    private Chip chipDevice, chipMemory, chipCores, chipHardwareStatus;

    // Core components
    private EncryptionManager encryptionManager;
    private File testFile;
    // Track last encryption output to test decryption function
    private File lastEncryptedFile;

    private Handler progressHandler = new Handler(Looper.getMainLooper());



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

        // File Type Dropdown
        fileTypeDropdown = findViewById(R.id.fileTypeDropdown);
        fileTypeDescription = findViewById(R.id.fileTypeDescription);

        // Setup File Type Dropdown
        setupFileTypeDropdown();

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
     * Setup file type dropdown with supported file types and descriptions
     */
    private void setupFileTypeDropdown() {
        // Define supported file types with descriptions
        Map<String, String> fileTypes = new LinkedHashMap<String, String>() {{
            put("TXT", "Text file with readable content");
            put("PDF", "PDF document with structured content");
            put("JPG", "JPEG image file (simulated)");
            put("PNG", "PNG image file (simulated)");
            put("MP3", "Audio file (simulated)");
            put("MP4", "Video file (simulated)");
            put("DOCX", "Word document (simulated)");
            put("ZIP", "Compressed archive (simulated)");
        }};

        List<String> fileTypeList = new ArrayList<>(fileTypes.keySet());

        ArrayAdapter<String> fileTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, fileTypeList);
        fileTypeDropdown.setAdapter(fileTypeAdapter);

        // Update description when selection changes
        fileTypeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = fileTypeList.get(position);
            String description = fileTypes.get(selectedType);
            fileTypeDescription.setText(description);
        });

        // Set initial description
        fileTypeDescription.setText(fileTypes.get("TXT"));
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

    // Get easier access on real hardware device testing
    private File getOutputFileInDownloads(String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        return new File(downloadsDir, fileName);
    }

    // Initialise different file types for testing based on patterns
    private String getTestPatternForFileType(String fileExtension) {
        switch (fileExtension.toLowerCase()) {
            case "txt":
                return "This is a test text file for Raziel encryption testing. " +
                        "The quick brown fox jumps over the lazy dog. " +
                        "Encryption test pattern - File created at: " + System.currentTimeMillis() + " ";
            case "jpg":
            case "jpeg":
                // Simple JPEG header pattern, not real but recognised by extension and data
                return "JPEG_TEST_DATA_" + System.currentTimeMillis() + "_RAZIEL_ENCRYPTION_TEST_PATTERN_";
            case "png":
                return "PNG_TEST_DATA_" + System.currentTimeMillis() + "_RAZIEL_ENCRYPTION_TEST_PATTERN_";
            case "pdf":
                return "%PDF_TEST_VERISON_RAZIEL_ENCRYPTION_TEST_" + System.currentTimeMillis() + " ";
            case "mp4":
                return "MP4_TEST_DATA_" + System.currentTimeMillis() + "_RAZIEL_ENCRYPTION_TEST_PATTERN_";
            default:
                return "RAZIEL_ENCRYPTION_TEST_FILE_" + System.currentTimeMillis() + "_TEST_DATA_PATTERN_";
        }
    }

    /**
     * Create test file with specified size
     */
    private File createTestFile(int sizeMB, String fileExtension, String algorithmName) throws IOException {
        String timeStamp = String.valueOf(System.currentTimeMillis());
        String fileName = "test_file_" + sizeMB + "mb_" + timeStamp + "_" + algorithmName + "." + fileExtension;
        //testFile = new File(getFilesDir(), "test_file_" + sizeMB + "mb.dat");

        // Save to Downloads directory for easy access on hardware device testing
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File testFile = new File(downloadsDir, fileName);

        updateProgress("Creating " + sizeMB + "MB test file...", -1);

        // Use larger buffer for file creation
        final int BUFFER_SIZE = 1024 * 1024; //1MB buffer
        byte[] buffer = new byte[BUFFER_SIZE];

        // Fill with different patterns based on file type
        String pattern = getTestPatternForFileType(fileExtension);
        byte[] patternBytes = pattern.getBytes();

        long targetBytes = (long) sizeMB * 1024 * 1024;
        long written = 0;

        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(testFile), BUFFER_SIZE)) {

            // Get appropriate content pattern for file type
            String header = generateFileHeader(fileExtension, sizeMB);
            byte[] headerBytes = header.getBytes();

            bos.write(headerBytes);
            written += headerBytes.length;

            // Fill the rest of the file with pattern
            while (written < targetBytes) {
                int toWrite = (int) Math.min(BUFFER_SIZE, targetBytes - written);
                bos.write(buffer, 0, toWrite);
                written += toWrite;

                // Update progress occasionally
                if (written % (5 * 1024 * 1024) == 0) {  // Every 5MB
                    int progress = (int) ((written * 100) / targetBytes);
                    updateProgress("Creating test file...", progress);
                }

            }
        }
        Log.d("FileDebug", "Test file created: " + testFile.getAbsolutePath() + ", Size: " + testFile.length() + " bytes, Type: " + fileExtension);
        return testFile;
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
                // Get selected algorithm
                String algorithmName = algorithmDropdown.getText().toString();
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(algorithmName);


                if (algorithm == null) {
                    runOnUiThread(() -> {
                        updateStatus("Algorithm not found: " + algorithmName);
                        setUiEnabled(true);
                        showProgress(false);
                    });
                    return;
                }

                // Get file size from slider
                int sizeMB = (int) fileSizeSlider.getValue();

                // Get file type from Dropdown
                String fileType = fileTypeDropdown.getText().toString();
                if (fileType == null || fileType.isEmpty()) {
                    fileType = "TXT"; // Default
                }

                // Create test file
                //createTestFile(sizeMB);
                File testFile = createTestFile(sizeMB, fileType, algorithmName);

                // Set up progress callback
                algorithm.setProgressCallback((bytesProcessed, totalBytes) ->
                        runOnUiThread(() -> updateProgressBar(bytesProcessed, totalBytes)));

                // Create encrypted file with appropriate extension
                String encryptedFileName = "encrypted_" + testFile.getName() + ".raziel";

                //long startTime = System.currentTimeMillis();
                EncryptionResult result = encryptionManager.encryptFile(testFile, algorithm, "encrypted_file.enc");
                //long endTime = System.currentTimeMillis();

                // Clear the callback
                algorithm.setProgressCallback(null);

                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        lastEncryptedFile = result.getOutputFile();

                        //testDecryptionVerification(testFile, lastEncryptedFile);
                        //handleEncryptionResult(result);
                        showDetailedResults(result);

                        // Log file locations for debugging
                        Log.d("FileLocations", "Original: " + testFile.getAbsolutePath());
                        Log.d("FileLocations", "Encrypted: " + lastEncryptedFile.getAbsolutePath());

                    } else {
                        //updateStatus("Encryption Failed: " + result.getErrorMessage());\
                        showResults("Decryption Failed: " + result.getErrorMessage());
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
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(algorithmName);

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
                    "Processing Time: " + timeSec + " seconds\n" +
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
     * Generate appropriate file header based on file type
     */
    private String generateFileHeader(String fileExtension, int sizeMB) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        switch (fileExtension.toUpperCase()) {
            case "TXT":
                return "RAZIEL ENCRYPTION TEST - TEXT FILE\n" +
                        "Created: " + timestamp + "\n" +
                        "Size: " + sizeMB + "MB\n" +
                        "Purpose: Encryption performance testing\n" +
                        "Content: The quick brown fox jumps over the lazy dog.\n" +
                        "Repeat pattern below:\n" +
                        "=" .repeat(50) + "\n";

            case "PDF":
                return "%PDF-1.4\n" +
                        "% Raziel Test PDF Document\n" +
                        "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                        "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                        "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
                        "4 0 obj\n<< /Length 100 >>\nstream\n" +
                        "BT /F1 12 Tf 72 720 Td (RAZIEL ENCRYPTION TEST PDF) Tj ET\n" +
                        "endstream\nendobj\n" +
                        "xref\n0 5\n" +
                        "0000000000 65535 f \n" +
                        "0000000009 00000 n \n" +
                        "0000000058 00000 n \n" +
                        "0000000115 00000 n \n" +
                        "0000000234 00000 n \n" +
                        "trailer\n<< /Size 5 /Root 1 0 R >>\n" +
                        "startxref\n300\n%%EOF\n";

            case "JPG":
                return "\u00FF\u00D8\u00FF\u00E0" + // JPEG Start
                        "RAZIEL TEST JPEG IMAGE - " + timestamp +
                        " - Size: " + sizeMB + "MB - This is simulated JPEG data for encryption testing.";

            case "PNG":
                return "\u0089PNG\r\n\u001a\n" + // PNG Start
                        "RAZIEL TEST PNG IMAGE - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated PNG data for encryption testing.";

            case "MP3":
                return "ID3" + // MP3 Start
                        "RAZIEL TEST MP3 AUDIO - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated audio data for encryption testing.";

            case "MP4":
                return "ftypmp42" + // MP4 Start
                        "RAZIEL TEST MP4 VIDEO - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated video data for encryption testing.";

            case "DOCX":
                return "PK\u0003\u0004" + // ZIP header (DOCX is a zip)
                        "[Content_Types].xml" +
                        "RAZIEL TEST DOCX DOCUMENT - " + timestamp;

            case "ZIP":
                return "PK\u0003\u0004" + // ZIP header
                        "RAZIEL TEST ZIP ARCHIVE - " + timestamp +
                        " - Contains simulated compressed data for encryption testing.";

            default:
                return "RAZIEL ENCRYPTION TEST FILE\nType: " + fileExtension +
                        "\nCreated: " + timestamp + "\nSize: " + sizeMB + "MB\n";
        }
    }

    /**
     * Generate content pattern based on file type
     */
    private String generateContentPattern(String fileExtension) {
        switch (fileExtension.toUpperCase()) {
            case "TXT":
                return "Encryption test pattern line - ABCDEFGHIJKLMNOPQRSTUVWXYZ - 0123456789 - " +
                        "The quick brown fox jumps over the lazy dog. ";

            case "PDF":
                return "stream\nBT /F1 10 Tf 50 700 Td (Encryption test content line: PDF document simulation) Tj ET\nendstream\n";

            case "JPG":
            case "PNG":
                return "IMAGE_DATA_BLOCK[" + System.currentTimeMillis() + "]_RAZIEL_ENCRYPTION_TEST_PATTERN_";

            case "MP3":
            case "MP4":
                return "MEDIA_DATA_FRAME[" + System.currentTimeMillis() + "]_AUDIO_VIDEO_TEST_PATTERN_";

            case "DOCX":
                return "<w:p><w:r><w:t>Encryption test paragraph for DOCX document simulation.</w:t></w:r></w:p>";

            case "ZIP":
                return "COMPRESSED_DATA_BLOCK[" + System.currentTimeMillis() + "]_ZIP_ARCHIVE_TEST_CONTENT_";

            default:
                return "TEST_DATA_PATTERN[" + System.currentTimeMillis() + "]_FILE_TYPE_" + fileExtension + "_";
        }
    }
}

