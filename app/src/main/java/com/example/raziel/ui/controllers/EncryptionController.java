package com.example.raziel.ui.controllers;

import android.app.AlertDialog;
import android.os.Environment;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.example.raziel.core.caching.CacheManager;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.managers.file.FileSelectionManager;
import com.example.raziel.ui.activities.MainActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EncryptionController {
    private static final String TAG = "EncryptionController";
    private final MainActivity activity;
    private final EncryptionManager encryptionManager;

    public EncryptionController(MainActivity activity, EncryptionManager encryptionManager) {
        this.activity = activity;
        this.encryptionManager = encryptionManager;
    }

    public void setupAlgorithmDropdown() {
        List<String> algorithms = new ArrayList<>();

        for (InterfaceEncryptionAlgorithm algorithm : encryptionManager.getAvailableAlgorithms()) {
            algorithms.add(algorithm.getAlgorithmName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, algorithms);

        activity.algorithmDropdown.setAdapter(adapter);

        if (!algorithms.isEmpty()) {
            // Set to recommended algorithm
            InterfaceEncryptionAlgorithm recommended = encryptionManager.getRecommendedAlgorithm();
            activity.algorithmDropdown.setText(recommended.getAlgorithmName(), false);
        }
    }

    public void encryptSelectedFile() {
        performEncryption();
    }

    public void decryptSelectedFile() {
        performDecryption();
    }

    private void performEncryption() {
        FileSelectionManager.FileInfo selectedFile = activity.selectedFile;

        if (selectedFile == null || selectedFile.tempFile == null || !selectedFile.tempFile.exists()) {
            activity.showError("No File Selected", "Please select a file first");
            return;
        }

        File inputFile = selectedFile.tempFile;

        // Prevent encrypting already-encrypted files
        String nameLower = selectedFile.name != null ? selectedFile.name.toLowerCase(Locale.US) : inputFile.getName().toLowerCase(Locale.US);

        if (nameLower.endsWith(".raziel")) {
            activity.showError("Cannot Encrypt", "The selected file is already encrypted (.raziel).");
            return;
        }

        long fileSize = inputFile.length();

        String selectedAlgorithm = activity.algorithmDropdown.getText() != null ? activity.algorithmDropdown.getText().toString() : "";

        long maxXChaChaBytes = 50L * 1024L * 1024L; // 50MB

        if ("XChaCha20-Poly1305".equals(selectedAlgorithm) && fileSize > maxXChaChaBytes) {

            activity.showError("File Too Large", "XChaCha20-Poly1305 can only encrypt up to 50MB.\n" +
                    "Use AES-256-GCM for larger files.");
            return;
        }

        // Storage check before operation starts
        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        long free = outputDir.getFreeSpace();
        long required = inputFile.length() * 2;  // 2× input size overhead safety

        if (free < required) {
            activity.showError("Insufficient Storage", "Not enough free space to complete this operation.");
            activity.setUiEnabled(true);
            activity.showProgress(false, "", false);
            return;
        }

        // Lock UI & prepare progress
        activity.setUiEnabled(false);
        if (activity.progressController != null) {
            activity.progressController.showProgress("Encrypting...");
        }

        // Encryption ran in background
        activity.executorService.execute(() -> {
            try {
                // Get selected algorithm
                InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(selectedAlgorithm);

                if (algorithm == null) {
                    // Reset UI before returning to not gray out buttons
                    activity.mainHandler.post(() -> {
                        activity.showError("Invalid Algorithm", "Algorithm not found: " + selectedAlgorithm);
                        activity.setUiEnabled(true);
                        activity.showProgress(false, "", false);
                    });
                    return;
                }

                algorithm.setProgressCallback((processedBytes, totalBytes) -> {
                    if (activity.progressController != null) {
                        activity.progressController.update(processedBytes, totalBytes);
                    }});

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File outputFile = new File(outputDir, "encrypted_" + timestamp + ".raziel");

                EncryptionResult result = encryptionManager.encryptFile(inputFile, algorithm, outputFile);

                if (result.isSuccess() && selectedFile.tempFile != null && selectedFile.tempFile.exists()) {
                    boolean deleted = selectedFile.tempFile.delete();
                    Log.d(TAG, "Deleted temp input file: " + deleted + " - " + selectedFile.tempFile.getAbsolutePath());
                }

                algorithm.setProgressCallback(null);

                // UI Thread Updates
                activity.mainHandler.post(() -> {
                    if (result.isSuccess()) {
                        activity.lastEncryptedFile = result.getOutputFile();

                        showDetailedResultsWithCache(result);

                        Log.d("UI_Debug", "About to call showDetailedResults");
                        //showDetailedResults(result);
                        Log.d("UI_Debug", "After showDetailedResults");
                        //updateStatus("Encryption completed successfully");

                        // Log file locations for debugging
                        Log.d("FileLocations", "Original temp file deleted: " + selectedFile.tempFile.getAbsolutePath());
                        Log.d("FileLocations", "Encrypted: " + activity.lastEncryptedFile.getAbsolutePath());

                        // Reset selection UI
                        clearSelectedFileUI();
                        activity.selectedFile = null;

                    } else {
                        activity.showError("Encryption Failed", result.getErrorMessage());
                    }
                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", false);
                });

            } catch (Exception e) {
                activity.mainHandler.post(() -> {
                    activity.showResults("Encryption failed: " + e.getMessage());
                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", false);
                });
            }
        });
    }


    private void performDecryption() {
        FileSelectionManager.FileInfo selectedFile = activity.selectedFile;

        if (selectedFile == null || selectedFile.tempFile == null || !selectedFile.tempFile.exists()) {
            activity.showError("No Encrypted File", "Please select a .raziel encrypted file first.");
            return;
        }

        File encryptedFile = selectedFile.tempFile;
        String realName = selectedFile.name != null ? selectedFile.name.toLowerCase(Locale.US) : encryptedFile.getName().toLowerCase(Locale.US);

        if (realName.endsWith(".dec")) {
            activity.showError("Already Decrypted", "This file is already decrypted (.dec).");
            return;
        }

        if (!realName.endsWith(".raziel")) {
            activity.showError("Invalid File", "The selected file is not an encrypted (.raziel) file.");
            return;
        }

        // Read metadata: which algorithm was actually used
        String actualAlgorithm = encryptionManager.getAlgorithmUsedForFile(encryptedFile);
        String userSelected = activity.algorithmDropdown.getText().toString();

        if (actualAlgorithm != null && !actualAlgorithm.equals(userSelected)) {
            // Auto-correct dropdown
            activity.algorithmDropdown.setText(actualAlgorithm, false);

            // Dialog to tell the user we corrected it
            new AlertDialog.Builder(activity)
                    .setTitle("Algorithm Corrected")
                    .setMessage("The correct algorithm for this file is:\n\n" + actualAlgorithm + "\n\nYour selection has been updated.")
                    .setPositiveButton("OK", null)
                    .show();

            activity.updateStatus("Correct algorithm applied: " + actualAlgorithm);
        }

        // If metadata missing, fall back to whatever is in dropdown
        String algoNameToUse = (actualAlgorithm != null) ? actualAlgorithm : userSelected;

        InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmName(algoNameToUse);
        if (algorithm == null) {
            activity.showError("Algorithm Error", "Algorithm not found: " + algoNameToUse);
            return;
        }

        // Storage check (Downloads dir)
        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        long free = outputDir.getFreeSpace();
        long required = encryptedFile.length() * 2; // again ~2x safety

        if (free < required) {
            activity.showError("Insufficient Storage", "Not enough free space to decrypt this file.");
            return;
        }

        activity.setUiEnabled(false);
        if (activity.progressController != null) {
            activity.progressController.showProgress("Decrypting...");
        }

        activity.progressBar.setIndeterminate(false);
        activity.progressBar.setProgress(0);
        activity.progressPercentage.setText("0%");
        activity.resetProgressTracking();

        // Background decryption
        activity.executorService.execute(() -> {
            try {

                algorithm.setProgressCallback((processedBytes, totalBytes) -> {
                    if (activity.progressController != null) {
                        activity.progressController.update(processedBytes, totalBytes);
                    }
                });

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File outputFile = new File(outputDir, "decrypted_" + timestamp + ".dec");

                EncryptionResult result = encryptionManager.decryptFile(encryptedFile, algorithm, outputFile);

                algorithm.setProgressCallback(null);

                activity.mainHandler.post(() -> {
                    if (result.isSuccess()) {
                        activity.showDetailedResults(result);
                    } else {
                        activity.showError("Decryption Failed", result.getErrorMessage());
                    }

                    clearSelectedFileUI();
                    activity.selectedFile = null;

                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", false);
                });

            } catch (Exception e) {
                activity.mainHandler.post(() -> {
                    activity.showResults("Decryption error: " + e.getMessage());
                    activity.setUiEnabled(true);
                    activity.showProgress(false, "", false);
                    clearSelectedFileUI();
                    activity.selectedFile = null;
                });
            }
        });

    }

    private void clearSelectedFileUI() {
        activity.selectedFile = null;

        if (activity.fileInfoText != null) {
            activity.fileInfoText.setText("No file selected");
        }
        if (activity.fileSelectionCard != null) {
            activity.fileSelectionCard.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * Show encryption result + cache stats as a combined message.
     * Uses CacheManager.CacheStats from EncryptionManager.getCacheStats().
     */
    private void showDetailedResultsWithCache(EncryptionResult result) {
        double fileSizeMB = result.getFileSizeBytes() / (1024.0 * 1024.0);
        double timeSec = result.getProcessingTimeMs() / 1000.0;
        double throughputMBps = fileSizeMB / timeSec;

        CacheManager.CacheStats cacheStats = encryptionManager.getCacheStats();

        String message = String.format(
                Locale.US,
                "ENCRYPTION SUCCESSFUL\n\n" +
                        "Performance Metrics:\n" +
                        "• Operation: %s\n" +
                        "• Algorithm: %s\n" +
                        "• File Size: %.2f MB\n" +
                        "• Time: %.2f seconds\n" +
                        "• Throughput: %.2f MB/s\n\n" +
                        "Cache Performance:\n" +
                        "• Keyset: %d hits, %d misses (%.1f%% hit rate)\n\n" +
                        "Device: %s (%d cores)",
                result.getOperation(),
                result.getAlgorithmName(),
                fileSizeMB,
                timeSec,
                throughputMBps,
                cacheStats.keysetHits,
                cacheStats.keysetMisses,
                cacheStats.keysetHitRate,
                activity.chipDevice.getText(),
                Runtime.getRuntime().availableProcessors()
        );

        activity.showResults(message);
    }
}
