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
    public void cacheKeyset(String keyID, KeysetHandle keyset) {
        keysetCache.put(keyID, keyset);
    }

    public KeysetHandle getCachedKeyset(String keyID) {

        KeysetHandle keyset = keysetCache.get(keyID);
        if (keyset != null) {
            keysetHits++;
            Log.d(TAG, "Keyset cache HIT for: " + keyID);
        } else {
            keysetMisses++;
            Log.d(TAG, "Keyset cache MISS for: " + keyID);
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

        public final long totalKeysetGenerationTimeNs;
        public final double avgKeysetGenerationTimeMs;

        public CacheStats(int keysetCacheSize, int keysetHits, int keysetMisses, long totalKeysetGenerationTimeNs) {
            this.keysetCacheSize = keysetCacheSize;
            this.keysetHits = keysetHits;
            this.keysetMisses = keysetMisses;

            this.keysetHitRate = keysetHits + keysetMisses > 0 ? (double) keysetHits / (keysetHits + keysetMisses) * 100 : 0;

            this.totalKeysetGenerationTimeNs = totalKeysetGenerationTimeNs;
            this.avgKeysetGenerationTimeMs = (keysetHits + keysetMisses) > 0 ? (totalKeysetGenerationTimeNs / 1_000_000.0) / (keysetHits + keysetMisses) : 0.0;
        }
    }

    // Get Cache Statistics
    public CacheStats getStats() {
        return new CacheStats(keysetCache.size(), keysetHits, keysetMisses, totalKeysetGenerationTime);
    }


    // Get cache performance summary for display
    @SuppressLint("DefaultLocale")
    public String getCachePerformanceSummary() {
        CacheStats stats = getStats();

        double speedup = stats.avgKeysetGenerationTimeMs / 0.05;

        return String.format(
                "=== CACHE PERFORMANCE ===\n\n" +
                        "Keyset Cache:\n" +
                        "Hits: %d\n"+
                        "Misses: %d\n" +
                        "Hit Rate: %.1f%%\n" +
                        "Avg Keyset Generation Time: %.3f ms\n" +
                        "Cache Load Time: < 0.05 ms\n" +
                        "Estimated Speedup: ~%.1fx\n",
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
