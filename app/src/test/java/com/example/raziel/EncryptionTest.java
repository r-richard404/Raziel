package com.example.raziel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.raziel.core.benchmarking.EncryptionBenchmark;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.performance.PerformanceMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class EncryptionTest {

    private Context context;
    private EncryptionManager encryptionManager;
    private File testFile;


    private File createTestFile(String filename, String content) throws Exception {
        File file = new File(context.getFilesDir(), filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
        }
        return file;
    }

    private byte[] readFileBytes(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        return data;
    }

    private String readFile(File file) throws Exception {
        return new String(readFileBytes(file));
    }

    private boolean isAllZeros(byte[] array) {
        for (byte b : array) {
            if (b != 0) return false;
        }
        return true;
    }

    private String generateLargeContent(int sizeBytes) {
        StringBuilder sb = new StringBuilder();
        String pattern = "This is test data for large file encryption " +
                "This is some meaningful data that will fill this file, to expand it to a bigger size";

        while (sb.length() < sizeBytes) {
            sb.append(pattern);
        }

        return sb.substring(0, sizeBytes);
    }


    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        encryptionManager = new EncryptionManager(context);

        // Create test file with known context
        testFile = createTestFile("test_input.txt", "This is test data for encryption validation " +
                "The content should not be altered when going through encryption/decryption phases");
    }

    @After
    public void tearDown() throws Exception {
        // Clean up test files
        if (testFile != null && testFile.exists()) {
            testFile.delete();
        }

        // Clean up any generated files
        File filesDir = context.getFilesDir();
        for (File file : filesDir.listFiles()) {
            if (file.getName().contains("test_") ||
            file.getName().contains("encrypted") ||
            file.getName().contains("decrypted")) {
                file.delete();
            }
        }

        // Clean up encryption manager resources
        if (encryptionManager != null) {
            encryptionManager.cleanup();
        }
    }

    // Verify AES-256-GCM encrypts and decrypts while preserving the data
    @Test
    public void test_AES256_EncryptionDecryption_Operations() throws Exception {
        // Get AES algorithm
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        assertNotNull("AES256 algorithm should be available", aes);

        // Encrypt the test file
        EncryptionResult encryptResult = encryptionManager.encryptFile(testFile, aes, "test_aes.enc");
        assertTrue("AES256 Encryption should succeeded", encryptResult.isSuccess());
        assertTrue("Encrypted file should exist", encryptResult.getOutputFile().exists());
        assertTrue("Encrypted file should have content", encryptResult.getOutputFile().length() > 0);

        // Decrypt the encrypted file
        EncryptionResult decryptResult = encryptionManager.decryptFile(
                encryptResult.getOutputFile(), aes, "test_aes_decrypted.txt");
        assertTrue("AES256 Decryption should succeeded", decryptResult.isSuccess());
        assertTrue("Decrypted file should exist", decryptResult.getOutputFile().exists());
        assertTrue("Decrypted file should have content", decryptResult.getOutputFile().length() > 0);

        // Verify content matches original
        String originalContent = readFile(testFile);
        String decryptedContent = readFile(decryptResult.getOutputFile());
        assertEquals("Decrypted content should match original", originalContent, decryptedContent);

        // Clean up
        encryptResult.getOutputFile().delete();
        decryptResult.getOutputFile().delete();
    }


    // Verify ChaCha20-Poly1305 encrypts and decrypts while preserving the data
    @Test
    public void test_ChaCha20_EncryptionDecryption_Operations() throws Exception {
        // Get ChaCha20 algorithm
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");
        assertNotNull("ChaCha20 should be available", chacha);

        // Encrypt the test file
        EncryptionResult encryptResult = encryptionManager.encryptFile(testFile, chacha, "test_chacha.enc");
        assertTrue("ChaCha20 Encryption should succeed", encryptResult.isSuccess());
        assertTrue("Encrypted file should exist", encryptResult.getOutputFile().exists());
        assertTrue("Encrypted file should have content", encryptResult.getOutputFile().length() > 0);

        // Decrypt the test file
        EncryptionResult decryptResult = encryptionManager.decryptFile(encryptResult.getOutputFile(), chacha, "test_chacha_decrypted.txt");
        assertTrue("ChaCha20 Decryption should succeed", decryptResult.isSuccess());
        assertTrue("Decrypted file should exist", decryptResult.getOutputFile().exists());
        assertTrue("Decrypted file should have content", decryptResult.getOutputFile().length() > 0);

        // Verify content matches original
        String originalContent = readFile(testFile);
        String decryptedContent = readFile(decryptResult.getOutputFile());
        assertEquals("Decrypted content should match original encrypted file", decryptedContent, originalContent);

        // Clean up
        encryptResult.getOutputFile().delete();
        decryptResult.getOutputFile().delete();
    }


    // Verify different IVs/nonces for each encryption
    @Test
    public void test_UniqueIVs_ForEachEncryption() throws Exception {
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Encrypt the same file twice AES256
        EncryptionResult result1 = encryptionManager.encryptFile(testFile, aes, "test_file1_iv_aes.enc");
        EncryptionResult result2 = encryptionManager.encryptFile(testFile, aes, "test_file2_iv_aes.enc");

        assertTrue("Both AES256 and ChaCha20 encryption should succeed", result1.isSuccess() && result2.isSuccess());

        // Encrypt the same file twice ChaCha20
        EncryptionResult result3 = encryptionManager.encryptFile(testFile, chacha, "test_file3_iv_chacha.enc");
        EncryptionResult result4 = encryptionManager.encryptFile(testFile, chacha, "test_file4_iv_chacha.enc");

        // Read first 12 bytes (IV) from both encrypted files
        byte[] iv1 = new byte[12];
        byte[] iv2 = new byte[12];
        byte[] iv3 = new byte[12];
        byte[] iv4 = new byte[12];

        try (FileInputStream fis1 = new FileInputStream(result1.getOutputFile())) {
            fis1.read();
        }
        try (FileInputStream fis2 = new FileInputStream(result2.getOutputFile())) {
            fis2.read();
        }

        try (FileInputStream fis3 = new FileInputStream(result3.getOutputFile())) {
            fis3.read();
        }
        try (FileInputStream fis4 = new FileInputStream(result4.getOutputFile())) {
            fis4.read();
        }

        // IVs must be different AES256
        assertFalse("AES256 IVs must be unique for each encryption", Arrays.equals(iv1, iv2));

        // IVs must be different ChaCha20
        assertFalse("ChaCha20 IVs must be unique for each encryption", Arrays.equals(iv3, iv4));

        // Clean up
        result1.getOutputFile().delete();
        result2.getOutputFile().delete();
        result3.getOutputFile().delete();
        result4.getOutputFile().delete();
    }


    // Verify tampering detection
    @Test
    public void test_TamperingDetection() throws Exception{
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Encrypt file
        EncryptionResult encryptResultAES = encryptionManager.encryptFile(testFile, aes, "test_file_aes_tampering.enc");
        EncryptionResult encryptResultChaCha = encryptionManager.encryptFile(testFile, chacha, "test_file_chacha_tampering.enc");

        assertTrue("AES256 Encryption should succeed", encryptResultAES.isSuccess());
        assertTrue("ChaCha20 Encryption should succeed", encryptResultChaCha.isSuccess());

        // Tamper with the encrypted file (flipping one bit in the middle)
        File encryptedFileAES = encryptResultAES.getOutputFile();
        File encryptedFileChaCha = encryptResultChaCha.getOutputFile();

        byte[] encryptedDataAES = readFileBytes(encryptedFileAES);
        byte[] encryptedDataChaCha = readFileBytes(encryptedFileChaCha);

        int midpointAES = encryptedDataAES.length / 2;
        int midpointChaCha = encryptedDataChaCha.length / 2;

        encryptedDataAES[midpointAES] ^= 0x01; // flip one bit
        encryptedDataChaCha[midpointChaCha] ^= 0x01; // flip one bit

        // Write tampered data back
        try (FileOutputStream fosAES = new FileOutputStream(encryptedFileAES)) {
            fosAES.write(encryptedDataAES);
        }
        try (FileOutputStream fosChaCha = new FileOutputStream(encryptedFileChaCha)) {
            fosChaCha.write(encryptedDataChaCha);
        }

        // Attempt decryption - should fail due to authentication tag mismatch
        EncryptionResult decryptResultAES = encryptionManager.decryptFile(encryptedFileAES, aes, "test_file_aes_tampering_decrypted.txt");
        EncryptionResult decryptResultChaCha = encryptionManager.decryptFile(encryptedFileChaCha, chacha, "test_file_chacha_tampering_decrypted.txt");

        assertFalse("AES256 Decryption of tampered file should fail", decryptResultAES.isSuccess());
        assertTrue("AES256 Error message should indicate authentication failure",
                decryptResultAES.getErrorMessage().toLowerCase().contains("fail") ||
                decryptResultAES.getErrorMessage().toLowerCase().contains("corrupt"));

        assertFalse("ChaCha20 Decryption of tampered file should fail", decryptResultChaCha.isSuccess());
        assertTrue("ChaCha20 Error message should indicate authentication failure",
                decryptResultChaCha.getErrorMessage().toLowerCase().contains("fail") ||
                decryptResultChaCha.getErrorMessage().toLowerCase().contains("corrupt"));

        // Clean up
        encryptedFileAES.delete();
        if (decryptResultAES.getOutputFile() != null && decryptResultAES.getOutputFile().exists()) {
            decryptResultAES.getOutputFile().delete();
        }
        encryptedFileChaCha.delete();
        if (decryptResultChaCha.getOutputFile() != null && decryptResultChaCha.getOutputFile().exists()) {
            decryptResultChaCha.getOutputFile().delete();
        }
    }

    // Verify key generation produces correct length keys
    @Test
    public void test_KeyGeneration_CorrectLength() {
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Generate multiple keys to test consistency
        for (int i = 0; i < 10; i++) {
            byte[] aesKey = aes.generateKey();
            byte[] chachaKey = chacha.generateKey();

            assertNotNull("AES256 key should not be null", aesKey);
            assertNotNull("ChaCha20 key should not be null", chachaKey);

            assertEquals("AES256 key length should be 32 bytes", 32, aesKey.length);
            assertEquals("ChaCha20 key length should be 32 bytes", 32, chachaKey.length);

            // Keys should not be all zeros
            assertFalse("AES256 key should not be all zeros", isAllZeros(aesKey));
            assertFalse("ChaCha20 key should not be all zeros", isAllZeros(chachaKey));
        }
    }

    // Verify performance metrics collection
    @Test
    public void test_PerformanceMetrics_Collection() throws Exception {
        encryptionManager.resetMetrics();

        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Perform multiple operations
        for (int i = 0; i < 5; i++) {
            EncryptionResult resultAES = encryptionManager.encryptFile(testFile, aes, "test_file_aes_metrics_" + i + ".enc");
            EncryptionResult resultChaCha = encryptionManager.encryptFile(testFile, chacha, "test_file_chacha_metrics_" + i + ".enc");

            if (resultAES.isSuccess()) {
                resultAES.getOutputFile().delete();
            }
            if (resultChaCha.isSuccess()) {
                resultChaCha.getOutputFile().delete();
            }
        }s

        // Check metrics
        PerformanceMetrics.PerformanceSnapshot metrics = encryptionManager.getPerformanceMetrics();

        assertEquals("AES256 and ChaCha20 Should have recorded 5 operations each, totaling 10 operations", 10, metrics.totalOperations);

        assertEquals("All operations should have succeeded", 100.0, metrics.getSuccessRate());

        assertTrue("Should have metrics for AES256", metrics.algorithmMetrics.containsKey("AES") ||
                metrics.algorithmMetrics.values().stream().anyMatch(m -> m.algorithmName.contains("AES")));
        assertTrue("Should have metrics for ChaCha20", metrics.algorithmMetrics.containsKey("ChaCha20") ||
                metrics.algorithmMetrics.values().stream().anyMatch(m -> m.algorithmName.contains("ChaCha20")));
    }

    // Verify Benchmark functionality
    @Test
    public void test_Benchmark() {
        EncryptionBenchmark benchmark = new EncryptionBenchmark(context);

        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Run benchmark (using small files for speed testing)
        List<EncryptionBenchmark.BenchmarkResult> resultsAES = benchmark.runBenchmark(aes);
        List<EncryptionBenchmark.BenchmarkResult> resultsChaCha = benchmark.runBenchmark(chacha);

        assertNotNull("AES256 Benchmark results should not be null", resultsAES);
        assertNotNull("ChaCha20 Benchmark results should not be null", resultsChaCha);

        assertFalse("AES256 Benchmark should produce results", resultsAES.isEmpty());
        assertFalse("ChaCha20 Benchmark should produce results", resultsChaCha.isEmpty());

        for (EncryptionBenchmark.BenchmarkResult result : resultsAES) {
            assertTrue("AES256 Mean time should be positive", result.meanTimeMS > 0);
            assertTrue("AES256 Throughput should be positive", result.throughputMBps > 0);
            assertTrue("AES256 Benchmark should have run multiple iterations", result.iterations >= 20);
            assertTrue("AES256 CV (Coefficient Variation) should be reasonable", result.coefficientOfVariation < 50.0);

            assertNotNull("AES256 should have reproducibility assessment", result.getReproducibilityAssessment());
        }
    }

    // Compare algorithms
    @Test
    public void test_AlgorithmComparison() {
        EncryptionBenchmark benchmark = new EncryptionBenchmark(context);

        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        List<EncryptionBenchmark.BenchmarkResult> aesResults = benchmark.runBenchmark(aes);
        List<EncryptionBenchmark.BenchmarkResult> chachaResults = benchmark.runBenchmark(chacha);

        List<EncryptionBenchmark.CompareResult> comparisons = benchmark.compareResults(aesResults, chachaResults);


    }

    // Large file handling
    @Test
    public void test_LargeFile_Handling() throws Exception {
        // Create 100MB test file
        File largetFile = createTestFile("large_test.txt", generateLargeContent(100 * 1024 * 1024));

        try {
            InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
            InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

            // Encrypt
            EncryptionResult encryptResultAES = encryptionManager.encryptFile(largetFile, aes, "large_test_aes.enc");
            EncryptionResult encryptResultChaCha = encryptionManager.encryptFile(largetFile, chacha, "large_test_chacha.enc");

            assertTrue("AES256 Large File Encryption should succeed", encryptResultAES.isSuccess());
            assertTrue("ChaCha20 Large File Encryption should succeed", encryptResultChaCha.isSuccess());

            // Decrypt
            EncryptionResult decryptResultAES = encryptionManager.decryptFile(encryptResultAES.getOutputFile(), aes, "large_file_aes_decrypted.txt");
            EncryptionResult decryptResultChaCha = encryptionManager.decryptFile(encryptResultChaCha.getOutputFile(), chacha, "large_file_chacha_decrypted.txt");

            assertTrue("AES256 Large File Decryption should succeed", decryptResultAES.isSuccess());
            assertTrue("ChaCha20 Large File Decryption should succeed", decryptResultChaCha.isSuccess());

            // Verify sizes match
            assertEquals("AES256 File sizes should match", largetFile.length(), decryptResultAES.getOutputFile().length());
            assertEquals("ChaCha20 File sizes should match", largetFile.length(), decryptResultChaCha.getOutputFile().length());

            // Clean up
            encryptResultAES.getOutputFile().delete();
            decryptResultAES.getOutputFile().delete();

            encryptResultChaCha.getOutputFile().delete();
            decryptResultChaCha.getOutputFile().delete();

        } finally {
            largetFile.delete();
        }
    }

    // Verify Error Handling for invalid inputs
    @Test
    public void test_ErrorHandling_InvalidInputs() {
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        InterfaceEncryptionAlgorithm chacha = encryptionManager.getAlgorithmByName("ChaCha20");

        // Test 1: Non-existent files
        File nonExistent = new File(context.getFilesDir(), "does_not_exist.txt");

        EncryptionResult encryptedResultAES1 = encryptionManager.encryptFile(nonExistent, aes, "output_non_existent_aes.enc");
        EncryptionResult encryptedResultChaCha1 = encryptionManager.encryptFile(nonExistent, chacha, "output_non_existent_chacha.enc");

        assertFalse("AES256 Should fail for non-existent file", encryptedResultAES1.isSuccess());
        assertFalse("ChaCha20 Should fail for non-existent file", encryptedResultChaCha1.isSuccess());

        // Test 2: Empty file
        File emptyFile = new File(context.getFilesDir(), "empty_file.txt");
        try {
            emptyFile.createNewFile();
            EncryptionResult encryptedResultAES2 = encryptionManager.encryptFile(emptyFile, aes, "output_empty_aes.enc");
            EncryptionResult encryptedResultChaCha2 = encryptionManager.encryptFile(emptyFile, chacha, "output_empty_chacha.enc");

            assertFalse("AES256 Should fail for empty file", encryptedResultAES2.isSuccess());
            assertFalse("ChaCha20 Should fail for empty file", encryptedResultChaCha2.isSuccess());

        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        } finally {
            emptyFile.delete();
        }

        // Test 3: Decrypt without prior encryption
        encryptionManager.resetMetrics(); // Clearing stored key
        File dummyFile = new File(context.getFilesDir(), "dummy.enc");
        try {
            dummyFile.createNewFile();
            EncryptionResult decryptResultAES = encryptionManager.decryptFile(dummyFile, aes, "dummy_file_aes_output.txt");
            EncryptionResult decryptResultChaCha = encryptionManager.decryptFile(dummyFile, chacha, "dummy_file_chacha_output.txt");

            assertFalse("AES256 Should fail for decryption without encryption key", decryptResultAES.isSuccess());
            assertFalse("ChaCha20 Should fail for decryption without encryption key", decryptResultChaCha.isSuccess());

        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());

        } finally {
            dummyFile.delete();
        }
    }
}