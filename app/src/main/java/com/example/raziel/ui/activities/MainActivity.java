package com.example.raziel.ui.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.raziel.R;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

// Test Encryption Foundation

public class MainActivity extends AppCompatActivity {
    private Spinner algorithmSpinner;
    private Button btnEncrypt, btnDecrypt;
    private ProgressBar progressBar;
    private TextView processStatus;
    private EncryptionManager encryptionManager;
    private File testFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialiseViews();
        setupEncryptionManager();
        createTestFile();
    }


    private void initialiseViews() {
        algorithmSpinner = findViewById(R.id.algorithmSpinner);
        btnEncrypt = findViewById(R.id.btnEncrypt);
        btnDecrypt = findViewById(R.id.btnDecrypt);
        progressBar = findViewById(R.id.progressBar);
        processStatus = findViewById(R.id.processStatus);

        // Setup algorithm spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getAlgorithmNames());
        algorithmSpinner.setAdapter(adapter);

        // Set button listeners
        btnEncrypt.setOnClickListener(v -> performEncryption());
        btnDecrypt.setOnClickListener(v -> performDecryption());
    }


    private void setupEncryptionManager() {
        encryptionManager = new EncryptionManager();
    }


    private String[] getAlgorithmNames() {
        return EncryptionManager.getAvailableAlgorithms().stream().map(InterfaceEncryptionAlgorithm::getAlgorithmName).toArray(String[]::new);
    }


    private void createTestFile() {
        try {
            testFile = new File(getFilesDir(), "test_file.txt");
            try(RandomAccessFile raf = new RandomAccessFile(testFile, "rw")){
                raf.setLength(1024 * 1024 * 50);
            }
            FileOutputStream fosSmall = new FileOutputStream(testFile);
            fosSmall.write("This is a small test file for Raziel encryption".getBytes());
            fosSmall.close();
            updateStatus("Test file created: " + testFile.getAbsolutePath() + "\n Test file size: " + testFile.length());
        } catch (IOException e) {
            updateStatus("Error creating test file: " + e.getMessage());
        }
    }


    private void performEncryption() {
        if (testFile == null || !testFile.exists()) {
            updateStatus("Test file not available");
            return;
        }

        setUiEnabled(false);
        updateStatus("Starting encryption...");

        new Thread(() -> {
            InterfaceEncryptionAlgorithm algorithm = getSelectedAlgorithm();
            EncryptionResult result = encryptionManager.encryptFile(testFile, algorithm, null);

            new Handler(Looper.getMainLooper()).post(() -> {
                handleEncryptionResult(result);
                setUiEnabled(true);
            });
        }).start();
    }


    private void performDecryption() {
        // First must be encrypted
        //TODO: At the moment just testing file encrypt/decrypt, add file selection to decrypt whenever
        //TODO: not only after an encryption
        updateStatus("Use Encrypt first, proper file selection will be added in the future");
    }


    private InterfaceEncryptionAlgorithm getSelectedAlgorithm() {
        String selectedName = (String) algorithmSpinner.getSelectedItem();
        return EncryptionManager.getAlgorithmByName(selectedName);
    }


    private void handleEncryptionResult(EncryptionResult result) {
        if (result.isSuccess()) {
            @SuppressLint("DefaultLocale") String message = String.format("%s successful!\n" + "Algorithm: %s\n" + "Time: %d ms\n" + "Input: %s\n" + "Output: %s",
                    result.getOperation(), result.getAlgorithmName(), result.getProcessingTimeMs(), result.getInputFile().getName(), result.getOutputFile().getName());
            updateStatus(message);
        } else {
            updateStatus(result.getOperation() + " failed: " + result.getErrorMessage());
        }
    }


    private void updateStatus(String message) {
        runOnUiThread(() -> processStatus.setText(message));
    }


    private void setUiEnabled(boolean enabled) {
        runOnUiThread(() -> {
            btnEncrypt.setEnabled(enabled);
            btnDecrypt.setEnabled(enabled);
            algorithmSpinner.setEnabled(enabled);
            progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        });
    }
}

