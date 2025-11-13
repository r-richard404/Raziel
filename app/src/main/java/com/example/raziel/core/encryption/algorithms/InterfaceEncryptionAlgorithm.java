package com.example.raziel.core.encryption.algorithms;

import com.google.crypto.tink.KeysetHandle;

import java.io.File;

/**
 *  Common interface for encryption algorithms
 */

public interface InterfaceEncryptionAlgorithm {
    // Return the algorithm's display name for UI
    String getAlgorithmName();

    // Return security strength description
    String getSecurityStrength();

    // Encrypt file using Tink keyset
    boolean encryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData);

    // Decrypt file using Tink keyset
    boolean decryptFile(File inputFile, File outputFile, KeysetHandle keysetHandle, byte[] associatedData);

    // Get optimal segment size for this algorithm
    int getOptimalSegmentSize();

    // Add progress callback support
    interface ProgressCallback {
        void onProgressUpdate(long bytesProcessed, long totalBytes);
    }

    void setProgressCallback(ProgressCallback callback);
}
