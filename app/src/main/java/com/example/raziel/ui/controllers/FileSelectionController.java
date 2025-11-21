package com.example.raziel.ui.controllers;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.example.raziel.core.managers.file.FileSelectionManager;
import com.example.raziel.ui.activities.MainActivity;

import java.io.File;
import java.util.Locale;

public class FileSelectionController implements FileSelectionManager.FileSelectionCallback{

    private final MainActivity activity;
    private final FileSelectionManager fileSelectionManager;
    private final Handler main = new Handler(Looper.getMainLooper());

    public FileSelectionController(MainActivity activity, FileSelectionManager manager) {
        this.activity = activity;
        this.fileSelectionManager = manager;
        manager.setCallback(this);
    }

    public void openPicker() {
        fileSelectionManager.openFilePicker();
    }


    @Override
    public void onFileSelected(FileSelectionManager.FileInfo fileInfo) {

        // 1. STORAGE CHECK
        File f = fileInfo.tempFile;
        if (f != null) {
            long size = f.length();
            long free = f.getParentFile().getFreeSpace();
            long required = size * 2;

            if (free < required) {
                activity.showError(
                        "Insufficient Storage",
                        "File requires ≈ " + (required / (1024*1024)) + "MB free.\n" +
                                "You have only " + (free / (1024*1024)) + "MB."
                );
                clearUI();
                return;
            }
        }

        // 2. Update Activity state
        activity.selectedFile = fileInfo;

        // 3. UI update
        main.post(() -> {
            float mb = fileInfo.size / (1024f * 1024f);
            activity.fileInfoText.setText(
                    String.format(Locale.US, "Selected: %s (%.2f MB)", fileInfo.name, mb)
            );
            activity.fileSelectionCard.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onFileSelectionError(String error) {
        activity.showError("File Selection Error", error);
    }

    public void clearUI() {
        main.post(() -> {
            activity.selectedFile = null;
            activity.fileSelectionCard.setVisibility(View.GONE);
            activity.fileInfoText.setText("No file selected");
        });
    }

    public void cleanup() {
        fileSelectionManager.cleanUpTempFiles();
    }
}
