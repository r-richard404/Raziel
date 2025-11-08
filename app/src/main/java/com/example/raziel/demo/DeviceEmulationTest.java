package com.example.raziel.demo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.ui.activities.MainActivity;

import junit.framework.TestResult;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Simulating performance characteristics of different android devices
 * to show adaptability and performance improvements/drawbacks
 */
public class DeviceEmulationTest {
    private static final String TAG = "DeviceEmulation";

    /**
     * Device profiles for testing
     */
    public enum DeviceProfile {
        SAMSUNG_S23_ULTRA("Samsung S23 Ultra", 12, 256, true, 8, 1.0f),
        PIXEL_7_PRO("Google Pixel 7 Pro", 12, 128, true, 8, 0.9f),
        ONEPLUS_11("OnePlus 11", 8, 128, true, 8, 0.85f),
        SAMSUNG_A54("Samsung Galaxy A54", 6, 64, false, 8, 0.6f),
        REDMI_NOTE_12("Xiaomi Redmi Note 12", 4, 48, false, 8, 0.5f),
        BUDGET_DEVICE("Generic Budget Phone", 2, 32, false, 4, 0.3f);

        final String name;
        final int ramGB;
        final int memoryClassMB;
        final boolean hasHardwareAES;
        final int cores;
        final float performanceMultiplier;

        DeviceProfile(String name, int ramGB, int memoryClassMB, boolean hasHardwareAES, int cores, float performanceMultiplier) {
            this.name = name;
            this.ramGB = ramGB;
            this.memoryClassMB = memoryClassMB;
            this.hasHardwareAES = hasHardwareAES;
            this.cores = cores;
            this.performanceMultiplier = performanceMultiplier;

        }
    }


    /**
     * Test result class
     */
    public static class TestResult {
        public final DeviceProfile device;
        public final TestScenario scenario;
        public final String algorithm;
        public final boolean success;
        public final long elapsedMs;
        public final double throughputMBps;

        public TestResult(DeviceProfile device, TestScenario scenario, String algorithm, boolean success, long elapsedMs, double throughputMBps) {
            this.device = device;
            this.scenario = scenario;
            this.algorithm = algorithm;
            this.success = success;
            this.elapsedMs = elapsedMs;
            this.throughputMBps = throughputMBps;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "%s | %s | %s | %.1f MB/s | %.2fs",
                    device.name, scenario.name, algorithm, throughputMBps, elapsedMs / 1000.0
            );
        }
    }


    /**
     * Test scenario for different file sizes
     */
    public static class TestScenario {
        public final String name;
        public final int fileSizeMB;
        public final boolean useChunking;

        public TestScenario(String name, int fileSizeMB, boolean useChunking) {
            this.name = name;
            this.fileSizeMB = fileSizeMB;
            this.useChunking = useChunking;
        }
    }

    // Standard test scenarios
    private static final List<TestScenario> TEST_SCENARIO_LIST = List.of(
            new TestScenario("Small File", 1, false),
            new TestScenario("Medium File", 10, false),
            new TestScenario("Large File", 100, true),
            new TestScenario("Extra Large File", 500, true),
            new TestScenario("Gigabyte Test", 1000, true)
    );

    private final Context context;
    private final EncryptionManager encryptionManager;

    public DeviceEmulationTests(Context context) {
        this.context = context;
        this.encryptionManager = new EncryptionManager(context);
    }




    /**
     * Calculate throughput in MB/s
     */
    private double calculateThroughput(int fileSizeMB, long elapsedMS) {
        if (elapsedMS <= 0) return 0;
        return (fileSizeMB * 1000.0) / elapsedMS;
    }


    /**
     * Create test file with specified size
     */
    private File createTestFile(int sizeMB) throws IOException {
        File testFile = new File(context.getFilesDir(), "emulation_test_file_" + sizeMB + "mb.dat");


        // Use larger buffer for file creation
        final int BUFFER_SIZE = 1024 * 1024; //1MB buffer
        byte[] buffer = new byte[BUFFER_SIZE];

        // Fill buffer with test data
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i % 256);
        }

        long targetBytes = (long) sizeMB * 1024 * 1024;
        long written = 0;

        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            while (written < targetBytes) {
                int toWrite = (int) Math.min(BUFFER_SIZE, targetBytes - written);
                fos.write(buffer, 0, toWrite);
                written += toWrite;
            }
        }

        return testFile;
    }


    /**
     * Select optimal algorithm based on device capabilities
     */
    private InterfaceEncryptionAlgorithm selectOptimalAlgorithm(DeviceProfile device) {
        String algorithmName = device.hasHardwareAES ? "AES" : "ChaCha20";

        InterfaceEncryptionAlgorithm algorithm = encryptionManager.getAlgorithmByName(algorithmName);

        // Fallback to software available if not found
        if (algorithm == null) {
            algorithm = encryptionManager.getAvailableAlgorithms().get(1);
        }

        return algorithm;
    }


    /**
     * Device performance constraints
     */
    private void deviceConstraints(DeviceProfile device) {
        Log.d(TAG, String.format("Simulating %s: %dGB RAM, %d cores, %s AES", device.name, device.ramGB, device.cores, device.hasHardwareAES ? "Hardware" : "Software"));
    }


    /**
     * Chunk processing result
     */
    private static class ChunkResult {
        final boolean success;
        final long bytesProcessed;

        ChunkResult(boolean success, long bytesProcessed) {
            this.success = success;
            this.bytesProcessed = bytesProcessed;
        }
    }


    /**
     * Process individual chunk
     */
    private ChunkResult processChunk(File inputFile, File outputFile, InterfaceEncryptionAlgorithm algorithm,
                                                  byte[] key, int chunkIndex, long chunkStart, int chunkSize) {
        try {
            // Read chunk from input file
            byte[] chunkData = new byte[chunkSize];
            try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
                raf.seek(chunkStart);
                raf.readFully(chunkData);
            }

            return new DeviceEmulationTest(true, chunkSize);

        } catch (Exception e) {
            return new ChunkResult(false, 0);
        }
    }


    /**
     * Chunked encryption for large files (>= 100MB)
     * Uses parallel processing for better performance
     */
    private void performChunkedEncryption(File inputFile, InterfaceEncryptionAlgorithm algorithm) {
        long fileSize = inputFile.length();
        int chunkSize = CHUNK_SIZE_MB * 1024 * 1024; // conver to bytes
        int numChunks = (int) Math.ceil((double) fileSize / chunkSize);

        startTime = SystemClock.elapsedRealtime();
        bytesProcessed = 0;

        updateProgress("Encryption with " + numChunks + " chunks...", 0);
        startProgressMonitoring(fileSize);

        try {
            // Generate encryption key once for all chunks
            byte[] key = algorithm.generateKey();

            // Output file for encrypted data
            File outputFile = new File(inputFile.getParent(), inputFile.getName() + ".encrypted");

            // Process chunks in parallel
            List<Future<MainActivity.ChunkResult>> futures = new ArrayList<>();

            for (int i = 0; i < numChunks; i++) {
                final int chunkIndex = i;
                final long chunkStart = (long) i * chunkSize;
                final int currentChunkSize = (int) Math.min(chunkSize, fileSize - chunkStart);

                Future<MainActivity.ChunkResult> future = executorService.submit(() ->
                        processChunk(inputFile, outputFile, algorithm, key, chunkIndex, chunkStart, currentChunkSize));
                futures.add(future);
            }

            // Wait for all chunks to complete
            boolean success = true;
            for (Future<MainActivity.ChunkResult> future : futures) {
                MainActivity.ChunkResult result = future.get();
                if (!result.success) {
                    success = false;
                    break;
                }
                bytesProcessed += result.bytesProcessed;
            }

            stopProgressionMonitoring();

            if (success) {
                lastEncryptedFile = outputFile;
                long elapsedMs = SystemClock.elapsedRealtime() - startTime;
                showChunkedResults(fileSize, elapsedMs, numChunks);
            } else {
                showResults("Chunked encryption failed");
            }
        } catch (Exception e) {
            showResults("Chunked encryption error: " + e.getMessage());
        }

        runOnUiThread(() -> {
            setUiEnabled(true);
            showProgress(false);
        });
    }

    /**
     * Run tests for all device profiles
     */
    public List<TestResult> runAllDeviceTest() {
        List<TestResult> results = new ArrayList<>();

        for (DeviceProfile device : DeviceProfile.values()) {
            Log.d(TAG, "Testing device: " + device.name);

            //TODO: Check large files on low-end devices
            for (TestScenario scenario : TEST_SCENARIO_LIST) {
//                if (device.memoryClassMB < 64 && scenario.fileSizeMB > 100) {
//                    continue;
//                }

                TestResult result = runSingleTest(device, scenario);
                results.add(result);
            }
        }

        return results;
    }


    /**
     * Generate performance comparison report
     */
    @SuppressLint("DefaultLocale")
    public static String generateReport(List<TestResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("=== RAZIEL PERFORMANCE REPORT ===\n\n");

        // Group by scenario
        for (TestScenario scenario : TEST_SCENARIO_LIST) {
            report.append(String.format("--- %s (%d MB) ---\n", scenario.name, scenario.fileSizeMB));

            for (TestResult result : results) {
                if (result.scenario == scenario) {
                    report.append(String.format("  -> %s: %.1f MB/s (%.2fs) [%s]\n",
                            result.device.name,
                            result.throughputMBps,
                            result.elapsedMs,
                            result.algorithm));
                }
            }

            report.append("\n");
        }
        //TODO: add performance insights

        return report.toString();
    }
}
