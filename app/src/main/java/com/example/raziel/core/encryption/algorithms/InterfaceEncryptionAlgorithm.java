package com.example.raziel.core.encryption.algorithms;

import java.io.File;

// Common interface for all encryption algorithms following SOLID principles
// Open/Closed principles by easily adding new algorithms without modifying existing code

// Mathematical foundation:
// - All implementations must provide cryptographic proof of security
// - Key generation must use cryptographically secure random number generators
// - Must support authenticated encryption for data integrity

public interface InterfaceEncryptionAlgorithm {
    // Return the algorithm's display name for UI
    String getAlgorithmName();

    // Return security strength description
    String getSecurityStrength();

    // Encrypts a file using the specified key
    boolean encryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData);

    // Decrypts a file using the specified key
    boolean decryptFile(File inputFile, File outputFile, byte[] key, byte[] additionalData);

    // Generate a cryptographically secure key for this algorithm
    byte[] generateKey();

    // Get the required key length in bytes
    int getKeyLength();

}
