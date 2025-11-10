package com.example.raziel.core.profiler;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

// Detects device capabilities for adaptive optimisation
public class DeviceProfiler {
    private static final String TAG = "DeviceProfiler";

    public enum MemoryClass {
        LOW, // < 256MB available
        MEDIUM,  // 256-512MB available
        HIGH // > 512MB available
    }

    public enum SegmentSize{
        SMALL(64 * 1024),  // 64KB for low-end devices
        MEDIUM(256 * 1024),  // 256KB for mid-range
        LARGE(512 * 1024);  // 1MB for high-end

        public final int bytes;

        SegmentSize(int bytes) {
            this.bytes = bytes;
        }
    }

    private final Context context;
    private final MemoryClass memoryClass;
    private final boolean hasAESHardware;
    private final int cpuCores;


    public DeviceProfiler(Context context) {
        this.context = context.getApplicationContext();
        this.memoryClass = detectMemoryClass();
        this.hasAESHardware = detectAESHardware();
        this.cpuCores = Runtime.getRuntime().availableProcessors();

        Log.d(TAG, String.format("Device Profile: Memory=%s, AES HW=%b, CPU Cores=%d", memoryClass, hasAESHardware, cpuCores));
    }


    // Determines available memory
    private MemoryClass detectMemoryClass() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return MemoryClass.LOW;

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memoryInfo);

        long availableMB = memoryInfo.availMem / (1024 * 1024);

        if (availableMB > 512 ) {
            return MemoryClass.HIGH;
        }
        if (availableMB > 256) {
            return MemoryClass.MEDIUM;
        }
        return MemoryClass.LOW;
    }

    // Checks for ARMv8 Crypto extensions
    private boolean detectAESHardware() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        String[] abis = Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            if (abi.contains("arm64")) {
                return true;
            }
        }
        return false;
    }

    // Returns optimal segment size for Tink StreamingAead
    public SegmentSize getOptimalSegmentSize() {
        switch (memoryClass) {
            case HIGH:
                return SegmentSize.LARGE;
            case MEDIUM:
                return SegmentSize.MEDIUM;
            default:
                return SegmentSize.SMALL;
        }
    }

    // Returns optimal read buffer size for file I/O
    public int getOptimalBufferSize() {
        switch (memoryClass) {
            case HIGH:
                return 8 * 1024 * 1024;
            case MEDIUM:
                return 4 * 1024 * 1024;
            default:
                return 2 * 1024 * 1024;
        }
    }

    // Determines if parallel processing should be used
    public boolean shouldUseParallelProcessing() {
        return cpuCores >= 4 && memoryClass != memoryClass.LOW;
    }

    // Recommends AES over ChaCha20 when hardware support is available
    public boolean preferAES() {
        return hasAESHardware;
    }

    public MemoryClass getMemoryClass() {
        return memoryClass;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public boolean hasAESHardware() {
        return hasAESHardware;
    }
}
