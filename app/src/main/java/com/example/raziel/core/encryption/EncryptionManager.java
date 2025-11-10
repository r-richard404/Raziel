package com.example.raziel.core.encryption;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.algorithms.AES_256;
import com.example.raziel.core.encryption.algorithms.ChaCha20_Poly1305;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.performance.PerformanceMetrics;
import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/** Providing a simple interface to complex encryption subsystem
* Uses Single Responsibility by managing encryption operations and algorithm selection
* Dependent on InterfaceEncryptionAlgorithm abstraction
*/
public class EncryptionManager {
    // Available encryption algorithms
    // TODO: Left list only with 1 algorithm until the others are implemented

    private static final String TAG = "EncryptionManager";
    private final Context context;
    private final KeyManager keyManager;
    private final DeviceProfiler deviceProfiler;
    private final List<InterfaceEncryptionAlgorithm> availableAlgorithms;
    private final PerformanceMetrics performanceMetrics;

    //TODO: Implement proper storage for key management\

    // Temporary keyset storage for supervisor demo
    private KeysetHandle lastEncryptionKey = null;

    /**
     * Constructor initialises encryption manager with device context
     */
    public EncryptionManager(Context context) {
        this.context = context;
        this.keyManager = new KeyManager(context);
        this.deviceProfiler = new DeviceProfiler(context);
        this.performanceMetrics = new PerformanceMetrics();

        // Initialising available algorithms
        // Context is passed for algorithms to adapt to device capabilities
        this.availableAlgorithms = Arrays.asList(new AES_256(context), new ChaCha20_Poly1305(context));

        Log.d(TAG, "Encryption Manager initialised with " + availableAlgorithms.size() + " algorithms");
    }

    /**
     * Get list of available encryption algorithms for UI display
     */
    public List<InterfaceEncryptionAlgorithm> getAvailableAlgorithms() {
        return availableAlgorithms;
    }

    /**
     * Get current performance metrics
     */
    public PerformanceMetrics.PerformanceSnapshot getPerformanceMetrics() {
        return performanceMetrics.getSnapshot();
    }

    /**
     * Reset performance metrics
     */
    public void resetMetrics() {
        performanceMetrics.reset();
    }

    /**
     * Find algorithm implementation by name
     */
    public InterfaceEncryptionAlgorithm getAlgorithmByName(String name) {
        for (InterfaceEncryptionAlgorithm algorithm : availableAlgorithms) {
            if (algorithm.getAlgorithmName().equals(name)) {
                return algorithm;
            }
        }
        return null;
    }

    /**
     * Clean up encryption resources when shutting down
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up EncryptionManager resources");
        lastEncryptionKey = null;
    }

    // Intelligent algorithm recommendation based on device capabilities
    public InterfaceEncryptionAlgorithm getRecommendedAlgorithm() {
        return deviceProfiler.preferAES() ? getAlgorithmByName("AES-256-GCM") : getAlgorithmByName("ChaCha20-Poly1305");
    }

    // Select appropriate Tink key template based on algorithm and device capablities
    private KeyTemplate selectKeyTemplate(InterfaceEncryptionAlgorithm algorithm) throws GeneralSecurityException {
        int segmentSize = algorithm.getOptimalSegmentSize();

        if (algorithm.getAlgorithmName().contains("AES")) {
            // AES-256-GCm with adaptive segment size
            if (segmentSize >= 1024 * 1024) {
                return KeyTemplates.get("AES256_GCM_HKDF_1MB");
            } else if (segmentSize >= 256 * 1024){
                return KeyTemplates.get("AES256_GCM_HKDF_4KB");
            } else {
                return KeyTemplates.get("AES256_GCM_HKDF_4KB");
            }
        } else {
            // ChaCha20
            if (segmentSize >= 1024 * 1024) {
                return KeyTemplates.get("AES256_GCM_HKDF_1MB"); // Tink doesn't have streaming template for ChaCha
            } else {
                return KeyTemplates.get("AES256_GCM_HKDF_4KB");
            }
        }
    }


    /**
     * Encrypts a file using the specified algorithm
     */
    public EncryptionResult encryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, String outputFileName) {
        long startTime = System.nanoTime();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file does not exist", inputFile);
            }

            // Check if file contains any data
            if (inputFile.length() == 0) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file is empty", inputFile);
            }

            // Generate output file path
            File outputFile = new File(inputFile.getParent(), outputFileName != null ? outputFileName : inputFile.getName() + ".encrypted");

            // Create keyset for encryption
            KeyTemplate keyTemplate = selectKeyTemplate(algorithm);
            KeysetHandle keysetHandle = keyManager.createStreamingKeysetHandle(keyTemplate.toParameters());

            lastEncryptionKey = keysetHandle;

            // Perform encryption
            boolean success = algorithm.encryptFile(inputFile, outputFile, keysetHandle, null);

            long endTime = System.nanoTime();
            long elapsedNS = (endTime - startTime); // Used for performance metric
            long elapsedMS =  elapsedNS / 1_000_000; // Convert nano to milliseconds

            // Record Metrics
            performanceMetrics.recordOperation(
                    algorithm.getAlgorithmName(),
                    inputFile.length(),
                    elapsedNS,
                    success
            );

            if (success) {
                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.ENCRYPT, elapsedMS, inputFile.length());
            } else {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption process failed", inputFile);
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Encryption Failed", e);
            return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption error: " + e.getMessage(), inputFile);
        }
    }


    /**
     * Decrypting file using the specified algorithm
     */
    public EncryptionResult decryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, String outputFileName) {
        long startTime = System.nanoTime();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Input file does not exist", inputFile);
            }

            // For decryption the key needs to be handled properly
            // TODO: retrieve key from secure storage not generated for testing purposes
            // Key is generated instead of retrieved to debug the process properly


            if (lastEncryptionKey == null) {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "No encryption key available. You must encrypt a file" +
                        "first before decrypting it. This is a limitation until keys are retrieved from secure storage.", inputFile);
            }


            // Generate output file path
            String originalName = inputFile.getName().replace(".encrypted", "");
            File outputFile = new File(inputFile.getParent(), outputFileName != null ? outputFileName : "decrypted_" + originalName);

            // Perform decryption
            boolean success = algorithm.decryptFile(inputFile, outputFile, lastEncryptionKey, null);
            long endTime = System.nanoTime();
            long elapsedNS = (endTime - startTime);
            long elapsedMS =  elapsedNS / 1_000_000;

            // Performance Metrics
            performanceMetrics.recordOperation(
                    algorithm.getAlgorithmName(),
                    inputFile.length(),
                    elapsedNS,
                    success
            );

            if (success) {
                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.DECRYPT, elapsedMS, inputFile.length());
            } else {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption process failed - possibly wrong key or corrupted file", inputFile);
            }
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption error: " + e.getMessage(), inputFile);
        }
    }

}
