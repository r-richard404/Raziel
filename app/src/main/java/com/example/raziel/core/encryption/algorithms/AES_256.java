package com.example.raziel.core.encryption.algorithms;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import java.io.*;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Optimised AES-256-GCM with intelligent buffering and cipher reuse
 *
 * Performance Optimisations:
 * 1. Adaptive buffer sizing ( 4-8MB based on device memory)
 * 2. Cipher instance reuse to eliminate provider lookup overhead
 * 3. Pre-allocated buffers to reduce GC pressure
 * 4. Buffered output stream for aligned flash writes
 *
 * Mathematical Foundation:
 * - Advanced Encryption Standard with 256-bit keys
 * - Key size: 256 bits (32 bytes) - provides 2^128 security level
 * - Block size: 128 bits (16 bytes)
 * - IV size: 12 bytes (96 bits) - recommended for GCM
 * - Authentication tag: 128 bits (16 bytes) for integrity
 *
 * CIA Security Properties:
 * - Confidentiality: IND-CPA secure
 * - Integrity: detects unauthorized modifications
 * - Authentication: verifies data origin
*/
public class AES_256 implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "AES256";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_LENGTH = 12; // 96 bits optimal standard for GCM performance
    private static final int TAG_LENGTH = 128; // 16 bytes for authentication tag
    private static final int KEY_LENGTH_BYTES = KEY_SIZE / 8; // 32 bytes

    // Using Cipher instance pool for optimisation (thread-safe via ThreadLocal)
    // Each thread gets its own cipher instance to avoid synchronisation overhead
    // This eliminates the expensive getInstance() + provider lookup on every operation
    private static final ThreadLocal<Cipher> cipherPool = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create cipher instance for pool", e);
            return null;
        }
    });

    // Adaptive buffer sizing based on device memory class
    // Low-End devices = 2MB | Mid-Range = 4MB | High-End = 8MB
    // This prevents OutOfMemoryError while maximises throughput
    private final int optimalBufferSize;

    // Reusable SecureRandom instance
    // Creating SecureRandom costs (~100ms) so one instance is shared
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor that determines optimal buffer size for this device
     *
     * Adaptive sizing matters because:
     * Small buffers (8kb) require too mny system calls and has poor CPU cache utilisation
     * Large buffers (16MB+) risk OutOfMemory error on low-end devices
     * Optimal buffers (4-8MB) bring balance in throughput with memory safety
     *
     * @param context Android context for accessing system services
     */
    public AES_256(Context context) {
        this.optimalBufferSize = calculateOptimalBufferSize(context);
        Log.d(TAG, "Initialised with buffer size: " + (optimalBufferSize / 1024 / 1024) + "MB");
    }

    /**
     * Determines optimal buffer size based on device memory class
     *
     * Memory class:
     * Low (< 128MB): 2MB buffer to prevent OutOfMemory error on old devices
     * Medium (128-256MB): 4MB buffer to have good balance for mid-range devices
     * High (> 256MB): 8MB buffer for maximum throughput for high-end devices
     *
     * @param context Android context
     * @return optimal buffer size in bytes
     */
    private int calculateOptimalBufferSize(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                int memoryClass = am.getMemoryClass(); // MB of RAM that the app can use

                if (memoryClass >= 256) {
                    return 8 * 1024 * 1024; // 8MB for high-end devices
                } else if (memoryClass >= 128) {
                    return 4 * 1024 * 1024; // 4MB for mid range devices
                } else {
                    return 2 * 1024 * 1024; // 2MB for old devices
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not determine device memory class, using default", e);
        }

        // Safe default if we can't determine memory class
        return 4 * 1024 * 1024; // 4MB default
    }

    @Override
    public String getAlgorithmName() {
        return ALGORITHM;
    }


    @Override
    public String getSecurityStrength() {
        return "Military Grade";
    }


    @Override
    public int getKeyLength() {
        return KEY_LENGTH_BYTES;
    }


    @Override
    public byte[] generateKey() {
        try {
            // Cryptographically secure random number generator
            // Provides true randomness from OS entropy pool
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return secretKey.getEncoded();

        } catch (Exception e) {
            Log.e(TAG, "Key generation failed", e);
            return null;
        }
    }


    @Override
    public boolean encryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData) {
        // Input validation
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) + " bytes, expected" + KEY_LENGTH_BYTES);
            return false;
        }

        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        // Pre-allocated buffer that is reused
        // Eliminates repeated allocation/deallocation overhead

        byte[] buffer = new byte[optimalBufferSize];


        try (FileInputStream inputStream = new FileInputStream(inputFile);
             // FileOutputStream is wrapped with BufferedOutputStream to align flash writes
             // This optimises write operation to 16KB blocks aligned with flash memory
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outputFile), 16 * 1024)) {

            // Generate secure IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Getting cipher from thread-local pool to eliminate costly getInstance call
            Cipher cipher = cipherPool.get();
            if (cipher == null) {
                Log.e(TAG, "Failed to get cipher from pool");
                return false;
            }

            // Initialise cipher with GCM parameters for encryption
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Add additional authenticated data if provided
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Write IV to output file for decryption
            // Cannot recover key from IV so is safe to store in plaintext
            bufferedOutput.write(iv);

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (encryptedChunk != null && encryptedChunk.length > 0) {
                    bufferedOutput.write(encryptedChunk);
                }
                bytesProcessed += bytesRead;
            }

            // Finalise encryption and write authentication tag
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null && finalBlock.length > 0) {
                bufferedOutput.write(finalBlock);
            }

            // Buffered output flushed to ensure all data is written to disk
            bufferedOutput.flush();

            // Performance logging for optimisation validation
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (bytesProcessed / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Encryption complete: %.2f MB in %.2f ms (%.2f MB/s)", bytesProcessed / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Encryption failed for " + inputFile.getName(), e);

            // Clean up partial output file if it fails to encrypt
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete partial output file: " + outputFile.getName());
            }
            return false;
        } finally {
            // Buffer must be zero out to prevent key material leakage
            // Even if keys are not stored in the buffer it adds to the defense in depth
            java.util.Arrays.fill(buffer, (byte) 0);
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData) {
        // Input Validation
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) + " bytes, expected: " + KEY_LENGTH_BYTES);
            return false;
        }

        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        byte[] buffer = new byte[optimalBufferSize];

        try (FileInputStream inputStream = new FileInputStream(inputFile);
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outputFile), 16*1024)){

            // Read IV from beginning of encrypted file
            byte[] iv = new byte[IV_LENGTH];
            int bytesRead = inputStream.read(iv);
            if (bytesRead != IV_LENGTH) {
                throw new SecurityException("Invalid encrypted file format: IV missing or incomplete");
            }

            // Getting cipher from pool
            Cipher cipher = cipherPool.get();
            if (cipher == null) {
                Log.e(TAG, "Failed to get cipher from pool");
                return false;
            }

            // Initialise cipher for decryption with the same parameters as encryption
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Add same Additional Authenticated Data
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Processing encrypted data in large chunks
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] decryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (decryptedChunk != null && decryptedChunk.length > 0) {
                    bufferedOutput.write(decryptedChunk);
                }
                bytesProcessed += bytesRead;
            }

            // Finalise decryption and verify authentication tag
            // If tag doesn't match the doFinal() will throw AEADBadTagException
            // Meaning the file was tempered with or corrupted
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null && finalBlock.length > 0) {
                bufferedOutput.write(finalBlock);
            }

            bufferedOutput.flush();

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (bytesProcessed / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Decryption complete: %.2f MB in %.2f ms (%.2f MB/s)", bytesProcessed / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (javax.crypto.AEADBadTagException e) {
            // Special handling for authentication failure
            // This marks a SECURITY EVENT where the file was tampered with or wrong key used
            Log.e(TAG, "SECURITY ALERT: Authentication tag verification failed! " + "File may be corrupted or tampered with: " + inputFile.getName(), e);

            // Delete potentially malicious output
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete corrupted output file: " + outputFile.getName());
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed for " + outputFile.getName(), e);

            // Clean up failure
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete partial output file: " + outputFile.getName());
            }
            return false;
        } finally {
            // Zero out the buffer for security
            java.util.Arrays.fill(buffer, (byte) 0);
        }
    }


    // Clean up resources when algorithm instance is no longer needed
    // This is called when an algorithm is switched or shutting down encryption subsystem
    public static void cleanup() {
        cipherPool.remove();
    }
}
