package com.example.raziel.core.encryption.algorithms;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;

import java.io.*;
import java.security.GeneralSecurityException;


/**
 * Tink AES-256-GCM with hardware accelerated
*/
public class AES_256_GCM implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "AES256-GCM";
    private final DeviceProfiler deviceProfiler;
    private final int segmentSize;
    private final int bufferSize;
    private ProgressCallback progressCallback;

    // Progress tracking
    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL_MS = 50; // around 20 FPS

    public AES_256_GCM(Context context) {
        this.deviceProfiler = new DeviceProfiler(context);
        this.segmentSize = deviceProfiler.getOptimalSegmentSize().bytes();
        this.bufferSize = deviceProfiler.getOptimalBufferSize();

        Log.d(TAG,  String.format("Initialised AES256-GCM with segment=%dKB, buffer=%dMB", segmentSize/1024, bufferSize/(1024*1024)));
    }

    @Override
    public String getAlgorithmName() {
        return "AES-256-GCM";
    }

    @Override
    public String getSecurityStrength() {
        return deviceProfiler.hasAESHardware() ? "Military Grade (Hardware Accelerated)" : "Military Grade";
    }

    @Override
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }


    @Override
    public int getOptimalSegmentSize() {
        return deviceProfiler.getOptimalSegmentSize().bytes;
    }

    // Clean resource closure
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing resource", e);
            }
        }
    }

    @Override
    public boolean encryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        long startTime = System.nanoTime();

        FileInputStream fis = null;
        OutputStream encryptingStream = null;

        try {
            // Get StreamingAead primitive from keyset
            StreamingAead streamingAead = keysetHandle.getPrimitive(StreamingAead.class);

            // Use empty byte array if null for associatedData because Tink can't handle null
            byte[] actualAssociatedData = associatedData != null ? associatedData : new byte[0];

            fis = new FileInputStream(inputFile);
            FileOutputStream fos = new FileOutputStream(outputFile);
            encryptingStream = streamingAead.newEncryptingStream(fos, actualAssociatedData);

            // Buffered copy, Tink handles the streaming
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long totalBytes = inputFile.length();
            long bytesProcessed = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                encryptingStream.write(buffer, 0, bytesRead);
                bytesProcessed += bytesRead;

                // Progress updates
                long currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                     progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                     lastUpdateTime = currentTime;
                 }
            }

            // Final progress update
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(totalBytes, totalBytes);
            }



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
            closeQuietly(encryptingStream);
            closeQuietly(fis);
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        InputStream decryptingStream = null;
        OutputStream outputStream = null;

        try {
            // Validate input file
            if (!inputFile.exists() || inputFile.length() == 0) {
                Log.e(TAG, "Input file does not exist or is empty");
                return false;
            }

            // Get StreamingAead primitive from keyset
            StreamingAead streamingAead = keysetHandle.getPrimitive(StreamingAead.class);

            // Use empty byte array if null for associatedData because Tink can't handle null
            byte[] actualAssociatedData = associatedData != null ? associatedData : new byte[0];

            // Tink streaming decryption
            FileInputStream fis = new FileInputStream(inputFile);
            decryptingStream = streamingAead.newDecryptingStream(fis, actualAssociatedData);

            outputStream = new FileOutputStream(outputFile);

            // Buffered copy
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long totalBytes = inputFile.length();
            long bytesProcessed = 0;
            long lastUpdateTime = 0;

            while ((bytesRead = decryptingStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesProcessed += bytesRead;

                // Throttled progress updates
                long currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                    progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                    lastUpdateTime = currentTime;
                }
            }

            // Final update
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(totalBytes, totalBytes);
            }

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (totalBytes / (1024.0 * 1024.0)) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Decryption complete: %.2f MB in %.2f ms (%.2f MB/s)",
                    totalBytes / (1024.0 * 1024.0), elapsedMS, throughputMBs));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed with error: " + e.getMessage(), e);

            if (outputFile.exists()) {
                boolean deleted = outputFile.delete();
                Log.d(TAG, "Cleaned up partial output file: " + deleted);
            }
            return false;

        } finally {
            closeQuietly(decryptingStream);
            closeQuietly(outputStream);
        }
    }
}
