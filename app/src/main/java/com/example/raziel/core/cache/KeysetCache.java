package com.example.raziel.core.cache;

import android.util.Log;
import android.util.LruCache;

import com.google.crypto.tink.KeysetHandle;

public class KeysetCache {
    private static final String TAG = "KeysetCache";
    private static final int MAX_CACHE_SIZE = 10;
    private final LruCache<String, KeysetHandle> cache;

    public KeysetCache() {
        this.cache = new LruCache<String, KeysetHandle>(MAX_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, KeysetHandle value) {
                return 1; // Count each keyset as 1 unit
            }
        };
    }

    /**
     * Put keyset in cache
     */
    public void put(String algorithm, KeysetHandle keyset) {
        String cacheKey = algorithm + "_keyset";
        cache.put(algorithm + "_keyset", keyset);
        Log.d(TAG, "Cached keyset for: " + algorithm);
    }

    /**
     * Get keyset from cache
     */
    public KeysetHandle get(String algorithm) {
        String cacheKey = algorithm + "_keyset";
        KeysetHandle keysetHandle = cache.get(cacheKey);
        if (keysetHandle != null) {
            Log.d(TAG, "Cache hit for keyset: " + algorithm);
        }
        return keysetHandle;
    }

    public int size() {
        return cache.size();
    }

    // Clear keyset cache
    public void clear() {
        cache.evictAll();
        Log.d(TAG, "Keyset cache cleared");
    }
}
