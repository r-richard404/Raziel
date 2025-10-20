package com.example.raziel.core.managers.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileManager {
    // Methods for small files
    byte[] readFile(String filePath) throws IOException;
    void writeFile(String filePath, byte[] data) throws IOException;

    // Methods for large files
    InputStream getInputStream(String filePath) throws IOException;
    OutputStream getOutputStream(String filePath) throws IOException;

    boolean deleteFile(String filePath) throws IOException;
    String[] getSupportedFileTypes();
    long getFileSize(String filePath) throws IOException;
}
