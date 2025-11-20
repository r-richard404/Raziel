package com.example.raziel.core.encryption.algorithms;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

// XChaCha20-Poly1305 Tink implementation
public class XChaCha20_Poly1305 implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "XChaCha20-Poly1305";
    private static final long MIN_UPDATE_INTERVAL_MS = 50; // around 20 FPS
    private final DeviceProfiler deviceProfiler;
    private ProgressCallback progressCallback;

    public XChaCha20_Poly1305(Context context) {
        this.deviceProfiler = new DeviceProfiler(context);
        Log.d(TAG, String.format("Initialised XChaCha20-Poly1305"));
    }

    @Override
    public String getAlgorithmName() { return "XChaCha20-Poly1305";
    }

    @Override
    public String getSecurityStrength() {
        return "Military grade (Software Optimised)";
    }

    @Override
    public int getOptimalSegmentSize() {
        return 0;
    }

    @Override
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    // Clean resource closure
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try  {
                closeable.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing stream", e);
            }
        }
    }

    @Override
    public boolean encryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            Aead aead = keysetHandle.getPrimitive(Aead.class);

            // Use buffered reading for progress tracking
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);

            //byte[] buffer = new byte[bufferSize];
            long totalBytes = inputFile.length();
            //long processedBytes = 0;
            long lastUpdateTime = 0;

            // Limited by Tink's Aead primitive
            byte[] fileData = new byte[(int) totalBytes];

            // Read entire file
            int bytesRead = fis.read(fileData);
            if (bytesRead != totalBytes) {
                throw new IOException("Failed to read entire file");
            }

            // Tink Encryption
            byte[] encryptedData = aead.encrypt(fileData, associatedData);
            fos.write(encryptedData);

            // Final update
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(totalBytes, totalBytes);
            }

            // Clear sensitive data from memory since whole file must be stored in memory for encryption
            Arrays.fill(fileData, (byte) 0);

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (totalBytes / 1024.0 / 1024.0) / (elapsedMS / 1000.0);
            Log.d(TAG, String.format("Encryption complete: %.2f MB in %.2f ms (%.2f MB/s)", totalBytes / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Encryption failed", e);

            if (outputFile.exists()) {
                boolean deleted = outputFile.delete();
                Log.d(TAG, "Cleaned up partial output file: " + deleted);
            }
            return false;
        } finally {
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }

    @Override
    public boolean decryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        long bytesProcessed = 0;

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            Aead aead = keysetHandle.getPrimitive(Aead.class);

            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);

            long totalBytes = inputFile.length();
            byte[] encryptedData = new byte[(int) totalBytes];

            // Read entire encrypted file
            int bytesRead = fis.read(encryptedData);
            if (bytesRead != totalBytes) {
                throw new IOException("Failed to read entire encrypted file");
            }

            // Tink Decryption
            byte[] decryptedData = aead.decrypt(encryptedData, associatedData);
            fos.write(decryptedData);

            // Clear sensitive data from memory
            Arrays.fill(encryptedData, (byte) 0);
            Arrays.fill(decryptedData, (byte) 0);

            // Final Update
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(totalBytes, totalBytes);
            }

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (totalBytes / 1024.0 / 1024.0) / (elapsedMS / 1000.0);
            Log.d(TAG, String.format("Decryption complete: %.2f MB in %.2f ms (%.2f MB/s)", totalBytes / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Encryption failed", e);

            if (outputFile.exists()) {
                boolean deleted = outputFile.delete();
                Log.d(TAG, "Cleaned up partial output file: " + deleted);
            }
            return false;
        } finally {
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }
}

