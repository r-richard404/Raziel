package com.example.raziel.core.cache;

import android.util.Log;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;

import java.security.GeneralSecurityException;

public class CipherCache {
    private static final String TAG = "CipherCache";
    private static class CipherPool {
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
            if (cachedAead != null && keysetHandle.equals(lastAeadKeysetHandle)) {
                return cachedAead;
            }
            cachedAead = keysetHandle.getPrimitive(Aead.class);
            lastAeadKeysetHandle = keysetHandle;
            return cachedAead;
        }
    }

    private final ThreadLocal<CipherPool> threadLocalPools = ThreadLocal.withInitial(CipherPool::new);

    /**
     * Get or create StreamingAead instance for thread
     * avoiding expensive primitive creation overhead
     */
    public StreamingAead getOrCreateStreamingAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        CipherPool pool = threadLocalPools.get();
        return pool.getStreamingAead(keysetHandle);
    }

    /**
     * Get or create Aead instance for thread
     * avoiding expensive primitive creation overhead
     */
    public Aead getOrCreateAead(KeysetHandle keysetHandle) throws GeneralSecurityException {
        CipherPool pool = threadLocalPools.get();
        return pool.getAead(keysetHandle);
    }

    // Clear thread pools
    public void clear() {
        threadLocalPools.remove();
        Log.d(TAG, "Cipher cache cleared");
    }
}
