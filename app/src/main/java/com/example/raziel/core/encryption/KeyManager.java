package com.example.raziel.core.encryption;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * KeyManager with EncryptedSharedPreferences and MasterKey
 * Where each file has the Tink KeysetHandles securely and persists
 * It stores mapping from encrypted file path => (keyId, algorithm)
 */
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String PREF_FILE_NAME = "raziel_secure_keys";
    private static final String MASTER_KEY_ALIAS = "raziel_master_key";
    private static final String KEY_PREFIX_KEYSET = "keyset_";
    private static final String KEY_PREFIX_FILE_META = "filemeta_";

    private final Context context;
    private final SharedPreferences securePrefs;

    public static class FileKeyMetadata {
        public final String keyID;
        public final String algorithmName;

        public FileKeyMetadata(String keyID, String algorithmName) {
            this.keyID = keyID;
            this.algorithmName = algorithmName;
        }
    }

    public KeyManager(Context context) throws GeneralSecurityException, IOException {
        this.context = context.getApplicationContext();

        // Register Tink configurations
        AeadConfig.register();
        StreamingAeadConfig.register();

        // Build hardware-backed MasterKey when available
        MasterKey masterKey = new MasterKey.Builder(context, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        // Replacing SharedPreferences Interface with EncryptedSharedPreferences Interface
        this.securePrefs = EncryptedSharedPreferences.create(
                context,
                PREF_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

        Log.d(TAG, "KeyManager initialised with EncryptedSharedPreferences");

    }

    // Generate unique global ID for a file key to be called once per encrypted file
    public String generateFileKeyId() {
        return "filekey-" + UUID.randomUUID();
    }

    // Persist Tink KeysetHandle encrypted inside EncryptedSharedPreferences
    public void storeKeyset(String keyId, KeysetHandle keysetHandle) {
        if (keyId == null || keyId.isEmpty() || keysetHandle == null) {
            Log.e(TAG, "storeKeyset: invalid arguments");
            return;
        }
        try {
            // Serialize the keyset to a JSON string
            String serialisedKeyset = TinkJsonProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get());

            // Store the string in EncryptedSharePreferences
            securePrefs.edit()
                    .putString(KEY_PREFIX_KEYSET + keyId, serialisedKeyset)
                    .apply();
            Log.d(TAG, "Keyset stored for keyId=" + keyId);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Error serialising keyset for storage", e);
        }
    }

    // Load a KeysetHandle previously stored with storeKeyset()
    public KeysetHandle loadKeyset(String keyId) {
        if (keyId == null || keyId.isEmpty()) {
            return null;
        }
        try {
            String serialisedKeyet = securePrefs.getString(KEY_PREFIX_KEYSET + keyId, null);
            if (serialisedKeyet == null) {
                Log.w(TAG, "No keyset found for keyId=" + keyId);
                return null;
            }
            return TinkJsonProtoKeysetFormat.parseKeyset(serialisedKeyet, InsecureSecretKeyAccess.get());
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Error reading keyset for keyId=" + keyId, e);
            return null;
        }
    }

    // Remove a stored keyset (used during key rotation or cleanup)
    public void deleteKeyset(String keyId) {
        if (keyId == null || keyId.isEmpty()) return;
        securePrefs.edit()
                .remove(KEY_PREFIX_KEYSET + keyId)
                .apply();
        Log.d(TAG, "Keyset deleted for keyId=" + keyId);
    }

    // Create a streaming keyset for AES-256-GCM
    public KeysetHandle createAes256GcmStreamingKeyset(int segmentSizeBytes) throws GeneralSecurityException {
        AesGcmHkdfStreamingParameters parameters = AesGcmHkdfStreamingParameters.builder()
                .setKeySizeBytes(32) // 256bits
                .setDerivedAesGcmKeySizeBytes(32) // 256 bits
                .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                .setCiphertextSegmentSizeBytes(segmentSizeBytes) // 64KB low-end | 1MB high-end
                .build();
        return KeysetHandle.generateNew(parameters);
    }

    // Create Aead keyset for XChaCha20-Poly1305
    public KeysetHandle createXChaCha20Poly1305Keyset() throws GeneralSecurityException {
        return KeysetHandle.generateNew(PredefinedAeadParameters.XCHACHA20_POLY1305);
    }

    // Used when already the Parameter are available
    public KeysetHandle createStreamingKeysetHandle(Parameters parameters)
            throws GeneralSecurityException {
        return KeysetHandle.generateNew(parameters);
    }
}









