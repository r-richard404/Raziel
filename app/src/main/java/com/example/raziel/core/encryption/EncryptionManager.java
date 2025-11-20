package com.example.raziel.core.encryption;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.caching.CacheManager;
import com.example.raziel.core.encryption.algorithms.AES_256_GCM;
import com.example.raziel.core.encryption.algorithms.XChaCha20_Poly1305;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.optimisation.AlgorithmSelector;
import com.example.raziel.core.performance.PerformanceMetrics;
import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encryption Manager
 */
public class EncryptionManager {
    private static final String TAG = "EncryptionManager";

    private final Context context;
    private final KeyManager keyManager;
    private final CacheManager cacheManager;
    private final DeviceProfiler deviceProfiler;
    private final AlgorithmSelector algorithmSelector;
    private final PerformanceMetrics performanceMetrics;

    // Track keys per file
    private final Map<String, KeysetHandle> fileKeyMap = new HashMap<>();

    /**
     * Constructor initialises encryption manager with device context
     */
    public EncryptionManager(Context context) {
        this.context = context;
        this.keyManager = new KeyManager(context);
        this.deviceProfiler = new DeviceProfiler(context);
        this.cacheManager = new CacheManager(context);
        this.performanceMetrics = new PerformanceMetrics();

        // Initialising available algorithms
        // Context is passed for algorithms to adapt to device capabilities
        List<InterfaceEncryptionAlgorithm> algorithms = Arrays.asList(new AES_256_GCM(context), new XChaCha20_Poly1305(context));

        this.algorithmSelector = new AlgorithmSelector(deviceProfiler, algorithms);

        Log.d(TAG, "Encryption Manager initialized");
        Log.d(TAG, "Device Profile: " + deviceProfiler.getPerformanceTier());
        Log.d(TAG, "Recommended: " + getRecommendedAlgorithm().getAlgorithmName());
    }


    // Delegate AlgorithmSelector
    public List<InterfaceEncryptionAlgorithm> getAvailableAlgorithms() {
        return algorithmSelector.getAvailableAlgorithms();
    }

    public InterfaceEncryptionAlgorithm getAlgorithmName(String name) {
        return algorithmSelector.getAlgorithmByName(name);
    }

    public InterfaceEncryptionAlgorithm getRecommendedAlgorithm() {
        return algorithmSelector.getRecommendedAlgorithm();
    }


    // Performance Tracking
    public PerformanceMetrics.PerformanceSnapshot getPerformanceMetrics() {
        return performanceMetrics.getSnapshot();
    }

    public void resetMetrics() {
        performanceMetrics.reset();
    }


    // Cache Management
    public CacheManager.CacheStats getCacheStats() {
        return cacheManager.getStats();
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up EncryptionManager resources");
        cacheManager.clearAll();
    }


    // Select appropriate Tink key parameter based on algorithm and device capabilities
    private Parameters selectKeyParameters(InterfaceEncryptionAlgorithm algorithm) throws GeneralSecurityException {
        int segmentSize = algorithm.getOptimalSegmentSize();

        if (algorithm.getAlgorithmName().contains("AES")) {
            return AesGcmHkdfStreamingParameters.builder()
                    .setKeySizeBytes(32) // 256 bits
                    .setDerivedAesGcmKeySizeBytes(32) // 256 bits
                    .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                    .setCiphertextSegmentSizeBytes(segmentSize)
                    .build();
        } else {
            // ChaCha20 uses Aead, not streaming
            return PredefinedAeadParameters.XCHACHA20_POLY1305;
        }
    }

    /**
     * Encrypts a file using the specified algorithm
     */
    public EncryptionResult encryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, File outputFile) {
        long startTime = System.nanoTime();
        long keysetStartTime = System.nanoTime();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file does not exist", inputFile);
            }

            // Check if file contains any data
            if (inputFile.length() == 0) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file is empty", inputFile);
            }

            // Generate unique key for this specific file
            String fileKeyId = keyManager.generateFileKeyId(outputFile);
            KeysetHandle keysetHandle = keyManager.createKeysetForAlgorithm(algorithm.getAlgorithmName(), algorithm.getOptimalSegmentSize());

            // Store key for later decryption
            fileKeyMap.put(fileKeyId, keysetHandle);
            cacheManager.cacheKeyset(fileKeyId, keysetHandle);
            keyManager.storeKeyset(fileKeyId, keysetHandle);

            // Get or create keyset caching
            //KeysetHandle keysetHandle = cacheManager.getCachedKeyset(algorithm.getAlgorithmName());

            if (keysetHandle == null) {
                // Cache miss - create new keyset and record time
                long generationStart = System.nanoTime();
                // Create keyset parameters for encryption
                Parameters parameters = selectKeyParameters(algorithm);
                keysetHandle = keyManager.createStreamingKeysetHandle(parameters);

                long generationTime = System.nanoTime() - generationStart;

                cacheManager.cacheKeyset(algorithm.getAlgorithmName(), keysetHandle);
                cacheManager.recordKeysetGenerationTime(generationTime);
                Log.d(TAG, "Created and cached new keyset for " + algorithm.getAlgorithmName() + " in " + generationTime/1_000_000 + "ms");
            } else {
                Log.d(TAG, "Using cached keyset for " + algorithm.getAlgorithmName());
            }

            //lastEncryptionKey = keysetHandle;

            // Debug keyset
            debugKeysetInfo(keysetHandle, "Encryption using");

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
    public EncryptionResult decryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, File outputFile) {
        long startTime = System.nanoTime();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Input file does not exist", inputFile);
            }

            // Check if we have a valid keyset
//            if (lastEncryptionKey == null) {
//                // Try to get from cache first
//                lastEncryptionKey = cacheManager.getCachedKeyset(algorithm.getAlgorithmName());
//                if (lastEncryptionKey == null) {
//                    return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT,
//                            "No encryption key available. Encrypt a file first or check cache.", inputFile);
//                }
//                Log.d(TAG, "Retrieved keyset from cache for decryption: " + algorithm.getAlgorithmName());
//            }

            KeysetHandle keysetHandle = cacheManager.getCachedKeyset(algorithm.getAlgorithmName());
                if (keysetHandle == null) {
                    return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT,
                            "No encryption key available. Encrypt a file first or check cache.", inputFile);
                }
                Log.d(TAG, "Retrieved keyset from cache for decryption: " + algorithm.getAlgorithmName());

            // Debug keyset
            debugKeysetInfo(lastEncryptionKey, "Decryption using");

            // Perform decryption
            boolean success = algorithm.decryptFile(inputFile, outputFile, keysetHandle, null);

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


    private void debugKeysetInfo(KeysetHandle keysetHandle, String operation) {
        try {
            if (keysetHandle != null) {
                Log.d("KeyDebug", operation + " - Keyset exists: " +
                        (keysetHandle.getPrimary().toString()));
            } else {
                Log.d("KeyDebug", operation + " - Keyset is NULL");
            }
        } catch (Exception e) {
            Log.e("KeyDebug", operation + " - Error checking keyset", e);
        }
    }
}
