package com.example.raziel.core.encryption;

import com.example.raziel.core.encryption.algorithms.AES_256;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Providing a simple interface to complex encryption subsystem
* Uses Single Responsibility by managing encryption operations and algorithm selection
* Dependent on InterfaceEncryptionAlgorithm abstraction
*/

public class EncryptionManager {
    // Available encryption algorithms
    // TODO: Left list only with 1 algorithm until the others are implemented
    private static final List<InterfaceEncryptionAlgorithm> availableAlgorithms = Arrays.asList(new AES_256());


    public static List<InterfaceEncryptionAlgorithm> getAvailableAlgorithms() {
        return availableAlgorithms;
    }


    // Encrypting a file using the specified algorithm chosen
    public EncryptionResult encryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, String outputFileName) {
        long startTime = System.currentTimeMillis();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file does not exist", inputFile);
            }

            // Check if file contains any data
            if (inputFile.length() == 0) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Input file is empty", inputFile);
            }

            // Generate output file path
            File outputFile = new File(inputFile.getParent(), outputFileName != null ? outputFileName : inputFile.getName() + ".encrypted");

            // Generate encryption key
            byte[] key = algorithm.generateKey();
            if (key == null) {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Failed to generate encryption key", inputFile);
            }

            // Perform encryption
            boolean success = algorithm.encryptFile(inputFile, outputFile, key, null);
            long endTime = System.currentTimeMillis();

            if (success) {
                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.ENCRYPT, endTime - startTime, inputFile.length());
            } else {
                return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption process failed", inputFile);
            }
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.ENCRYPT, "Encryption error: " + e.getMessage(), inputFile);
        }
    }


    // Decrypting file using the specified algorithm
    public EncryptionResult decryptFile(File inputFile, InterfaceEncryptionAlgorithm algorithm, String outputFileName) {
        long startTime = System.currentTimeMillis();

        try {
            // Input validation
            if (!inputFile.exists()) {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Input file does not exist", inputFile);
            }

            // For decryption the key needs to be handled properly
            // TODO: retrieve key from secure storage not generated for testing purposes
            // Key is generated instead of retrieved to debug the process properly

            byte[] key = algorithm.generateKey();

            // Generate output file path
            String originalName = inputFile.getName().replace(".encrypted", "");
            File outputFile = new File(inputFile.getParent(), outputFileName != null ? outputFileName : "decrypted_" + originalName);

            // Perform decryption
            boolean success = algorithm.decryptFile(inputFile, outputFile, key, null);
            long endTime = System.currentTimeMillis();

            if (success) {
                return EncryptionResult.success(inputFile, outputFile, algorithm.getAlgorithmName(), EncryptionResult.Operation.DECRYPT, startTime - endTime, inputFile.length());
            } else {
                return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption process failed - possibly wrong key or corrupted file", inputFile);
            }
        } catch (Exception e) {
            return EncryptionResult.failure(EncryptionResult.Operation.DECRYPT, "Decryption error: " + e.getMessage(), inputFile);
        }
    }


    // Find algorithm by name
    public static InterfaceEncryptionAlgorithm getAlgorithmByName(String name) {
        for (InterfaceEncryptionAlgorithm algorithm : availableAlgorithms) {
            if (algorithm.getAlgorithmName().equals(name)) {
                return algorithm;
            }
        }
        return null;
    }
}
