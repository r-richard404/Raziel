package com.example.raziel.core.encryption;

/**
 * Defines the encryption methods used for adaptive processing
 */
public enum EncryptionMethod {
    SINGLE_SHOT(1, "Single-shot encryption", "Fastest for small files (< 2MB)"),
    MEMORY_MAPPED(2, "Memory-mapped encryption", "Balanced performance for medium files"),
    CHUNKED_STREAMING(3, "Chunked streaming", "Most memory-efficient for large files");

    public final int id;
    public final String name;
    public final String description;

    EncryptionMethod(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static EncryptionMethod fromId(int id) {
        for (EncryptionMethod method : values()) {
            if (method.id == id) {
                return method;
            }
        }
        return CHUNKED_STREAMING; // Default fallback
    }
}
