package com.example.raziel.core.encryption;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

import java.io.File;
import java.security.GeneralSecurityException;

/**
 * KeyManager with hardware-backed security where available
 * Falls back to software encryption for older devices
 */
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String ENCRYPTED_PREF_NAME = "raziel_secure_prefs";

    private final Context context;
    private final MasterKey masterKey;
    private final EncryptedSharedPreferences encryptedSharedPreferences;

    public KeyManager(Context context) {
        this.context = context.getApplicationContext();
        this.masterKey = createMasterKey();
        this.encryptedSharedPreferences = createEncryptedSharedPreferences();
        initialize();
    }

    private MasterKey createMasterKey() {
        try {
            return new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create master key", e);
            throw new RuntimeException("Master key creation failed", e);
        }
    }

    // Initialise and register primitives
    private void initialize() {
        try {
            StreamingAeadConfig.register();
            AeadConfig.register();
            Log.d(TAG, "Tink initialised successfully");

        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Failed to initialise Tink");
            throw new RuntimeException("Tink initialisation failed", e);
        }
    }

    // Secure shared preferences for storing keyset metadata
    private EncryptedSharedPreferences createEncryptedSharedPreferences() {
        try {
            return (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to create encrypted preferences", e);
            throw new RuntimeException("Secure storage initialisation failed", e);
        }
    }

    // Parameter-based key generation for AES streaming
    public KeysetHandle createAesStreamingKeysetHandle(int segmentSizeBytes) throws GeneralSecurityException {
        AesGcmHkdfStreamingParameters parameters = AesGcmHkdfStreamingParameters.builder()
                .setKeySizeBytes(32) // 256bits
                .setDerivedAesGcmKeySizeBytes(32) // 256 bits
                .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                .setCiphertextSegmentSizeBytes(segmentSizeBytes)
                .build();

        return KeysetHandle.generateNew(parameters);
    }

    // Create keyset with algorithm specific parameters
    public KeysetHandle createKeysetForAlgorithm(String algorithmName, int segmentSize) throws GeneralSecurityException {
        if (algorithmName.contains("AES")) {
            return createAesStreamingKeysetHandle(segmentSize);
        } else {
            return createChaChaKeysetHandle();
        }
    }

    // Generate unique key ID for file-based key management
    public String generateFileKeyId(File file) {
        String baseId = file.getName() + "_" + file.length() + "_" + file.lastModified();
        // Add some randomness to avoid collisions
        return baseId + "_" + System.currentTimeMillis();
    }

    // Store keyset metadata securely
    public void storeKeysetMetadata(String keysetId, String metadata) {
        encryptedSharedPreferences.edit().putString("keyset_" + keysetId, metadata).apply();
    }

    public String getKeysetMetadata(String keysetId) {
        return encryptedSharedPreferences.getString("keyset_" + keysetId, null);
    }

    // Clear all secure data
    public void clearAllSecureData() {
        encryptedSharedPreferences.edit().clear().apply();
        Log.d(TAG, "All secure data cleared");
    }

    // Parameter-based key generation for ChaCha20
    public KeysetHandle createChaChaKeysetHandle() throws GeneralSecurityException {
        return KeysetHandle.generateNew(PredefinedAeadParameters.XCHACHA20_POLY1305);
    }

    // Generic method to create streaming keyset with parameters
    public KeysetHandle createStreamingKeysetHandle(Parameters parameters) throws GeneralSecurityException {
        return KeysetHandle.generateNew(parameters);
    }
}









