package com.example.raziel.core.managers.file;

import android.content.Context;
import android.net.Uri;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;


//TODO: Add security exceptions

public class InternalFileManager implements FileManager {
    private final Context context;
    Uri currentInputStream;
    Uri currentOutputStream;

    public InternalFileManager(Context context) {
        this.context = context;
    }


    public void currentInputStream(Uri uri) {
        this.currentInputStream = uri;
    }


    public void setCurrentOutputStream(Uri uri) {
        this.currentOutputStream = uri;
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
    public InputStream getInputStream(String filePath) throws IOException {
        if(currentInputStream == null) {
            throw new IOException("No input file selected");
        }
        return context.getContentResolver().openInputStream(currentInputStream);
    }


    @Override
    public OutputStream getOutputStream(String filePath) throws IOException {
        if(currentOutputStream == null) {
            throw new IOException("No output file selected");
        }
        return context.getContentResolver().openOutputStream(currentOutputStream);
    }


    @Override
    public boolean deleteFile(String filePath) throws IOException {
        File file = new File(context.getFilesDir(), filePath);
        if(file.exists()) {
            return file.delete();
        } else {
            throw new IOException("File does not exist and cannot be deleted");
        }

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

