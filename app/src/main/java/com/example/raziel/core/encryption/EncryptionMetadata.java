package com.example.raziel.core.encryption;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Metadata stored in encrypted files to enable adaptive decryption
 */
public class EncryptionMetadata {
    private static final String TAG = "EncryptionMetadata";

    // File format constants
    public static final byte[] FILE_MAGIC = "RAZIEL_ENC".getBytes(StandardCharsets.UTF_8);
    public static final int HEADER_SIZE = FILE_MAGIC.length + Integer.BYTES + Long.BYTES + Integer.BYTES;

    public final EncryptionMethod encryptionMethod;
    public final long originalFileSize;
    public final int chunkSize;
    public final long dataOffset; // Where encrypted data starts in file

    public EncryptionMetadata(EncryptionMethod encryptionMethod, long originalFileSize, int chunkSize, long dataOffset) {
        this.encryptionMethod = encryptionMethod;
        this.originalFileSize = originalFileSize;
        this.chunkSize = chunkSize;
        this.dataOffset = dataOffset;
    }

    // Write metadata header to output file
    public static boolean writeHeader(File outputFile, EncryptionMethod encryptionMethod, long fileSize, int chunkSize) throws FileNotFoundException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            ByteBuffer header =  ByteBuffer.allocate(HEADER_SIZE);
            header.put(FILE_MAGIC);
            header.putInt(encryptionMethod.id);
            header.putLong(fileSize);
            header.putInt(chunkSize);
            fos.write(header.array());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write metadata header", e);
            return false;
        }
    }

    /**
     * Read metadata header from input file
     */
    public static EncryptionMetadata readHeader(File inputFile) {
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            byte[] headerBytes = new byte[HEADER_SIZE];
            int bytesRead = fis.read(headerBytes);

            if (bytesRead != HEADER_SIZE) {
                Log.e(TAG, "Incomplete header read: " + bytesRead + " bytes");
                return null;
            }

            ByteBuffer header = ByteBuffer.wrap(headerBytes);
            byte[] magic = new byte[FILE_MAGIC.length];
            header.get(magic);

            // Verify magic bytes
            if (!Arrays.equals(magic, FILE_MAGIC)) {
                Log.e(TAG, "Invalid file format - magic bytes mismatch");
                return null;
            }

            int methodId = header.getInt();
            long fileSize = header.getLong();
            int chunkSize = header.getInt();

            EncryptionMethod encryptionMethod = EncryptionMethod.fromId(methodId);
            return new EncryptionMetadata(encryptionMethod, fileSize, chunkSize, HEADER_SIZE);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read metadata header", e);
            return null;
        }
    }

    // Validate that the file appears to be a valid encrypted file
    public static boolean isValidEncryptedFile(File file) {
        if (file == null || !file.exists() || file.length() < HEADER_SIZE) {
            return false;
        }

        EncryptionMetadata encryptionMetadata = readHeader(file);
        return encryptionMetadata != null;
    }
}
