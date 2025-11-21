package com.example.raziel.core.benchmarking;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.encryption.KeyManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.google.crypto.tink.KeysetHandle;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BenchmarkFileGenerator {
    private static final String TAG = "BenchmarkFileGenerator";
    private final Context context;
    private final KeyManager keyManager;

    public BenchmarkFileGenerator(Context context) throws GeneralSecurityException, IOException {
        this.context = context;
        this.keyManager = new KeyManager(context);
    }


    /**
     * Handle Keyset before generating files
     */
    public KeysetHandle createKeysetForAlgorithm(InterfaceEncryptionAlgorithm algorithm) throws GeneralSecurityException {

        if (algorithm.getAlgorithmName().contains("AES")) {

            return keyManager.createAes256GcmStreamingKeyset(64 * 1024);
        } else {
            return keyManager.createXChaCha20Poly1305Keyset();
        }
    }

    public File createBenchmarkFile(int sizeMB, String fileExtension, String algorithmName) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = String.format("bench_%dMB_%s_%s.%s", sizeMB, algorithmName.replace("-",""), timestamp, fileExtension);

        File benchmarkDir = new File(context.getExternalCacheDir(), "benchmark_files");
        if (!benchmarkDir.exists()) {
            benchmarkDir.mkdirs();
        }

        File testFile = new File(benchmarkDir, fileName);

        final int BUFFER_SIZE = 1024 * 1024;
        byte[] buffer = new byte[BUFFER_SIZE];

        // Initialise buffer with pattern based on file type
        String pattern = getPatternForFileType(fileExtension);
        byte[] patternBytes = pattern.getBytes();
        for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer[i] = patternBytes[i % patternBytes.length];
        }

        long targetBytes = (long) sizeMB * 1024 * 1024;
        long written = 0;

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(testFile), BUFFER_SIZE)) {
            // Write file header
            String header = generateFileHeader(fileExtension, sizeMB);
            bos.write(header.getBytes());
            written += header.getBytes().length;

            // Fill remaining space with pattern
            while (written < targetBytes) {
                int toWrite = (int) Math.min(BUFFER_SIZE, targetBytes - written);
                bos.write(buffer, 0, toWrite);
                written += toWrite;
            }
        }

        Log.d(TAG, String.format("Created benchmark file: %s (%.2f MB)", testFile.getName(), (double) testFile.length() / (1024 * 1024)));

        return testFile;
    }

    /**
     * Generate appropriate file header based on file type
     */
    private String generateFileHeader(String fileExtension, int sizeMB) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        switch (fileExtension.toUpperCase()) {
            case "TXT":
                return "RAZIEL ENCRYPTION TEST - TEXT FILE\n" +
                        "Created: " + timestamp + "\n" +
                        "Size: " + sizeMB + "MB\n" +
                        "Purpose: Encryption performance testing\n" +
                        "Content: The quick brown fox jumps over the lazy dog.\n" +
                        "Repeat pattern below:\n" +
                        "=" .repeat(50) + "\n";

            case "PDF":
                return "%PDF-1.4\n" +
                        "% Raziel Test PDF Document\n" +
                        "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                        "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                        "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
                        "4 0 obj\n<< /Length 100 >>\nstream\n" +
                        "BT /F1 12 Tf 72 720 Td (RAZIEL ENCRYPTION TEST PDF) Tj ET\n" +
                        "endstream\nendobj\n" +
                        "xref\n0 5\n" +
                        "0000000000 65535 f \n" +
                        "0000000009 00000 n \n" +
                        "0000000058 00000 n \n" +
                        "0000000115 00000 n \n" +
                        "0000000234 00000 n \n" +
                        "trailer\n<< /Size 5 /Root 1 0 R >>\n" +
                        "startxref\n300\n%%EOF\n";

            case "JPG":
                return "\u00FF\u00D8\u00FF\u00E0" + // JPEG Start
                        "RAZIEL TEST JPEG IMAGE - " + timestamp +
                        " - Size: " + sizeMB + "MB - This is simulated JPEG data for encryption testing.";

            case "PNG":
                return "\u0089PNG\r\n\u001a\n" + // PNG Start
                        "RAZIEL TEST PNG IMAGE - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated PNG data for encryption testing.";

            case "MP3":
                return "ID3" + // MP3 Start
                        "RAZIEL TEST MP3 AUDIO - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated audio data for encryption testing.";

            case "MP4":
                return "ftypmp42" + // MP4 Start
                        "RAZIEL TEST MP4 VIDEO - " + timestamp +
                        " - Size: " + sizeMB + "MB - Simulated video data for encryption testing.";

            case "DOCX":
                return "PK\u0003\u0004" + // ZIP header (DOCX is a zip)
                        "[Content_Types].xml" +
                        "RAZIEL TEST DOCX DOCUMENT - " + timestamp;

            case "ZIP":
                return "PK\u0003\u0004" + // ZIP header
                        "RAZIEL TEST ZIP ARCHIVE - " + timestamp +
                        " - Contains simulated compressed data for encryption testing.";

            default:
                return "RAZIEL_BENCHMARK_" + fileExtension.toUpperCase() +
                        "_" + sizeMB + "MB_" + timestamp + "\n";
        }
    }

    /**
     * Generate content pattern based on file type
     */
    private String getPatternForFileType(String fileExtension) {
        switch (fileExtension.toLowerCase()) {
            case "txt":
                return "Encryption test pattern line - ABCDEFGHIJKLMNOPQRSTUVWXYZ - 0123456789 - " +
                        "The quick brown fox jumps over the lazy dog. ";

            case "pdf":
                return "stream\nBT /F1 10 Tf 50 700 Td (Encryption test content line: PDF document simulation) Tj ET\nendstream\n";

            case "jpg":
            case "png":
                return "IMAGE_DATA_BLOCK[" + System.currentTimeMillis() + "]_RAZIEL_ENCRYPTION_TEST_PATTERN_";

            case "mp3":
            case "mp4":
                return "MEDIA_DATA_FRAME[" + System.currentTimeMillis() + "]_AUDIO_VIDEO_TEST_PATTERN_";

            case "docx":
                return "<w:p><w:r><w:t>Encryption test paragraph for DOCX document simulation.</w:t></w:r></w:p>";

            case "zip":
                return "COMPRESSED_DATA_BLOCK[" + System.currentTimeMillis() + "]_ZIP_ARCHIVE_TEST_CONTENT_";

            default:
                return "TEST_DATA_PATTERN[" + System.currentTimeMillis() + "]_FILE_TYPE_" + fileExtension + "_";
        }
    }

    public void cleanUpBenchmarkFiles() {
        File benchmarkDir = new File(context.getExternalCacheDir(), "benchmark_files");
        if (benchmarkDir.exists()) {
            File[] files = benchmarkDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
}
