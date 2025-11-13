package com.example.raziel.core.cache;

import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class KeyDerivationCache {
    private static final String TAG = "KeyDerivationCache";
    private static final int MAX_CACHE_SIZE = 50;

    private final ConcurrentHashMap<String, byte[]> cache;

    public KeyDerivationCache() {
        this.cache = new ConcurrentHashMap<>(MAX_CACHE_SIZE);
    }

    /**
     * Generate cache key from password and salt
     */
    private String generateKey(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        return password.hashCode() + ":" + salt;
    }

    /**
     * Store derived key in cache
     */
    public void put(String password, String salt, byte[] derivedKey) {
        if (derivedKey == null || derivedKey.length == 0) {
            return;
        }
        String cacheKey = generateKey(password, salt);
        byte[] cacheCopy = Arrays.copyOf(derivedKey, derivedKey.length);
        cache.put(cacheKey, cacheCopy);
    }

    /**
     * Retrieve derived key from cache
     * Returns defensive copy to prevent modification
     */
    public byte[] get(String password, String salt) {
        String cacheKey = generateKey(password, salt);
        byte[] cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit for key derivation");
            // Return a defensive copy
            return Arrays.copyOf(cached, cached.length);
        }

        return null;
    }

    public int size() {
        return cache.size();
    }

    // Clear key cache
    public void clear() {
        cache.clear();
        Log.d(TAG, "Key derivation cache cleared");
    }
}
