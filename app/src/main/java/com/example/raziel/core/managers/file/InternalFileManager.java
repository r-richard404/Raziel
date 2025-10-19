package com.example.raziel.core.managers.file;

import android.content.Context;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;


//TODO: Add security exceptions

public class InternalFileManager implements FileManager {
    private final Context context;


    public InternalFileManager(Context context) {
        this.context = context;
    }


    @Override
    public byte[] readFile(String filePath) throws IOException {
        // App's internal storage
        File file = new File(context.getFilesDir(), filePath);
        return Files.readAllBytes(file.toPath());
    }


    @Override
    public void writeFile(String filePath, byte[] data) throws IOException {
       File file = new File(context.getFilesDir(), filePath);
       try(FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
       }
    }


    @Override
    public boolean deleteFile(String filePath) {
        File file = new File(context.getFilesDir(), filePath);
        return file.delete();
    }


    // TODO: Add more file types and test them
    @Override
    public String[] getSupportedFileTypes() {
        return new String[] {"jpg", "txt", "png", "pdf"};
    }


    @Override
    public long getFileSize(String filePath) throws IOException {
        File file = new File(context.getFilesDir(), filePath);
        return file.length();
    }

}

