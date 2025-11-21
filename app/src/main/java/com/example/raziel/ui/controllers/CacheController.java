package com.example.raziel.ui.controllers;

import android.os.Handler;

import com.example.raziel.R;
import com.example.raziel.core.caching.CacheManager;
import com.example.raziel.core.encryption.EncryptionManager;
import com.example.raziel.ui.activities.MainActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class CacheController {

    private final MainActivity activity;
    private final EncryptionManager encryptionManager;
    private final Handler mainHandler;

    public CacheController(MainActivity activity, EncryptionManager encryptionManager) {
        this.activity = activity;
        this.encryptionManager = encryptionManager;
        this.mainHandler = activity.mainHandler;
    }

    // ---------------------------------------------------------------------
    //  Optional helper: called from MainActivity.onCreate to wire buttons
    // ---------------------------------------------------------------------
    public void attachButtons() {
        MaterialButton btnCacheStats = activity.findViewById(R.id.btnCacheStats);
        MaterialButton btnClearCache = activity.findViewById(R.id.btnClearCache);
        MaterialButton btnToggleCache = activity.findViewById(R.id.btnToggleCache);

        if (btnCacheStats != null) {
            btnCacheStats.setOnClickListener(v -> showCachePerformance());
        }

        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> clearCache());
        }

        // Toggle Caching
        if (btnToggleCache != null) {
            btnToggleCache.setText(encryptionManager.isCachingEnabled() ? "Disable Cache" : "Enable Cache");
            btnToggleCache.setOnClickListener(v -> {
                // Toggle the state
                boolean newState = !encryptionManager.isCachingEnabled();
                encryptionManager.setCachingEnabled(newState);

                // Update button text
                btnToggleCache.setText(newState ? "Disable Cache" : "Enable Cache");

                // UI feedback
                activity.updateStatus(newState ? "In-memory key caching ENABLED" : "In-memory key caching DISABLED");
            });
        }
    }

    // ---------------------------------------------------------------------
    //  Public API – called from Activity (or via attachButtons)
    // ---------------------------------------------------------------------
    public void showCachePerformance() {
        CacheManager.CacheStats stats = encryptionManager.getCacheStats();

        double speedup = stats.avgKeysetGenerationTimeMs / 0.05;

        String cacheMessage = String.format(Locale.US,
                "=== CACHE PERFORMANCE ===\n\n" +
                        "Hits: %d\n" +
                        "Misses: %d\n" +
                        "Hit Rate: %.1f%%\n" +
                        "Avg Keyset Generation Time: %.3f ms\n" +
                        "Cached Load Time: < 0.05 ms\n" +
                        "Estimated Speedup: ~%.1f×\n",
                stats.keysetHits, stats.keysetMisses, stats.keysetHitRate, stats.avgKeysetGenerationTimeMs, speedup
        );
        activity.showResults(cacheMessage);
    }

    public void clearCache() {
        encryptionManager.cleanup(); // internally calls cacheManager.clearAll()
        activity.updateStatus("All caches cleared. Next operations will be slower.");

        // Re-show stats after a short delay so the user can see the reset state
        mainHandler.postDelayed(this::showCachePerformance, 1000);
    }
}
