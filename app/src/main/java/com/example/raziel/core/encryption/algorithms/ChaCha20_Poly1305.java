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
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

// Since Google Tink doesn't support StreamingAead for ChaCha20-Poly1305 only for AES
// Creating custom adapter to process large files to read/write data in chunks without loading
// the whole file in memory
/**
 * ChaCha20-Poly1305 implementation for software-optimised encryption
 * */
public class ChaCha20_Poly1305 implements InterfaceEncryptionAlgorithm {
    private static final String TAG = "XChaCha20-Poly1305";
    private static final long MIN_UPDATE_INTERVAL_MS = 50; // around 20 FPS
    private final DeviceProfiler deviceProfiler;
    private final int segmentSize;
    private final int bufferSize;
    private ProgressCallback progressCallback;

    // For chunked encryption since Tink doesn't support streaming for ChaCha20
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks

    public ChaCha20_Poly1305(Context context) {
        this.deviceProfiler = new DeviceProfiler(context);
        this.segmentSize = deviceProfiler.getOptimalSegmentSize().bytes;
        this.bufferSize = deviceProfiler.getOptimalBufferSize();

        Log.d(TAG, String.format("Initialised XChaCha20-Poly1305 with segment=%dKB, buffer=%dMB", segmentSize/1024, bufferSize/(1024*1024)));
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
        return segmentSize;
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
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);

            byte[] buffer = new byte[bufferSize];
            long totalBytes = inputFile.length();
            long processedBytes = 0;
            long lastUpdateTime = 0;

            // Write file size first for proper decryption
            ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
            sizeBuffer.putLong(totalBytes);
            byte[] encryptedSize = aead.encrypt(sizeBuffer.array(), associatedData);

            // Write size of encrypted size filed, then the encrypted size
            fos.write(ByteBuffer.allocate(Integer.BYTES).putInt(encryptedSize.length).array());
            fos.write(encryptedSize);

            int bytesRead;
            long currentTime;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                byte[] encryptedChunk = aead.encrypt(chunk, associatedData);

                // Write chunk size followed by encrypted data
                fos.write(ByteBuffer.allocate(Integer.BYTES).putInt(encryptedChunk.length).array());
                fos.write(encryptedChunk);

                processedBytes += bytesRead;

                // Throttled progress updates
                currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                    progressCallback.onProgressUpdate(processedBytes, totalBytes);
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

            // Read and decrypt file size
            byte[] sizeLengthBytes = new byte[Integer.BYTES];
            if (fis.read(sizeLengthBytes) != Integer.BYTES) {
                throw new IOException("Invalid file format: cannot read size length");
            }

            int encryptedFileLength = ByteBuffer.wrap(sizeLengthBytes).getInt();
            byte[] encryptedSize = new byte[encryptedFileLength];
            if (fis.read(encryptedSize) != encryptedFileLength) {
                throw new IOException("Invalid file format: cannot read encrypted size");
            }

            byte[] decryptedSize = aead.decrypt(encryptedSize, associatedData);
            long totalBytes = ByteBuffer.wrap(decryptedSize).getLong();

            long processedBytes = 0;
            byte[] lengthBuffer = new byte[Integer.BYTES];
            long lastUpdateTime = 0;
            long currentTime;

            while (fis.read(lengthBuffer) == Integer.BYTES) {
                int chunkLength = ByteBuffer.wrap(lengthBuffer).getInt();
                byte[] encryptedChunk = new byte[chunkLength];
                if (fis.read(encryptedChunk) != chunkLength) {
                    throw new IOException("Invalid file format: chunk data incomplete");
                }

                byte[] decryptedChunk = aead.decrypt(encryptedChunk, associatedData);
                fos.write(decryptedChunk);

                processedBytes += decryptedChunk.length;

                // Throttled progress updates
                currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                    progressCallback.onProgressUpdate(processedBytes, totalBytes);
                    lastUpdateTime = currentTime;
                }
            }

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

