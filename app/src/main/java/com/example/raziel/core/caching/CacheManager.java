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

    // Cache Performance Metrics
    private int keysetHits = 0;
    private int keysetMisses = 0;
    private int cipherHits = 0;
    private int cipherMisses = 0;
    private int keyDerivationHits = 0;
    private int keyDerivationMisses = 0;

    private long totalKeysetGenerationTime = 0;
    private long totalCipherCreationTime = 0;
    private long totalKeyDerivationTime = 0;

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

        KeysetHandle keyset = keysetCache.get(algorithm);
        if (keyset != null) {
            keysetHits++;
            Log.d(TAG, "Keyset cache HIT for: " + algorithm);
        } else {
            keysetMisses++;
            Log.d(TAG, "Keyset cache MISS for: " + algorithm);
        }
        return keyset;
    }

    // Key Derivation Caching
    public byte[] cacheKeyDerivation(String password, String salt, byte[] derivedKey) {
        byte[] cached = keyDerivationCache.get(password, salt);
        if (cached != null) {
            keyDerivationHits++;
            Log.d(TAG, "Key derivation cache HIT");
            return cached;
        }
        keyDerivationMisses++;
        keyDerivationCache.put(password, salt, derivedKey);
        Log.d(TAG, "Key derivation cache MISS - cached new derivation");
        return derivedKey;
    }

    // Cipher Instance Caching
    public StreamingAead getOrCreateStreamingAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        long startTime = System.nanoTime();
        StreamingAead result = cipherCache.getOrCreateStreamingAead(keysetHandle);
        long endTime = System.nanoTime();

        // Check if this was a cache hit by comparing creation time
        long creationTime = endTime - startTime;
        totalCipherCreationTime += creationTime;

        // Less than 1ms = cache hit
        if (creationTime < 1000000) {
            cipherHits++;
            Log.d(TAG, "Cipher cache HIT - creation time: " + creationTime/1000 + "microSeconds");
        } else {
            cipherMisses++;
            Log.d(TAG, "Cipher cache MISS - creation time: " + creationTime/1000000 + "ms");
        }
        return result;
    }

    public Aead getOrCreateAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        long startTime = System.nanoTime();
        Aead result = cipherCache.getOrCreateAead(keysetHandle);
        long endTime = System.nanoTime();

        long creationTime = endTime - startTime;
        totalCipherCreationTime += creationTime;

        // Less than 1ms = cache hit
        if (creationTime < 1000000) {
            cipherHits++;
            Log.d(TAG, "Cipher cache HIT - creation time: " + creationTime/1000 + "microSeconds");
        } else {
            cipherMisses++;
            Log.d(TAG, "Cipher cache MISS - creation time: " + creationTime/1000000 + "ms");
        }

        return result;
    }

    // Record timing for operations that bypass cache
    public void recordKeysetGenerationTime(long timeNs) {
        totalKeysetGenerationTime += timeNs;
        keysetMisses++; // recording time implies cache miss
    }

    public void recordKeyDerivationTime(long timeNs) {
        totalKeyDerivationTime += timeNs;
        keyDerivationMisses++;
    }

    public static class CacheStats {
        public final int keysetCacheSize;
        public final int keyDerivationCacheSize;

        // Performance Metrics
        public final int keysetHits;
        public final int keysetMisses;
        public final int cipherHits;
        public final int cipherMisses;
        public final int keyDerivationHits;
        public final int keyDerivationMisses;
        public final double keysetHitRate;
        public final double cipherHitRate;
        public final double keyDerivationHitRate;
        public final long avgKeysetGenerationTimeMs;
        public final long avgCipherCreationTimeMs;
        public final long avgKeyDerivationTimeMs;

        public CacheStats(int keysetCacheSize, int keyDerivationCacheSize, int keysetHits, int keysetMisses, int cipherHits, int cipherMisses,
                          int keyDerivationHits, int keyDerivationMisses, long totalKeysetTime, long totalCipherTime, long totalKeyDerivationTime) {
            this.keysetCacheSize = keysetCacheSize;
            this.keyDerivationCacheSize = keyDerivationCacheSize;
            this.keysetHits = keysetHits;
            this.keysetMisses = keysetMisses;
            this.cipherHits = cipherHits;
            this.cipherMisses = cipherMisses;
            this.keyDerivationHits = keyDerivationHits;
            this.keyDerivationMisses = keyDerivationMisses;

            this.keysetHitRate = keysetHits + keysetMisses > 0 ? (double) keysetHits / (keysetHits + keysetMisses) * 100 : 0;
            this.cipherHitRate = cipherHits + cipherMisses > 0 ? (double) cipherHits / (cipherHits + cipherMisses) * 100 : 0;
            this.keyDerivationHitRate = keyDerivationHits + keyDerivationMisses > 0 ? (double) keyDerivationHits / (keyDerivationHits + keyDerivationMisses) * 100 : 0;

            this.avgKeysetGenerationTimeMs = keysetMisses > 0 ? totalKeysetTime / keysetMisses / 1_000_000 : 0;
            this.avgCipherCreationTimeMs = cipherMisses > 0 ? totalCipherTime / cipherMisses / 1_000_000 : 0;
            this.avgKeyDerivationTimeMs = keyDerivationMisses > 0 ? totalKeyDerivationTime / keyDerivationMisses / 1_000_000 : 0;
        }
    }

    // Get Cache Statistics
    public CacheStats getStats() {
        return new CacheStats(
                keysetCache.size(),
                keyDerivationCache.size(),
                keysetHits, keysetMisses,
                cipherHits, cipherMisses,
                keyDerivationHits, keyDerivationMisses,
                totalKeysetGenerationTime, totalCipherCreationTime, totalKeyDerivationTime);
    }


    // Get cache performance summary for display
    public String getCachePerformanceSummary() {
        CacheStats stats = getStats();
        long speedup = stats.avgKeysetGenerationTimeMs > 0 ? stats.avgKeysetGenerationTimeMs : 10; // 1ms cache hit vs actual generation time

        return String.format(
                "=== CACHE PERFORMANCE ===\n\n" +
                        "Keyset Cache: %d/%d (%.1f%% hit rate)\n" +
                        "  -> Avg generation: %d ms (vs <1ms cache)\n" +
                        "Cipher Cache: %d/%d (%.1f%% hit rate)\n" +
                        "  -> Avg creation: %d ms (vs <1ms cache)\n" +
                        "Key Derivation: %d/%d (%.1f%% hit rate)\n" +
                        "  -> Avg derivation: %d ms (vs <1ms cache)\n\n" +
                        "Performance Improvement: ~%dx faster with cache",
                stats.keysetHits, stats.keysetHits + stats.keysetMisses, stats.keysetHitRate,
                stats.avgKeysetGenerationTimeMs,
                stats.cipherHits, stats.cipherHits + stats.cipherMisses, stats.cipherHitRate,
                stats.avgCipherCreationTimeMs,
                stats.keyDerivationHits, stats.keyDerivationHits + stats.keyDerivationMisses, stats.keyDerivationHitRate,
                stats.avgKeyDerivationTimeMs,
                speedup // Estimate performance improvement
        );
    }

    // Clear cache and reset metrics
    public void clearAll() {
        keysetCache.clear();
        cipherCache.clear();
        keyDerivationCache.clear();

        // Reset metrics
        keysetHits = keysetMisses = 0;
        cipherHits = cipherMisses = 0;
        keyDerivationHits = keyDerivationMisses = 0;
        totalKeysetGenerationTime = totalCipherCreationTime = totalKeyDerivationTime = 0;
        Log.d(TAG, "All caches cleared and metrics reset");
    }

}
