package com.example.raziel.core.managers.file;

import java.io.IOException;

public interface FileManager {
    byte[] readFile(String filePath) throws IOException;
    void writeFile(String filePath, byte[] data) throws IOException;
    boolean deleteFile(String filePath);
    String[] getSupportedFileTypes();
    long getFileSize(String filePath) throws IOException;
}
