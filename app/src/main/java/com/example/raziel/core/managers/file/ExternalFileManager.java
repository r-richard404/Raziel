package com.example.raziel.core.managers.file;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


//TODO: Add security exceptions
public class ExternalFileManager implements FileManager{
    private final Context context;
    private Uri currentInputUri; // reading selected files
    private Uri currentOutputUri; // writing to selected locations


    public ExternalFileManager(Context context) {
        this.context = context;
    }


    public void setInputFileUri(Uri fileUri) {
        this.currentInputUri = fileUri;
    }


    public void setOutputFileUri(Uri fileUri) {
        this.currentOutputUri = fileUri;
    }


    @Override
    public byte[] readFile(String FilePath) throws IOException {
        // Using Uri to read external files
        if (currentInputUri == null) {
            throw new IOException("No input file selected");
        }
        return readFromUri(currentInputUri);
    }


    @Override
    public void writeFile(String FilePath, byte[] data) throws IOException {
        if (currentOutputUri == null) {
            throw new IOException("No output file selected");
        }
        writeFromUri(currentOutputUri, data);
    }


    public byte[] readFromUri(Uri uri) throws IOException {
        try(InputStream inputStream = context.getContentResolver().openInputStream(uri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            int nRead;
            byte[] data = new byte[16384]; //16 KB buffer

            while((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }


    public void writeFromUri(Uri uri, byte[] data) throws IOException {
        try(OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(data);
            } else {
                throw new IOException("Cannot open output stream for URI: " + uri);
            }
        }
    }


    @Override
    public InputStream getInputStream(String filePath) throws IOException {
        if (currentInputUri == null) {
            throw new IOException("No input file selected");
        }
        return context.getContentResolver().openInputStream(currentInputUri);
    }


    @Override
    public OutputStream getOutputStream(String filePath) throws IOException {
        if (currentOutputUri == null) {
            throw new IOException("No output file selected");
        }
        return context.getContentResolver().openOutputStream(currentOutputUri);
    }


    @Override
    public boolean deleteFile(String filePath) throws IOException {
        File file = new File(context.getFilesDir(), filePath);
        if (file.exists()) {
            return file.delete();
        } else {
            throw new IOException("File does not exist and cannot be deleted");
        }
    }


    //TODO: Add more file types
    @Override
    public String[] getSupportedFileTypes() {
        return new String[] {"jpg", "png", "pdf", "txt"};
    }


    @Override
    public long getFileSize(String filePath) throws IOException{
        File file = new File(context.getFilesDir(), filePath);
        return file.length();
    }
}
