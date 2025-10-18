package com.example.raziel.core.encryption.models;

import java.io.File;

// Immutable data class that represents the result of encryption/decryption operations
// Pure data structure without behaviour


public class EncryptionResult {
    public enum Operation {ENCRYPT, DECRYPT}
    private final boolean success;
    private final File inputFile;
    private final File outputFile;
    private final String algorithmName;
    private final Operation operation;
    private final String errorMessage;
    private final long processingTimeMs;
    private final long fileSizeBytes;


    // Factory method for private constructor
    private EncryptionResult(boolean success, File inputFile, File outputFile, String algorithmName,
                             Operation operation, String errorMessage, long processingTimeMs, long fileSizeBytes) {

        this.success = success;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.algorithmName = algorithmName;
        this.operation = operation;
        this.errorMessage = errorMessage;
        this.processingTimeMs = processingTimeMs;
        this.fileSizeBytes = fileSizeBytes;
    }


    // Factory method for success
    public static EncryptionResult success(File inputFile, File outputFile, String algorithmName,
                                           Operation operation, String errorMessage, long processingTimeMs, long fileSizeBytes) {
        return new EncryptionResult(true, inputFile, outputFile, algorithmName, operation, null, processingTimeMs, fileSizeBytes);
    }

    // Factory method for failure
    public static EncryptionResult failure(File inputFile, File outputFile, String algorithmName,
                                           Operation operation, String errorMessage, long processingTimeMs, long fileSizeBytes) {
        return new EncryptionResult(false, inputFile,null, null, operation, errorMessage, 0, 0);

    }


    // Getters
    public boolean isSuccess() {return success;}
    public File getInputFile() {return inputFile;}
    public File getOutputFile() {return outputFile;}
    public String getAlgorithmName() {return algorithmName;}
    public Operation getOperation() {return operation;}
    public String getErrorMessage() {return errorMessage;}
    public long getProcessingTimeMs() {return processingTimeMs;}
    public long getFileSizeBytes() {return fileSizeBytes;}

}
