package com.example.raziel.core.encryption;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.profiler.DeviceProfiler;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.config.TinkConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.Arrays;


/**
 * Core engine that provides adaptive encryption/decryption with performance matching
 * TODO: Implement other algorithms that don't have chunking/streaming and would benefit from adapter
 */
public class AdaptiveEncryptionEngine {
    private static final String TAG = "AdaptiveEncryptionEngine";
    private static final long MIN_UPDATE_INTERVAL_MS = 50; // around 20 FPS

    private final DeviceProfiler deviceProfiler;
    private final int memoryMappingThreshold;
    private final int singleShotThreshold;
    private final KeysetHandle aeadKeysetHandle;
    private final KeysetHandle streamingAeadKeysetHandle;

    public AdaptiveEncryptionEngine(Context context, KeysetHandle aeadKeysetHandle, KeysetHandle streamingAeadKeysetHandle) {
        this.deviceProfiler = new DeviceProfiler(context);
        this.memoryMappingThreshold = calculateMemoryMappingThreshold();
        this.singleShotThreshold = calculateSingleShotThreshold();
        this.aeadKeysetHandle = aeadKeysetHandle;
        this.streamingAeadKeysetHandle = streamingAeadKeysetHandle;

        Log.d(TAG, String.format("Initialised with thresholds: Single-shot < %dMB, Memory-mapped < %dMB", singleShotThreshold / (1024 * 1024), memoryMappingThreshold / (1024 * 1024)));
    }

    // Alternative constructor for XChaCha20-only use case
    public AdaptiveEncryptionEngine(Context context, KeysetHandle aeadKeysetHandle) {
        this(context, aeadKeysetHandle, null);
    }

    private int calculateSingleShotThreshold() {
        return 2 * 1024 * 1024; // 2MB
    }

    private int calculateMemoryMappingThreshold() {
        DeviceProfiler.PerformanceTier performanceTier = deviceProfiler.getPerformanceTier();
        switch (performanceTier) {
            case FLAGSHIP:
                return 500 * 1024 * 1024; // 500MB
            case HIGH_END:
                return 250 * 1024 * 1024; // 250MB
            case MID_RANGE:
                return 100 * 1024 * 1024; // 100MB
            case ENTRY_LEVEL:
            default:
                return 50 * 1024 * 1024; // 50MB
        }
    }

    private EncryptionMethod selectEncryptionMethod(long fileSize) {
        if (fileSize <= singleShotThreshold) {
            return EncryptionMethod.SINGLE_SHOT;
        } else if (fileSize <= memoryMappingThreshold && deviceProfiler.shouldUseDirectBuffers()) {
            return EncryptionMethod.MEMORY_MAPPED;
        } else {
            return EncryptionMethod.CHUNKED_STREAMING;
        }
    }

    private void updateProgress(InterfaceEncryptionAlgorithm.ProgressCallback callback, long processedBytes, long totalBytes, long lastUpdateTime) {
        if (callback != null && System.currentTimeMillis() - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
            callback.onProgressUpdate(processedBytes, totalBytes);
        }
    }

    private void cleanUpPartialFile(File file) {
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "Cleaned up partial file: " + deleted);
        }
    }


    // === Encryption Methods ===

    private boolean encryptSingleShot(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) {
        try {
            // Initialise primitive
            Aead aead = aeadKeysetHandle.getPrimitive(Aead.class);

            // Read entire file
            byte[] fileData = Files.readAllBytes(inputFile.toPath());

            // Single encryption operation
            byte[] encryptedData = aead.encrypt(fileData, associatedData);

            // Append to file (header already written)
            try (FileOutputStream fos = new FileOutputStream(outputFile, true)) {
                fos.write(encryptedData);
            }

            if (callback != null) {
                callback.onProgressUpdate(fileData.length, fileData.length);
            }

            Log.d(TAG, "Single-shot encryption completed: " + fileData.length + " bytes");
            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Single-shot encryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }

    private ByteBuffer createOptimalBuffer(int size) {
        DeviceProfiler.PerformanceTier tier = deviceProfiler.getPerformanceTier();

        // Using direct buffers only for high-performance devices with sufficient memory
        // and for buffer sizes where the benefit is meaningful
        boolean shouldUseDirect =
                (tier == DeviceProfiler.PerformanceTier.FLAGSHIP ||
                        tier == DeviceProfiler.PerformanceTier.HIGH_END) &&
                        size >= 64 * 1024;  // Only for buffers >= 64KB

        return shouldUseDirect ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }


    private boolean encryptMemoryMapped(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) throws IOException {
        try (FileChannel inChannel = FileChannel.open(inputFile.toPath(), StandardOpenOption.READ);
             FileOutputStream fos = new FileOutputStream(outputFile, true);
             FileChannel outChannel = fos.getChannel()) {

            Aead aead = aeadKeysetHandle.getPrimitive(Aead.class);
            long fileSize = inChannel.size();
            long processedBytes = 0;
            long lastUpdateTime = 0;

            int chunkSize = deviceProfiler.getOptimalChunkSize();

            // Process file in memory-mapped chunks
            long position = 0;
            while (position < fileSize) {
                long chunkLength = Math.min(chunkSize, fileSize - position);

                MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, position, chunkLength);
                byte[] chunk = new byte[(int) chunkLength];
                buffer.get(chunk);

                // Encrypt chunk
                byte[] encryptedChunk = aead.encrypt(chunk, associatedData);

                // Write with length prefix
                ByteBuffer outBuffer = ByteBuffer.allocate(Integer.BYTES + encryptedChunk.length);
                outBuffer.putInt(encryptedChunk.length);
                outBuffer.put(encryptedChunk);
                outBuffer.flip();

                outChannel.write(outBuffer);
                position += chunkLength;
                processedBytes += chunkLength;

                // Progress updates
                updateProgress(callback, processedBytes, fileSize, lastUpdateTime);
                lastUpdateTime = System.currentTimeMillis();
            }

            if (callback != null) {
                callback.onProgressUpdate(fileSize, fileSize);
            }

            return true;

        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Memory-mapped encryption failed", e);
            throw new RuntimeException(e);
        }
    }

    private boolean encryptWithTinkStreaming(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) throws IOException {
        if (streamingAeadKeysetHandle == null) {
            Log.w(TAG, "No StreamingAead keyset available, falling back to chunked AEAD");
            return encryptChunkedFallback(inputFile, outputFile, associatedData, callback);
        }

        try {
            StreamingAead streamingAead = streamingAeadKeysetHandle.getPrimitive(StreamingAead.class);
            long fileSize = inputFile.length();
            long processedBytes = 0;
            long lastUpdateTime = 0;

            try (FileInputStream inputStream = new FileInputStream(inputFile);
                 FileOutputStream outputStream = new FileOutputStream(outputFile, true);
                 WritableByteChannel encryptingChannel = streamingAead.newEncryptingChannel(outputStream.getChannel(), associatedData);) {

                ByteBuffer buffer = createOptimalBuffer(deviceProfiler.getOptimalBufferSize());

                while (inputStream.getChannel().read(buffer) > 0) {
                    buffer.flip();
                    encryptingChannel.write(buffer);
                    processedBytes += buffer.position();

                    updateProgress(callback, processedBytes, fileSize, lastUpdateTime);
                    lastUpdateTime = System.currentTimeMillis();

                    buffer.clear();
                }

                if (callback != null) {
                    callback.onProgressUpdate(fileSize, fileSize);
                }
            }
            return true;

        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Tink Streaming encryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }

    private boolean encryptChunkedFallback(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) throws FileNotFoundException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile, true)) {

            Aead aead = aeadKeysetHandle.getPrimitive(Aead.class);
            long fileSize = inputFile.length();
            long processedBytes = 0;
            long lastUpdateTime = 0;

            byte[] buffer = new byte[deviceProfiler.getOptimalBufferSize()];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                byte[] encryptedChunk = aead.encrypt(chunk, associatedData);

                // Write chunk with length prefix
                fos.write(ByteBuffer.allocate(Integer.BYTES).putInt(encryptedChunk.length).array());
                fos.write(encryptedChunk);

                processedBytes += bytesRead;

                // Progress Update
                updateProgress(callback, processedBytes, fileSize, lastUpdateTime);
                lastUpdateTime = System.currentTimeMillis();
            }

            if (callback != null) {
                callback.onProgressUpdate(fileSize, fileSize);
            }

            return true;

        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Chunked streaming encryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }


    // === Decryption Methods ===

    private boolean decryptSingleShot(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback, EncryptionMetadata encryptionMetadata) {
        try {
            Aead aead = aeadKeysetHandle.getPrimitive(Aead.class);

            // Read encrypted data (skip header)
            byte[] encryptedData = Files.readAllBytes(inputFile.toPath());
            byte[] encryptedContent = Arrays.copyOfRange(encryptedData, (int) encryptionMetadata.dataOffset, encryptedData.length);

            // Single decryption operation
            byte[] decryptedData = aead.decrypt(encryptedContent, associatedData);

            // Verify size matches expected
            if (decryptedData.length != encryptionMetadata.originalFileSize) {
                Log.e(TAG, "Decrypted size mismatch: expected " + encryptionMetadata.originalFileSize + ", got " + decryptedData.length);
                return false;
            }

            Files.write(outputFile.toPath(), decryptedData);

            if (callback != null) {
                callback.onProgressUpdate(decryptedData.length, decryptedData.length);
            }

            Log.d(TAG, "Single-shot decryption completed: " + decryptedData.length + " bytes");
            return true;

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Single-shot decryption failed", e);
            return false;
        }
    }

    private boolean decryptWithTinkStreaming(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback, EncryptionMetadata encryptionMetadata) {
        if (streamingAeadKeysetHandle == null) {
            Log.w(TAG, "No StreamingAead keyset available, falling back to chunked AEAD");
            return decryptChunkedGeneric(inputFile, outputFile, associatedData, callback, encryptionMetadata, false);
        }

        try {
            StreamingAead streamingAead = streamingAeadKeysetHandle.getPrimitive(StreamingAead.class);
            long processedBytes = 0;
            long lastUpdateTime = 0;

            try (FileInputStream cipherTextStream = new FileInputStream(inputFile);
                 FileOutputStream plainTextStream = new FileOutputStream(outputFile);
                 ReadableByteChannel decryptingChannel = streamingAead.newDecryptingChannel(cipherTextStream.getChannel(), associatedData)) {

                // Skip header for Tink streaming
                cipherTextStream.getChannel().position(encryptionMetadata.dataOffset);

                ByteBuffer buffer = createOptimalBuffer(deviceProfiler.getOptimalBufferSize());
                int bytesRead;

                while ((bytesRead = decryptingChannel.read(buffer)) > 0) {
                    buffer.flip();
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    plainTextStream.write(chunk);

                    processedBytes += bytesRead;
                    updateProgress(callback, processedBytes, encryptionMetadata.originalFileSize, lastUpdateTime);
                    lastUpdateTime = System.currentTimeMillis();

                    buffer.clear();
                }

                if (callback != null) {
                    callback.onProgressUpdate(encryptionMetadata.originalFileSize, encryptionMetadata.originalFileSize);
                }
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Tink Streaming decryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }

    private boolean decryptWithMemoryMapping(File inputFile, File outputFile, Aead aead, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback, EncryptionMetadata encryptionMetadata, long processedBytes, long lastUpdateTime) {
        try (FileChannel inChannel = FileChannel.open(inputFile.toPath(), StandardOpenOption.READ);
             FileChannel outChannel = FileChannel.open(outputFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long fileSize = inputFile.length();
            long dataSize = fileSize - encryptionMetadata.dataOffset;
            MappedByteBuffer mappedByteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, encryptionMetadata.dataOffset, dataSize);

            int optimalChunkSize = deviceProfiler.getOptimalChunkSize();
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);

            while (mappedByteBuffer.hasRemaining()) {
                if (mappedByteBuffer.remaining() < Integer.BYTES) {
                    throw new IOException("Invalid file format: incomplete length prefix");
                }

                lengthBuffer.clear();
                mappedByteBuffer.get(lengthBuffer.array());
                int chunkLength = lengthBuffer.getInt(0);

                if (mappedByteBuffer.remaining() < chunkLength) {
                    throw new IOException("Invalid file format: chunk data incomplete");
                }

                byte[] encryptedChunk = new byte[chunkLength];
                mappedByteBuffer.get(encryptedChunk);

                byte[] decryptedChunk = aead.decrypt(encryptedChunk, associatedData);
                ByteBuffer outputBuffer = ByteBuffer.wrap(decryptedChunk);
                outChannel.write(outputBuffer);

                processedBytes += decryptedChunk.length;
                updateProgress(callback, processedBytes, encryptionMetadata.originalFileSize, lastUpdateTime);
                lastUpdateTime = System.currentTimeMillis();
            }

            if (callback != null) {
                callback.onProgressUpdate(encryptionMetadata.originalFileSize, encryptionMetadata.originalFileSize);
            }

            outChannel.force(true);
            return true;
        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Memory-mapped decryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }


    private boolean decryptWithStreaming(File inputFile, File outputFile, Aead aead, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback, EncryptionMetadata encryptionMetadata, long processedBytes, long lastUpdateTime) {
        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r");
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            raf.seek(encryptionMetadata.dataOffset); // skip header

            byte[] lengthBuffer = new byte[Integer.BYTES];

            while (raf.read(lengthBuffer) == Integer.BYTES) {
                int chunkLength = ByteBuffer.wrap(lengthBuffer).getInt();
                byte[] encryptedChunk = new byte[chunkLength];

                if (raf.read(encryptedChunk) != chunkLength) {
                    throw new IOException("Invalid file format: chunk data incomplete");
                }

                byte[] decryptedChunk = aead.decrypt(encryptedChunk, associatedData);
                fos.write(decryptedChunk);

                processedBytes += decryptedChunk.length;

                // Progress updates
                updateProgress(callback, processedBytes, encryptionMetadata.originalFileSize, lastUpdateTime);
                lastUpdateTime = System.currentTimeMillis();
            }

            if (callback != null) {
                callback.onProgressUpdate(encryptionMetadata.originalFileSize, encryptionMetadata.originalFileSize);
            }

            return true;

        } catch (IOException | GeneralSecurityException e) {
            Log.e(TAG, "Chunked decryption failed", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }

    private boolean decryptChunkedGeneric(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback, EncryptionMetadata encryptionMetadata, boolean useMemoryMapping) {
        try {
            Aead aead = aeadKeysetHandle.getPrimitive(Aead.class);
            long processedBytes = 0;
            long lastUpdateTime = 0;

            if (useMemoryMapping) {
                return decryptWithMemoryMapping(inputFile, outputFile, aead, associatedData, callback, encryptionMetadata, processedBytes, lastUpdateTime);
            } else {
                return decryptWithStreaming(inputFile, outputFile, aead, associatedData, callback, encryptionMetadata, processedBytes, lastUpdateTime);
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Failed to get AEAD primitive", e);
            cleanUpPartialFile(outputFile);
            return false;
        }
    }


    // === Public Interface ===

    private boolean adaptiveEncryption(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) throws IOException {
        long fileSize = inputFile.length();
        EncryptionMethod encryptionMethod = selectEncryptionMethod(fileSize);

        Log.d(TAG, String.format("Encrypting %d bytes using %s", fileSize, encryptionMethod.name));

        // Write metadata header
        if (!EncryptionMetadata.writeHeader(outputFile, encryptionMethod, fileSize, deviceProfiler.getOptimalBufferSize())) {
            return false;
        }

        // Encrypt with selected method
        switch (encryptionMethod) {
            case SINGLE_SHOT:
                return encryptSingleShot(inputFile, outputFile, associatedData, callback);
            case MEMORY_MAPPED:
                return encryptMemoryMapped(inputFile, outputFile, associatedData, callback);
            case CHUNKED_STREAMING:
                return encryptWithTinkStreaming(inputFile, outputFile, associatedData, callback);
            default:
                return false;
        }
    }

    private boolean adaptiveDecryption(File inputFile, File outputFile, byte[] associatedData, InterfaceEncryptionAlgorithm.ProgressCallback callback) {
        // Read metadata to determine encryption method
        EncryptionMetadata encryptionMetadata = EncryptionMetadata.readHeader(inputFile);
        if (encryptionMetadata == null) {
            Log.e(TAG, "Invalid or corrupted encrypted file");
            return false;
        }

        Log.d(TAG, String.format("Decrypting using original method: %s", encryptionMetadata.encryptionMethod.name));

        // Decrypt with selected method
        switch (encryptionMetadata.encryptionMethod) {
            case SINGLE_SHOT:
                return decryptSingleShot(inputFile, outputFile, associatedData, callback, encryptionMetadata);
            case MEMORY_MAPPED:
                return decryptChunkedGeneric(inputFile, outputFile, associatedData, callback, encryptionMetadata, true);
            case CHUNKED_STREAMING:
                return decryptWithTinkStreaming(inputFile, outputFile, associatedData, callback, encryptionMetadata);
            default:
                return false;
        }
    }

    // Method to initialise Tink keys
    public static void initialiseTink() {
        try {
            // Initialise Tink with all required primitives
            TinkConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialise Tink", e);
        }
    }
}
