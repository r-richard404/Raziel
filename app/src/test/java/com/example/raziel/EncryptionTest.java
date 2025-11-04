package com.example.raziel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.encryption.models.EncryptionResult;
import com.example.raziel.core.managers.file.InternalFileManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

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
        try (java.io.FileInputStream fis = new FileInputStream(file)) {
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

    @Test
    public void test_AES256_RoundTrip_PreservesData() throws Exception {
        // Get AES algorithm
        InterfaceEncryptionAlgorithm aes = encryptionManager.getAlgorithmByName("AES");
        assertNotNull("AES algorithm should be available", aes);

        // Encrypt the test file
        EncryptionResult encryptResult = encryptionManager.encryptFile(testFile, aes, "test_aes.enc");
        assertTrue("Encryption should succeeed", encryptResult.isSuccess());
        assertTrue("Encrypted file should exist", encryptResult.getOutputFile().exists());
        assertTrue("Encrypted file should have content", encryptResult.getOutputFile().length() > 0);

        // Decrypt the encrypted file
        EncryptionResult decryptResult = encryptionManager.decryptFile(
                encryptResult.getOutputFile(), aes, "test_aes_decrypted.txt");
        assertTrue("Decryption should suceed", decryptResult.isSuccess());
        assertTrue("Decrypted file should exist", decryptResult.getOutputFile().exists());

        // Verify content matches original
        String originalContent = readFile(testFile);
        String decryptedContent = readFile(decryptResult.getOutputFile());
        assertEquals("Decrypted content should match original", originalContent, decryptedContent);

        // Clean up
        encryptResult.getOutputFile().delete();
        decryptResult.getOutputFile().delete();
    }

    //TODO: Finish the rest of tests to ensure proper file creation, deletion, encryption/decryption and metrics used
}