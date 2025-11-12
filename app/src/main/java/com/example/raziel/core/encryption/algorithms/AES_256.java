package com.example.raziel.core.encryption.algorithms;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.GeneralSecurityException;

import javax.crypto.*;

import java.nio.ByteBuffer;


/**
 * Tink AES-256-GCM with hardware accelerated
*/
public class AES_256 implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "AES256-GCM";
    private final DeviceProfiler deviceProfiler;
    private final int segmentSize;
    private final int bufferSize;
    private ProgressCallback progressCallback;

    // Progress tracking
    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL_MS = 50; // around 20 FPS

    public AES_256(Context context) {
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
        long bytesProcessed = 0;
        long totalBytes = inputFile.length();

        ReadableByteChannel source = null;
        WritableByteChannel dest = null;
        OutputStream encryptingStream = null;

        try {
            // Get StreamingAead primitive from keyset
            StreamingAead streamingAead = keysetHandle.getPrimitive(StreamingAead.class);

            // Setup input stream with buffering
            FileInputStream fis = new FileInputStream(inputFile);
            BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);
            source = Channels.newChannel(bis);

            // Setup output stream with buffering
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
            // Use empty byte array instead of null for associatedData because Tink can't handle null
            byte[] actualAssociatedData = associatedData != null ? associatedData : new byte[0];

            encryptingStream = streamingAead.newEncryptingStream(bos, associatedData);
            dest = Channels.newChannel(encryptingStream);

            // Use direct buffer for better performance on supported devices
            boolean useDirectBuffer = deviceProfiler.shouldUseDirectBuffers();
            ByteBuffer byteBuffer = useDirectBuffer ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);

            long currentTime;

            while (source.read(byteBuffer) != -1) {
                 byteBuffer.flip();
                 long chunkBytes = byteBuffer.remaining();
                 bytesProcessed += chunkBytes;
                 dest.write(byteBuffer);
                 byteBuffer.clear();

                 // Throttled progress updates for smooth UI
                 currentTime = System.currentTimeMillis();
                 if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                     progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                     lastUpdateTime = currentTime;
                 }
            }

            // Final progress update
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(totalBytes, totalBytes);
            }

            // Close streams to finalise encryption
            dest.close();
            source.close();

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
            closeQuietly(dest);
            closeQuietly(source);
            closeQuietly(encryptingStream);
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        long bytesProcessed = 0;
        long totalBytes = inputFile.length();

//        ReadableByteChannel source = null;
//        WritableByteChannel dest = null;
//        InputStream decryptingStream = null;
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

            // Setup input stream with buffering
            FileInputStream fis = new FileInputStream(inputFile);
            BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);

            Log.d(TAG, "Creating decrypting stream for file: " + inputFile.length() + " bytes");

            // Use empty byte array instead of null for associatedData because Tink can't handle null
            byte[] actualAssociatedData = associatedData != null ? associatedData : new byte[0];
            decryptingStream = streamingAead.newDecryptingStream(bis, actualAssociatedData);

            // Setup output stream with buffering
            outputStream = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(outputStream, bufferSize);

            // Use simple byte array instead of ByteBuffer to avoid potential issues
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long currentTime;

            while ((bytesRead = decryptingStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                bytesProcessed += bytesRead;

                // Throttled progress updates
                currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                    progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                    lastUpdateTime = currentTime;
                }
            }

            bos.flush();

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
