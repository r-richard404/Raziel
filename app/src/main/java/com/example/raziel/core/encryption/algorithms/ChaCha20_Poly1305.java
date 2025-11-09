package com.example.raziel.core.encryption.algorithms;

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// TODO: Edit algorithm specifications
/**
 * ChaCha20-Poly1305 implementation for software-optimised encryption
 *
 * Mathematical Foundation:
 * - ChaCha20 stream cipher with 256-bit keys
 * - Poly1305 MAC for authentication
 * - Key size: 256 bits (32 bytes) - provides 2^128 security level
 * - Nonce size: 12 bytes (96 bites) - standard for ChaCha20-Poly1305
 * - Authentication tag: 128 bits (16 bytes)
 *
 * Performance Characteristics:
 * - 2-3x faster than software AES on devices without AES-NI
 * - Pure software implementation (no hardware acceleration needed)
 * - Excellent performance on ARMv8 with NEON instructions
 * - Lower battery consumption than software AES
 *
 * Why ChaCha20-Poly1305 matters:
 * - Designed for software efficiency (uses only addition, XOR, rotation)
 * - Constant-time implementation prevents timing attacks
 * - Recommended by IETF RFC 8439 for general-purpose encryption
 * - Used in TLS 1.3, OpenSSH, Signal Protocol
 *
 * Security Properties:
 * - Confidentiality: IND-CPA secure like AES-GCM
 * - Integrity: Verifies data origin and detects tampering
 * - Authentication: Poly1305 provides strong authentication
 */
public class ChaCha20_Poly1305 implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "ChaCha20-Poly1305";
    private static final String ALGORITHM = "ChaCha20";
    private static final String TRANSFORMATION = "ChaCha20-Poly1305";
    private static final int KEY_SIZE = 256;
    private static final int NONCE_LENGTH = 12; // 96 bits for ChaCha20-Poly1305
    private static final int TAG_LENGTH = 128; // 16 bytes for Poly1305
    private static final int KEY_LENGTH_BYTES = KEY_SIZE / 8; // 32 bytes
    private static final int OUTPUT_BUFFER_SIZE = 16 * 1024;

    // Adaptive buffer sizing (same strategy as AES)
    private final int optimalBufferSize;

    // Shared SecureRandom instance
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor determines optimal buffer size for this device
     *
     * ChaCha20 benefits from large buffers even more than AES because:
     * - Stream cipher operates on continuous data without block padding
     * - Software implementation reduces per-call overhead significantly
     * - Memory bandwidth matters more than computation for ChaCha20
     *
     * @param context Android context for system service access
     */

    public ChaCha20_Poly1305(Context context) {
        this.optimalBufferSize = calculateOptimalBufferSize(context);
        Log.d(TAG, "Initialised ChaCha20-Poly1305 with buffer size: " + (optimalBufferSize / 1024 / 1024) + "MB");
    }

    /**
     * Calculate optimal buffer size based on device memory class
     * ChaCha20 uses same strategy as AES for consistency
     */
    private int calculateOptimalBufferSize(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                int memoryClass = am.getMemoryClass();

                if (memoryClass >= 512) {
                    return 8 * 1024 * 1024; // 8MB for high-end devices
                } else if (memoryClass >= 256) {
                    return 4 * 1024 * 1024; // 4MB for mid range devices
                } else {
                    return 2 * 1024 * 1024; // 2MB for low-end devices
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not determine device memory class, using default", e);
        }

        return 4 * 1024 * 1024; // 4MB safe default
    }

    @Override
    public String getAlgorithmName() { return ALGORITHM;
    }

    @Override
    public String getSecurityStrength() {
        return "Military grade (Software Optimised)";
    }

    @Override
    public int getKeyLength() {
        return KEY_LENGTH_BYTES;
    }

    @Override
    public byte[] generateKey() {
        try {
            // ChaCha20 uses standard key generation
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, secureRandom);
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
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) +
                    " bytes, expected " + KEY_LENGTH_BYTES);
            return false;
        }

        // Performance metrics
        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        // Pre-allocated buffer
        byte[] buffer = new byte[optimalBufferSize];

        FileInputStream fis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        CipherOutputStream cos = null;

        try {
            // Generate secure nonce (12 bytes for ChaCha20-Poly1305
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            // Initialise cipher for ChaCha20-Poly1305
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            // Add additional authenticated data if provided
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Setup Streaming encryption
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);
            bos = new BufferedOutputStream(fos, OUTPUT_BUFFER_SIZE);

            // Write nonce to output file (needed for decryption)
            bos.write(nonce);

            // CipherOutputStream handles encryption
            cos = new CipherOutputStream(bos, cipher);

            // Encrypt in large chunks
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
                bytesProcessed += bytesRead;
            }

            // Close in order to finalise encryption
            cos.close();
            cos = null;
            bos.close();
            bos = null;
            fos.close();
            fos = null;
            fis.close();
            fis = null;

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throghputMBs = (bytesProcessed / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Encryption complete: %.2f MB in %.2f ms (%.2f MB/s)",
                    bytesProcessed / 1024.0 / 1024.0, elapsedMS, throghputMBs));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Encryption failed for " + inputFile.getName(), e);

            // Cleanup partial output
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete partial output file: " + outputFile.getName());
            }
            return false;

        } finally {
            closeQuietly(cos);
            closeQuietly(bos);
            closeQuietly(fos);
            closeQuietly(fis);
            // Zero out buffer for security
            java.util.Arrays.fill(buffer, (byte) 0);
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData) {
        // Input validation
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) +
                    " bytes, expected: " + KEY_LENGTH_BYTES);
            return false;
        }

        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        byte[] buffer = new byte[optimalBufferSize];

        FileInputStream fis = null;
        CipherInputStream cis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try {
            // Read nonce from beginning of encrypted file
            fis = new FileInputStream(inputFile);
            byte[] nonce = new byte[NONCE_LENGTH];
            int nonceBytesRead = fis.read(nonce);
            if (nonceBytesRead != NONCE_LENGTH) {
                throw new SecurityException("Invalid encrypted file format: nonce missing or incomplete");
            }

            // Initialise cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            // Add same AAD if provided
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Setup streaming decryption
            cis = new CipherInputStream(fis, cipher);
            fos = new FileOutputStream(outputFile);
            bos = new BufferedOutputStream(fos, OUTPUT_BUFFER_SIZE);

            // Stream data
            int bytesRead;
            while ((bytesRead = cis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                bytesProcessed += bytesRead;
            }

            // Close in order to finalise decryption
            cis.close();
            cis = null;
            bos.close();
            bos = null;
            fos.close();
            fos = null;

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (bytesProcessed / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Decryption complete: %.2f MB in %.2f ms (%.2f MB/s)",
                    bytesProcessed / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed for " + inputFile.getName(), e);

            // Cleanup on failure
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete partial output file: " + outputFile.getName());
            }
            return false;

        } finally {
            closeQuietly(cis);
            closeQuietly(bos);
            closeQuietly(fos);
            closeQuietly(fis);

            // Zero out buffer for security
            java.util.Arrays.fill(buffer, (byte) 0);
        }
    }


    /**
     * Clean up resources when algorithm instance is no longer needed
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try  {
                closeable.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing stream", e);
            }
        }
    }
}
