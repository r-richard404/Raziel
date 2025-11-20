package com.example.raziel.core.caching;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.example.raziel.core.cache.KeysetCache;
import com.google.crypto.tink.KeysetHandle;

public class CacheManager {
    private static final String TAG = "CacheManager";

    private final KeysetCache keysetCache;

    // Cache Performance Metrics
    private int keysetHits = 0;
    private int keysetMisses = 0;

    private long totalKeysetGenerationTime = 0;

    public CacheManager(Context context) {
        this.keysetCache = new KeysetCache();

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


    // Record timing for operations that bypass cache
    public void recordKeysetGenerationTime(long timeNs) {
        totalKeysetGenerationTime += timeNs;
    }


    public static class CacheStats {
        public final int keysetCacheSize;

        // Performance Metrics
        public final int keysetHits;
        public final int keysetMisses;

        public final double keysetHitRate;

        public CacheStats(int keysetCacheSize, int keysetHits, int keysetMisses) {
            this.keysetCacheSize = keysetCacheSize;
            this.keysetHits = keysetHits;
            this.keysetMisses = keysetMisses;

            this.keysetHitRate = keysetHits + keysetMisses > 0 ? (double) keysetHits / (keysetHits + keysetMisses) * 100 : 0;
        }
    }

    // Get Cache Statistics
    public CacheStats getStats() {
        return new CacheStats(keysetCache.size(), keysetHits, keysetMisses);
    }


    // Get cache performance summary for display
    @SuppressLint("DefaultLocale")
    public String getCachePerformanceSummary() {
        CacheStats stats = getStats();

        return String.format(
                "=== CACHE PERFORMANCE ===\n\n" +
                        "Keyset Cache: %d/%d (%.1f%% hit rate)\n" +
                        "  -> Avg generation: %d ms (vs <1ms cache)\n" +
                        "  -> Avg creation: %d ms (vs <1ms cache)\n",
                stats.keysetHits, stats.keysetHits + stats.keysetMisses, stats.keysetHitRate
        );
    }

    // Clear cache and reset metrics
    public void clearAll() {

        // Reset metrics
        keysetHits = keysetMisses = 0;
        Log.d(TAG, "All caches cleared and metrics reset");
    }

}
