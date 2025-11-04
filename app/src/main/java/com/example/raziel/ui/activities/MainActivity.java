package com.example.raziel.ui.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.raziel.R;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Main Activity for testing encryption performance optimisations
 *
 * Key Architecture Decisions:
 * Background thread execution that prevents Application Not Responding
 * Handled-based UI updates for thread-safe communication
 */

public class MainActivity extends AppCompatActivity {
    private Spinner algorithmSpinner;
    private Button btnEncrypt, btnDecrypt;
    private ProgressBar progressBar;
    private TextView processStatus;
    private EncryptionManager encryptionManager;
    private File testFile;

    // Track last encryption output to test decryption function
    private File lastEncryptedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupEncryptionManager();
        initialiseViews();
        createTestFile();
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
     * Get algorithm names for spinner display
     *
     * @return Get algorithm names
     */
    private String[] getAlgorithmNames() {
        return encryptionManager.getAvailableAlgorithms().stream().map(InterfaceEncryptionAlgorithm::getAlgorithmName).toArray(String[]::new);
    }


    /**
     * Initialising UI components and setting up event handlers
     */
    private void initialiseViews() {
        algorithmSpinner = findViewById(R.id.algorithmSpinner);
        btnEncrypt = findViewById(R.id.btnEncrypt);
        btnDecrypt = findViewById(R.id.btnDecrypt);
        progressBar = findViewById(R.id.progressBar);
        processStatus = findViewById(R.id.processStatus);

        // Setup algorithm spinner with available algorithms
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getAlgorithmNames());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        algorithmSpinner.setAdapter(adapter);

        // Set button listeners
        btnEncrypt.setOnClickListener(v -> performEncryption());
        btnDecrypt.setOnClickListener(v -> performDecryption());
    }



    /**
     * Create test file with medium size for better performance testing
     * File sizes used for:
     * Small files (< 1MB)
     * Medium files (1-10MB)
     * Large files (> 10MB)
     *
     * This creates a ~10MB file for performance testing
     */
    private void createTestFile() {
        try {
            testFile = new File(getFilesDir(), "test_file.txt");
            FileOutputStream fos = new FileOutputStream(testFile);

            // Generate ~10MB of test data for performance testing
            // Using real text pattern, not random bytes
            String testData = "This is a test file for Raziel's encryption performance benchmark.";
            testData = testData.repeat(20);

            // Write approximately 10MB
            // Calculation: 8,000 iterations x ~1.4KB = ~10MB
            for (int i = 0; i < 8000; i++) {
                fos.write(testData.getBytes());
            }
            fos.close();

            long fileSizeMB = testFile.length() / 1024 / 1024;
            updateStatus(String.format("Test file created: %s (%d MB)\nReady to test encryption performance",
                    testFile.getName(), fileSizeMB));

        } catch (IOException e) {
            updateStatus("Error creating test file: " + e.getMessage());
        }
    }


    /**
     * Getting the current selected algorithm from the spinner
     *
     * @return Current selected algorithm from spinner
     */
    private InterfaceEncryptionAlgorithm getSelectedAlgorithm() {
        String selectedName = (String) algorithmSpinner.getSelectedItem();
        return encryptionManager.getAlgorithmByName(selectedName);
    }


    /**
     * Analysing performance metrics and providing feedback
     *
     * @param throughputMBs Input throughput
     * @param fileSizeMB future measure
     * @return Feedback based on performance obtained
     */
    private String analysePerfomance(double throughputMBs, long fileSizeMB) {
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
     * Update status text on UI thread
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> processStatus.setText(message));
    }


    /**
     * Enable/disable UI controls during operations
     */
    private void setUiEnabled(boolean enabled) {
        runOnUiThread(() -> {
            btnEncrypt.setEnabled(enabled);
            btnDecrypt.setEnabled(enabled);
            algorithmSpinner.setEnabled(enabled);
            progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        });
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
        if (encryptionManager != null) {
            encryptionManager.cleanup();
        }
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
    private void handleEncryptionResult(EncryptionResult result) {
        if (result.isSuccess()) {
            // Calculate throughput to validate performance
            long fileSizeMB = result.getFileSizeBytes() / 1024 / 1024;
            double timeSec = result.getProcessingTimeMs() / 1000.0;
            double throughputMBs = fileSizeMB / timeSec;

            @SuppressLint("DefaultLocale")
            String message = String.format(
                    "%s SUCCESSFUL!\n\n" +
                            "Algorithm: %s\n" +
                            "File Size: %d MB\n" +
                            "Processing Time: %d ms\n" +
                            "Throughput: %.2f MB/s\n\n" +
                            "Input: %s\n" +
                            "Output: %s\n\n" +
                            "Performance Analysis: \n%s",
                    result.getOperation(),
                    result.getAlgorithmName(),
                    fileSizeMB,
                    result.getProcessingTimeMs(),
                    throughputMBs,
                    result.getInputFile().getName(),
                    result.getOutputFile().getName(),
                    analysePerfomance(throughputMBs, fileSizeMB));

            updateStatus(message);

        } else {
            updateStatus(String.format(
                    "%s FAILED \n\nError: %s\n\n File: %s",
                    result.getOperation(),
                    result.getErrorMessage(),
                    result.getInputFile().getName()
            ));
        }
    }


    /**
     * Perform Encryption on background thread
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
        if (testFile == null || !testFile.exists()) {
            updateStatus("Test file not available");
            return;
        }

        setUiEnabled(false);
        updateStatus("Starting encryption...\nAnalysing device capabilities...");

        // Execute ecryption in the background thread
        new Thread(() -> {
            try {
                // Get Selected algorithm
                InterfaceEncryptionAlgorithm algorithm = getSelectedAlgorithm();

                // Perform encryption
                EncryptionResult result = encryptionManager.encryptFile(testFile, algorithm, null);

                // Store encrypted file to decrypt it
                if (result.isSuccess()) {
                    lastEncryptedFile = result.getOutputFile();
                }

                // Update UI on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    handleEncryptionResult(result);
                    setUiEnabled(true);
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                   updateStatus("Encryption failed with exception: " + e.getMessage());
                   setUiEnabled(true);
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
        updateStatus("Starting decryption...");

        new Thread(() -> {
            try {
                InterfaceEncryptionAlgorithm algorithm = getSelectedAlgorithm();
                EncryptionResult result = encryptionManager.decryptFile(lastEncryptedFile, algorithm, null);

                new Handler(Looper.getMainLooper()).post(() -> {
                    handleEncryptionResult(result);
                    setUiEnabled(true);
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateStatus("Decryption failed with exception " + e.getMessage());
                    setUiEnabled(true);
                });
            }
        }).start();
    }
}

