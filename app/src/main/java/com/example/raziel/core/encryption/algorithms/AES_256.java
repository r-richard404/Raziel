package com.example.raziel.core.encryption.algorithms;

import android.util.Log;
import java.io.*;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/* AES-256-GCM implementation
 * Mathematical Foundation:
 * - Advanced Encryption Standard with 256-bit keys
 * - Key size: 256 bits (32 bytes) - provides 2^128 security level
 * - Block size: 128 bits
 * - IV size: 12 bytes (96 bits) - recommended for GCM
 * - Authentication tag: 128 bits
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
            // Use cryptographically secure random number generator
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
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) + " bytes, expected" + KEY_LENGTH_BYTES);
            return false;
        }
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(inputFile);
            outputStream = new FileOutputStream(outputFile);

            // Generate secure IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom. nextBytes(iv);

            // Initialise cipher with GCM parameters
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Add additional authenticated data if provided
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Write IV to output file for decryption
            outputStream.write(iv);

            // Encrypt data in chunks to handle large files efficiently
            byte[] buffer = new byte[8192]; // 8KB chunks
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (encryptedChunk != null) {
                    outputStream.write(encryptedChunk);
                }
            }

            // Finalise encryption and write authentication tag
            byte[] finalBlock = cipher.doFinal();
            outputStream.write(finalBlock);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed for " + inputFile.getName(), e);
            return false;
        } finally {
            // Close streams properly
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData) {
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            Log.e(TAG, "Invalid key length: " + (key == null ? "null" : key.length) + " bytes, expected: " + KEY_LENGTH_BYTES);
            return false;
        }

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(inputFile);
            outputStream = new FileOutputStream(outputFile);

            // Read IV from beginning of encrypted file
            byte[] iv = new byte[IV_LENGTH];
            int bytesRead = inputStream.read(iv);
            if (bytesRead != IV_LENGTH) {
                throw new SecurityException("Invalid encrypted file format");
            }

            // Initialise cipher with same GCM parameters
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Add same Additional Authenticated Data
            if (additionalData != null) {
                cipher.updateAAD(additionalData);
            }

            // Decrypt data in chunks
            byte[] buffer = new byte[8192]; //8KB

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] decryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (decryptedChunk != null) {
                    outputStream.write(decryptedChunk);
                }
            }

            // Finalise decryption and verify authentication tag
            byte[] finalBlock = cipher.doFinal();
            outputStream.write(finalBlock);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed for +" + inputFile.getName(), e);
            return false;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams" , e);
            }
        }
    }
}
