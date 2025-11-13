package com.example.raziel.core.caching;

import android.content.Context;
import android.util.Log;

import com.example.raziel.core.cache.CipherCache;
import com.example.raziel.core.cache.KeyDerivationCache;
import com.example.raziel.core.cache.KeysetCache;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;

import java.security.GeneralSecurityException;

public class CacheManager {
    private static final String TAG = "CacheManager";

    private final KeysetCache keysetCache;
    private final CipherCache cipherCache;
    private final KeyDerivationCache keyDerivationCache;

    public CacheManager(Context context) {
        this.keysetCache = new KeysetCache();
        this.cipherCache = new CipherCache();
        this.keyDerivationCache = new KeyDerivationCache();

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        Log.d(TAG, "CacheManager initialised with memory budget: " + (maxMemory / 8) + "KB");
    }

    // Keyset caching
    public void cacheKeyset(String algorithm, KeysetHandle keyset) {
        keysetCache.put(algorithm, keyset);
    }

    public KeysetHandle getCachedKeyset(String algorithm) {
        return keysetCache.get(algorithm);
    }

    // Key Derivation Caching
    public byte[] cacheKeyDerivation(String password, String salt, byte[] derivedKey) {
        byte[] cached = keyDerivationCache.get(password, salt);
        if (cached != null) {
            return cached;
        }
        keyDerivationCache.put(password, salt, derivedKey);
        return derivedKey;
    }

    // Cipher Instance Caching
    public StreamingAead getOrCreateStreamingAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        return cipherCache.getOrCreateStreamingAead(keysetHandle);
    }

    public Aead getOrCreateAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        return cipherCache.getOrCreateAead(keysetHandle);
    }

    // Cache Management
    public void clearAll() {
        keysetCache.clear();
        cipherCache.clear();
        keyDerivationCache.clear();
        Log.d(TAG, "All caches cleared");
    }

    public static class CacheStats {
        public final int keysetCacheSize;
        public final int keyDerivationCacheSize;

        CacheStats(int keysetCacheSize, int keyDerivationCacheSize) {
            this.keysetCacheSize = keysetCacheSize;
            this.keyDerivationCacheSize = keyDerivationCacheSize;
        }
    }

    // Get Cache Statistics
    public CacheStats getStats() {
        return new CacheStats(keysetCache.size(), keyDerivationCache.size());
    }
}
