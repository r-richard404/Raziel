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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

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

    // Button method
    private boolean cachingEnabled = false; // Default OFF

    /**
     * Constructor initialises encryption manager with device context
     */
    public EncryptionManager(Context context) throws GeneralSecurityException, IOException {
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

    // Used to update UI in MainActivity for selective dropdown algorithms
    public String getAlgorithmUsedForFile(File encryptedFile) {
        if (encryptedFile == null || !encryptedFile.exists()) {
            return null;
        }
        try {
            EncryptionMetadata metadata = EncryptionMetadata.readHeader(encryptedFile);
            if (metadata != null && metadata.algorithmName != null && !metadata.algorithmName.isEmpty()) {
                return metadata.algorithmName;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read algorithm metadata from header for file: " + encryptedFile, e);
        }
        return null;
    }

    // Activated by button
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up EncryptionManager resources");
        cacheManager.clearAll();
    }

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

            // Generate unique key ID for this specific file
            String fileKeyId = keyManager.generateFileKeyId();

            // Get segment size based on device capabilities
            int segmentSize = deviceProfiler.getOptimalSegmentSize().bytes;

            // Create a keyset for the chosen algorithm
            KeysetHandle keysetHandle;
            EncryptionMethod method;
            String algorithmName = algorithm.getAlgorithmName();

            // Measure before generating keyset
            long keyGenerationStart = System.nanoTime();

            if ("AES-256-GCM".equals(algorithmName)) {
                // Use streaming AEAD key for AES-GCM-HKDF
                keysetHandle = keyManager.createAes256GcmStreamingKeyset(segmentSize);
                method = EncryptionMethod.CHUNKED_STREAMING;

            } else if ("XChaCha20-Poly1305".equals(algorithmName)) {
                // Use single-shot AEAD
                keysetHandle = keyManager.createXChaCha20Poly1305Keyset();
                method = EncryptionMethod.SINGLE_SHOT;

            } else {
                return EncryptionResult.failure(
                        EncryptionResult.Operation.ENCRYPT,
                        "Unsupported algorithm: " + algorithmName,
                        inputFile);
            }

            // Measure after generating keyset
            long keyGenerationEnd = System.nanoTime();
            cacheManager.recordKeysetGenerationTime(keyGenerationEnd - keyGenerationStart);

            if (cachingEnabled) {
                // Cache key in memory for fast reuse
                cacheManager.cacheKeyset(fileKeyId, keysetHandle);
            }

            // Persist keyset securely
            keyManager.storeKeyset(fileKeyId, keysetHandle);

            // Debug keyset
            debugKeysetInfo(keysetHandle, "Encryption using");

            // 6. Encrypt into a TEMP cipher-only file (no Raziel header yet)
            File cipherTemp = File.createTempFile("cipher_", ".tmp", context.getCacheDir());
            boolean success = algorithm.encryptFile(inputFile, cipherTemp, keysetHandle, new byte[0]);

            if (!success) {
                if (cipherTemp.exists()) cipherTemp.delete();
                return EncryptionResult.failure(
                        EncryptionResult.Operation.ENCRYPT,
                        "Encryption process failed",
                        inputFile
                );
            }

            debugKeysetInfo(keysetHandle, "Encryption using");

            // 7. Build final .raziel file: [Raziel header][ciphertext]
            if (outputFile.exists()) {
                // start clean
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }

            boolean headerOk = EncryptionMetadata.writeHeader(
                    outputFile,
                    method,
                    inputFile.length(),
                    segmentSize,
                    fileKeyId,
                    algorithmName
            );

            if (!headerOk) {
                if (cipherTemp.exists()) cipherTemp.delete();
                return EncryptionResult.failure(
                        EncryptionResult.Operation.ENCRYPT,
                        "Failed to write metadata header",
                        inputFile
                );
            }

            // Append ciphertext after header
            try (FileOutputStream fos = new FileOutputStream(outputFile, true);
                 FileInputStream cis = new FileInputStream(cipherTemp)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Clean up temp cipher file
            if (cipherTemp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cipherTemp.delete();
            }

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

            return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.ENCRYPT, elapsedMS, inputFile.length());

        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Encryption Failed", e);
            return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption error: " + e.getMessage(), inputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypting file using the specified algorithm
     */
    public EncryptionResult decryptFile(File encryptedFile, InterfaceEncryptionAlgorithm fallbackAlgorithm, File outputFile) {
        long startTime = System.nanoTime();

        try {
            // Input validation
            if (!encryptedFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Input file does not exist", encryptedFile);
            }


            // 1: Read metadata from inside encrypted file
            EncryptionMetadata metadata = EncryptionMetadata.readHeader(encryptedFile);
            if (metadata == null) {
                return EncryptionResult.failure(
                        EncryptionResult.Operation.DECRYPT,
                        "Invalid Raziel encrypted file (metadata header missing or corrupt)",
                        encryptedFile
                );
            }

            String fileKeyId = metadata.keyID;
            String algorithmName = metadata.algorithmName;

            if (fileKeyId == null || fileKeyId.isEmpty()) {
                return EncryptionResult.failure(
                        EncryptionResult.Operation.DECRYPT,
                        "Encrypted file is missing key ID in metadata",
                        encryptedFile
                );
            }

            if (algorithmName == null || algorithmName.isEmpty()) {
                return EncryptionResult.failure(
                        EncryptionResult.Operation.DECRYPT,
                        "Encrypted file is missing algorithm name in metadata",
                        encryptedFile
                );
            }

            // 2: Get keyset from cache or secure storage
            KeysetHandle keysetHandle = null;
            if (cachingEnabled) {
                keysetHandle = cacheManager.getCachedKeyset(fileKeyId);
            }

            if (keysetHandle == null) {
                keysetHandle = keyManager.loadKeyset(fileKeyId);
                if (keysetHandle == null) {
                    return EncryptionResult.failure(
                            EncryptionResult.Operation.DECRYPT,
                            "No encryption key available for this file",
                            encryptedFile
                    );
                }
                // Cache for subsequent operations
                if (cachingEnabled) {
                    cacheManager.cacheKeyset(fileKeyId, keysetHandle);
                }

            }

            // 3: Choose algorithm implementation based on stored algorithmName
            InterfaceEncryptionAlgorithm algorithm;

            if ("AES-256-GCM".equals(algorithmName)) {
                algorithm = new AES_256_GCM(context); // or however you construct it
            } else if ("XChaCha20-Poly1305".equals(algorithmName)) {
                algorithm = new XChaCha20_Poly1305(context);
            } else {
                // Fallback – try the one provided by UI if compatible
                if (fallbackAlgorithm != null) {
                    algorithm = fallbackAlgorithm;
                } else {
                    return EncryptionResult.failure(
                            EncryptionResult.Operation.DECRYPT,
                            "Unsupported algorithm recorded for this file: " + algorithmName,
                            encryptedFile
                    );
                }
            }

            // 4. Strip Raziel header into a temp cipher-only file
            File cipherTemp = File.createTempFile("cipher_", ".tmp", context.getCacheDir());
            try (FileInputStream fis = new FileInputStream(encryptedFile);
                 FileOutputStream cos = new FileOutputStream(cipherTemp)) {

                long toSkip = EncryptionMetadata.HEADER_SIZE;
                long skipped = 0;
                while (skipped < toSkip) {
                    long n = fis.skip(toSkip - skipped);
                    if (n <= 0) {
                        throw new IOException("Unable to skip metadata header");
                    }
                    skipped += n;
                }

                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    cos.write(buffer, 0, read);
                }
            }

            // 5. Decrypt using the algorithm on cipher-only temp file
            boolean success = algorithm.decryptFile(cipherTemp, outputFile, keysetHandle, new byte[0]);

            // Clean up temp cipher file
            if (cipherTemp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cipherTemp.delete();
            }

            // Debug keyset
            debugKeysetInfo(keysetHandle, "Decryption using");

            long endTime = System.nanoTime();
            long elapsedNS = (endTime - startTime);
            long elapsedMS =  elapsedNS / 1_000_000;

            if (!success) {
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption process failed - possibly wrong key or corrupted file", encryptedFile);
            }

            // Performance Metrics
            performanceMetrics.recordOperation(
                    algorithm.getAlgorithmName(),
                    encryptedFile.length(),
                    elapsedNS,
                    success
            );

            return EncryptionResult.success(encryptedFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.DECRYPT, elapsedMS, encryptedFile.length());
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption error: " + e.getMessage(), encryptedFile);
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
