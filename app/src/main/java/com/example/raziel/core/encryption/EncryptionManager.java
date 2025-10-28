package com.example.raziel.core.encryption;

import android.content.Context;

import com.example.raziel.core.encryption.algorithms.AES_256;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;

import java.io.File;
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

    // Context needed for device capability detection (memory class, CPU cores, etc)
    private final Context context;

    // Currently using only AES-256
    // TODO: add ChaCha20-Poly1305 for devices without AES-NI
    private static final List<InterfaceEncryptionAlgorithm> availableAlgorithms;

    /**
     * Constructor initialises encryption manager with device context
     *
     * Context matters because:
     * Algorithms require to detect device capabilities
     * Memory class determines optimal buffer size
     * CPU core count affects parallel processing decisions
     *
     * @param context Android context for system service access
     */
    public EncryptionManager(Context context) {
        this.context = context;

        // Initialising available algorithms
        // Context is passed for algorithms to adapt to device capabilities

        this.availableAlgorithms = Arrays.asList(new AES_256(context));
    }
    /**
     * Get list of available encryption algorithms for UI display
     *
     * @return List of algorithm implementations
     */


    public static List<InterfaceEncryptionAlgorithm> getAvailableAlgorithms() {
        return availableAlgorithms;
    }


    /**
     * Encrypts a file using the specified algorithm
     *
     *
     * @param inputFile The file to encrypt
     * @param algorithm The encryption algorithm to use
     * @param outputFileName Optional custom output filename
     * @return Encryption result with success/failure status and metrics
     */
    // Encrypting a file using the specified algorithm chosen
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

            // Generate encryption key
            byte[] key = algorithm.generateKey();
            if (key == null) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Failed to generate encryption key", inputFile);
            }

            // Perform encryption
            boolean success = algorithm.encryptFile(inputFile, outputFile, key, null);
            long endTime = System.nanoTime();
            long elapsedMS = (endTime - startTime) / 1_000_000; // Convert nano to milliseconds

            if (success) {
                // Zero out the key from memory immediately after use to ensure security
                // This prevents key leakage through memory dumps or swap
                Arrays.fill(key, (byte) 0);

                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.ENCRYPT, endTime - startTime, inputFile.length());
            } else {
                // Zero out key in case of failure
                Arrays.fill(key, (byte) 0);
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption process failed", inputFile);
            }
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption error: " + e.getMessage(), inputFile);
        }
    }


    /**
     * Decrypting file using the specified algorithm
     * This demo implementation generates a new key for decryption testing
     * It will use the key by:
     * 1. Stored in Android KeyStore
     * 2. Encrypted with user password using PBKDF2 (600,000+ iterations)
     * 3. Protected with biometric authentication
     * 4. Never stored in plaintext SharedPreferences or files
     *
     * TODO: Key management architecture
     * 1. Master key in Android Keystore
     * 2. File encryption keys encrypted with master key
     * 3. Key derivation caching for password-based keys
     * 4. Secure wiping after use
     *
     * @param inputFile The encrypted file to decrypt
     * @param algorithm The algorithm used for encryption
     * @param outputFileName Optional custom output filename
     * @return EncryptionResult with success/failure status and metrics
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

            byte[] key = algorithm.generateKey();

            // Generate output file path
            String originalName = inputFile.getName().replace(".encrypted", "");
            File outputFile = new File(inputFile.getParent(), outputFileName != null ? outputFileName : "decrypted_" + originalName);

            // Perform decryption
            boolean success = algorithm.decryptFile(inputFile, outputFile, key, null);
            long endTime = System.nanoTime());
            long elapsedMS = (startTime - endTime) / 1_000_000;

            // Security practice to zero out keys after use
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }

            if (success) {
                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.DECRYPT, startTime - endTime, inputFile.length());
            } else {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption process failed - possibly wrong key or corrupted file", inputFile);
            }
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption error: " + e.getMessage(), inputFile);
        }
    }


    /**
     * Find algorithm implementation by name
     *
     * @param name Algorithm name (e.g. AES)
     * @return  Algorithm implementation or null if not found
     */
    public static InterfaceEncryptionAlgorithm getAlgorithmByName(String name) {
        for (InterfaceEncryptionAlgorithm algorithm : availableAlgorithms) {
            if (algorithm.getAlgorithmName().equals(name)) {
                return algorithm;
            }
        }
        return null;
    }


    /**
     * Clean up encryption resources when shutting down
     *
     * This is called in Activity's onDestroy() or when switching encryption providers.
     * Ensures proper cleanup of:
     * 1. ThreadLocal cipher instances
     * 2. Cached keys
     * 3. Buffer pools
     *
     * It matters because:
     * 1. Prevents memory leaks in long-running apps
     * 2. Clears sensitive cryptographic material
     * 3. Releases native resources used by crypto providers
     *
     */
    public void cleanup() {
        // Clean up each algorithm's resources
        for (InterfaceEncryptionAlgorithm algorithm : availableAlgorithms) {
            if (algorithm instanceof AES_256) {
                AES_256.cleanup();
            }
            // TODO: Add cleanup for other algorithm types
        }
    }
}
