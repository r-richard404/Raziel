package com.example.raziel.core.caching;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private static final String TAG = "CacheManager";
    private final ThreadLocal<CipherInstancePool> cipherPools = ThreadLocal.withInitial(CipherInstancePool::new);
    private final LruCache<String, KeysetHandle> keysetCache;
    private final ConcurrentHashMap<String, byte[]> keyDerivationCache;

    // Configuration
    private static final int MAX_KEYSET_CACHE_SIZE = 10;
    private static final int MAX_KEY_DERIVATION_CACHE = 50;

    private static class CipherInstancePool {
        private StreamingAead cachedStreamingAead;
        private Aead cachedAead;
        private KeysetHandle lastStreamingKeysetHandle;
        private KeysetHandle lastAeadKeysetHandle;

        StreamingAead getStreamingAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
            // Reuse if same keyset
            if (cachedStreamingAead != null && keysetHandle.equals(lastStreamingKeysetHandle)) {
                return cachedStreamingAead;
            }

            // Create new instance and cache
            cachedStreamingAead = keysetHandle.getPrimitive(StreamingAead.class);
            lastStreamingKeysetHandle = keysetHandle;
            return cachedStreamingAead;
        }

        Aead getAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
            // Reuse if same keyset
            if (cachedAead != null && keysetHandle.equals(lastAeadKeysetHandle)) {
                return cachedAead;
            }

            // Create new instance and cache
            cachedAead = keysetHandle.getPrimitive(Aead.class);
            lastAeadKeysetHandle = keysetHandle;
            return cachedAead;
        }
    }

    public CacheManager(Context context) {
        // Use 1/8th of available memory for cache
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;

        keysetCache = new LruCache<String, KeysetHandle>(MAX_KEYSET_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, KeysetHandle value) {
                return 1; // Count each keyset as 1 unit
            }
        };

        keyDerivationCache = new ConcurrentHashMap<>(MAX_KEY_DERIVATION_CACHE);

        Log.d(TAG, "CacheManager initialised with memory budget: " + cacheSize + "KB");
    }



    public void cacheKeyset(String algorithm, KeysetHandle keyset) {
        String cacheKey = algorithm + "_keyset";
        keysetCache.put(algorithm + "_keyset", keyset);
        Log.d(TAG, "Cached keyset for: " + algorithm);
    }

    public KeysetHandle getCachedKeyset(String algorithm) {
        String cacheKey = algorithm + "_keyset";
        KeysetHandle keysetHandle = keysetCache.get(cacheKey);
        if (keysetHandle != null) {
            Log.d(TAG, "Cache hit for keyset: " + algorithm);
        }
        return keysetHandle;
    }

    public String generateDerivationKey(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        return password.hashCode() + ":" + salt;
    }

    public byte[] cacheKeyDerivation(String password, String salt, byte[] derivedKey) {
        String cacheKey = generateDerivationKey(password, salt);
        byte[] cached = keyDerivationCache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit for key derivation");
            // Return a defensive copy
            return Arrays.copyOf(cached, cached.length);
        }

        // Cache miss - store and return derived key
        if (derivedKey != null && derivedKey.length > 0) {
            // Store a defensive copy in cache
            byte[] cachedCopy = Arrays.copyOf(derivedKey, derivedKey.length);
            keyDerivationCache.put(cacheKey, cachedCopy);

            // Return a defensive copy to caller
            return Arrays.copyOf(derivedKey, derivedKey.length);
        }

        return null;
    }


    // Get or create StreamingAead instance for thread, avoiding expensive primitive creation overhead
    public StreamingAead getOrCreateStreamingAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        CipherInstancePool pool = cipherPools.get();
        return pool.getStreamingAead(keysetHandle);
    }

    public Aead getOrCreateAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        CipherInstancePool pool = cipherPools.get();
        return pool.getAead(keysetHandle);
    }

    // Clear all caches
    public void clearAll() {
        keysetCache.evictAll();
        keyDerivationCache.clear();
        cipherPools.remove();
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
