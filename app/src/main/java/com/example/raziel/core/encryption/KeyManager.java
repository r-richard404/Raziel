package com.example.raziel.core.encryption;

import android.content.Context;
import android.util.Log;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

import java.io.IOException;
import java.security.GeneralSecurityException;

// Manages Tink keysets with Android Keystore backing
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String MASTER_KEYSET_NAME = "raziel_master_keyset";
    private static final String PREF_FILE_NAME = "raziel_key_prefs";

    private final Context context;
    private AndroidKeysetManager keysetManager;

    public KeyManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    // Initialise Tink and register primitives
    private void initialize() {
        try {
            AeadConfig.register();
            StreamingAeadConfig.register();

            keysetManager = new AndroidKeysetManager.Builder()
                    .withSharedPref(context, MASTER_KEYSET_NAME, PREF_FILE_NAME)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri("android-keystore://raziel_master_key")
                    .build();

            Log.d(TAG, "Tink KeyManager initialised successfully");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialise KeyManager");
            throw new RuntimeException("Crypto initialisation failed", e);
        }
    }

    // Get keyset handle for cryptographic operations
    public KeysetHandle getKeysetHandle() throws GeneralSecurityException {
        return keysetManager.getKeysetHandle();
    }

    // Get AEAD primitive for small data encryption
    public Aead getAead() throws GeneralSecurityException {
        return getKeysetHandle().getPrimitive(Aead.class);
    }

    // Create new keyset handle for StreamingAead operations
    public KeysetHandle createStreamingKeysetHandle(Parameters template) throws GeneralSecurityException {
        return KeysetHandle.generateNew(template);
    }

    // Rotate master key
    public void rotateKeyTemplate(KeyTemplate template) throws GeneralSecurityException, IOException {
        keysetManager.add(template);
        Log.d(TAG, "Master key rotated successfully");
    }

    // Clear all keysets (logout/factory reset)
    public void clearKeysets() {
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        Log.d(TAG, "All keysets cleared");
    }
}
