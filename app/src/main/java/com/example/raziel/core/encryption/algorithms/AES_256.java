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
    private static final String TAG = "Tink-AES256-GCM";
    private static final int OUTPUT_BUFFER_SIZE = 8 * 1024;

    private final DeviceProfiler deviceProfiler;
    private final int segmentSize;
    private final int bufferSize;
    private ProgressCallback progressCallback;

    public AES_256(Context context) {
        this.deviceProfiler = new DeviceProfiler(context);
        this.segmentSize = deviceProfiler.getOptimalSegmentSize().bytes;
        this.bufferSize = deviceProfiler.getOptimalBufferSize();

        Log.d(TAG,  String.format("Initialised with segment=%dKB, buffer=%dMB", segmentSize, bufferSize));
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
    public byte[] generateKey() {
        return new byte[0];
    }

    @Override
    public int getOptimalSegmentSize() {
        return segmentSize;
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

        try {
            // Get StreamingAead primitive from keyset
            StreamingAead streamingAead = keysetHandle.getPrimitive(StreamingAead.class);

            // Setup input stream with buffering
            FileInputStream fis = new FileInputStream(inputFile);
            BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);
            source = Channels.newChannel(bis);

            // Setup output stream with buffering
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, OUTPUT_BUFFER_SIZE);
            OutputStream encryptingStream = streamingAead.newEncryptingStream(bos, associatedData);
            dest = Channels.newChannel(encryptingStream);

            // Stream encryption with progress updates
             ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);

             while (source.read(byteBuffer) != -1) {
                 byteBuffer.flip();
                 bytesProcessed += byteBuffer.remaining();
                 dest.write(byteBuffer);
                 byteBuffer.clear();

                 if (progressCallback != null) {
                     progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                 }
             }

             // Close streams to finalise encryption
             dest.close();
             dest = null;
             source.close();
             source = null;

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (totalBytes / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Encryption complete: %.2f MB in %.2f ms (%.2f MB/s)", totalBytes / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Encryption failed", e);

            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;

        } finally {
            closeQuietly(dest);
            closeQuietly(source);
        }
    }


    @Override
    public boolean decryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData) {
        // Performance metrics for optimisation validation
        long startTime = System.nanoTime();
        long bytesProcessed = 0;
        long totalBytes = inputFile.length();

        ReadableByteChannel source = null;
        WritableByteChannel dest = null;

        try {

            // Get StreamingAead primitive from keyset
            StreamingAead streamingAead = keysetHandle.getPrimitive(StreamingAead.class);

            // Setup input stream with buffering
            FileInputStream fis = new FileInputStream(inputFile);
            BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);
            source = Channels.newChannel(bis);

            // Setup output stream with buffering
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, OUTPUT_BUFFER_SIZE);
            OutputStream encryptingStream = streamingAead.newEncryptingStream(bos, associatedData);
            dest = Channels.newChannel(encryptingStream);

            // Stream decryption with progress updates
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);

            while (source.read(byteBuffer) != -1) {
                byteBuffer.flip();
                bytesProcessed += byteBuffer.remaining();
                dest.write(byteBuffer);
                byteBuffer.clear();

                if (progressCallback != null) {
                    progressCallback.onProgressUpdate(bytesProcessed, totalBytes);
                }
            }

            // Close streams to finalise decryption
            dest.close();
            dest = null;
            source.close();
            source = null;

            // Performance logging
            long endTime = System.nanoTime();
            double elapsedMS = (endTime - startTime) / 1_000_000.0;
            double throughputMBs = (totalBytes / 1024.0 / 1024.0) / (elapsedMS / 1000.0);

            Log.d(TAG, String.format("Encryption complete: %.2f MB in %.2f ms (%.2f MB/s)", totalBytes / 1024.0 / 1024.0, elapsedMS, throughputMBs));

            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Encryption failed", e);

            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;

        } finally {
            closeQuietly(dest);
            closeQuietly(source);
        }
    }
}
