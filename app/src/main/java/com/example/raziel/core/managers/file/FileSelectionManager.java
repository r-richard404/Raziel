package com.example.raziel.core.managers.file;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileSelectionManager {
    private static final String TAG = "FileSelectionManager";
    private final Context context;
    private ActivityResultLauncher<String> filePickerLauncher;
    private FileSelectionCallback callback;

    public static class FileInfo {
        public final Uri uri;
        public final String name;
        public final long size;
        public final String mimeType;
        public final File tempFile;

        public FileInfo(Uri uri, String name, long size, String mimeType, File tempFile) {
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.mimeType = mimeType;
            this.tempFile = tempFile;
        }
    }

    public interface FileSelectionCallback {
        void onFileSelected(FileInfo fileInfo);
        void onFileSelectionError(String error);
    }

    public void setCallback(FileSelectionCallback callback) {
        this.callback = callback;
    }

    public FileSelectionManager(AppCompatActivity activity) {
        this.context = activity;
        setupFilePicker(activity);
    }

    private FileInfo createFileInfoFromUri(Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();

        // Get file metadata
        String displayName = "unknown";
        long size = 0;
        String mimeType = resolver.getType(uri);

        try (var cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        }
        // Create temp file for processing
        File tempDir = new File(context.getCacheDir(), "file_selection");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempFile = new File(tempDir, System.currentTimeMillis() + "_" + displayName);

        // Copy content to temp file
        try (InputStream is = resolver.openInputStream(uri);
             OutputStream os = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        return new FileInfo(uri, displayName, size, mimeType, tempFile);
    }

    private void processSelectedFile(Uri uri) {
        new Thread(() -> {
            try {
                FileInfo fileInfo = createFileInfoFromUri(uri);
                if (callback != null) {
                    callback.onFileSelected(fileInfo);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing selected file", e);
                if (callback != null) {
                    callback.onFileSelectionError("Failed to process file " + e.getMessage());
                }
            }
        }).start();
    }

    private void setupFilePicker(AppCompatActivity activity) {
        filePickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        FileInfo fileInfo;
                        try {
                            fileInfo = createFileInfoFromUri(uri);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        if (callback != null) {
                            callback.onFileSelected(fileInfo);
                        }
                    } else if (callback != null) {
                        callback.onFileSelectionError("No file selected");
                    }
                }
        );
    }

    public void openFilePicker() {
        filePickerLauncher.launch("*/*");
    }

    public void cleanUpTempFiles() {
        File tempDir = new File(context.getCacheDir(), "file_selection");
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
}
